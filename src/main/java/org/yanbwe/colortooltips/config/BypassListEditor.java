package org.yanbwe.colortooltips.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 让位名单（{@code common.json} 的 {@code bypass} 块）的文件编辑器。
 * <p>
 * 供客户端命令使用：把物品加入/移出让位名单，并立即写回磁盘。
 * <p>
 * 本类**不依赖任何 Minecraft 类**（只依赖 Gson 与 JDK），因此可脱离游戏做单元测试。
 * 写入只修改 {@code bypass} 块，文件中的其它配置、注释式排版与未知字段全部原样保留。
 * <p>
 * File editor for the {@code bypass} block of {@code common.json}, used by the client
 * commands. Minecraft-free by design so it can be unit tested outside the game.
 */
public final class BypassListEditor {

    /** Gson 实例：宽松解析 + 缩进输出。 */
    private static final Gson GSON = new GsonBuilder()
            .setLenient()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    /** 操作结果状态。 */
    public enum Status {
        /** 已新增条目并写回。 */
        ADDED,
        /** 条目已存在，未做改动（幂等）。 */
        ALREADY_PRESENT,
        /** 已移除条目并写回。 */
        REMOVED,
        /** 条目本来就不在名单中。 */
        NOT_PRESENT,
        /** 写回磁盘失败。 */
        WRITE_FAILED
    }

    /** 一次操作的结果：状态 + 变更后的完整名单。 */
    public static final class Result {
        private final Status status;
        private final boolean enabled;
        private final Set<String> mods;
        private final Set<String> items;

        private Result(Status status, boolean enabled, Set<String> mods, Set<String> items) {
            this.status = status;
            this.enabled = enabled;
            this.mods = mods;
            this.items = items;
        }

        public Status getStatus() {
            return status;
        }

        /** @return 文件里 {@code bypass.enabled} 的当前值（操作未改动它） */
        public boolean isEnabled() {
            return enabled;
        }

        /** @return 变更后的命名空间名单（只读） */
        public Set<String> getMods() {
            return Collections.unmodifiableSet(mods);
        }

        /** @return 变更后的物品注册名名单（只读） */
        public Set<String> getItems() {
            return Collections.unmodifiableSet(items);
        }
    }

    private BypassListEditor() {
    }

    /**
     * 把条目加入让位名单并写回文件。
     *
     * @param file   {@code common.json} 路径
     * @param source 条目内容（物品 ID / 命名空间 / {@code namespace:*} / 原始 JSON）
     * @return 操作结果；{@link Status#WRITE_FAILED} 表示磁盘写入失败
     * @throws IOException           读取失败（文件不存在、无权限等）
     * @throws JsonSyntaxException   文件不是合法 JSON 对象
     * @throws IllegalArgumentException source 不是可识别的物品/命名空间/原始 JSON
     */
    public static Result add(Path file, String source) throws IOException {
        return mutate(file, source, true);
    }

    /**
     * 把条目从让位名单移除并写回文件。
     *
     * @throws IOException           读取失败
     * @throws JsonSyntaxException   文件不是合法 JSON 对象
     * @throws IllegalArgumentException source 不是可识别的物品/命名空间/原始 JSON
     */
    public static Result remove(Path file, String source) throws IOException {
        return mutate(file, source, false);
    }

    /**
     * 解析条目文本为 {@code [命名空间, 物品注册名]}；不适用的一侧为 null。
     * <p>
     * 支持三种写法：
     * <ol>
     *   <li>{@code namespace:path} — 命中 {@code items}</li>
     *   <li>{@code namespace:*} — 命中 {@code mods}（等价于只写命名空间）</li>
     *   <li>{@code namespace} — 无冒号，命中 {@code mods}</li>
     * </ol>
     * 另支持以 <code>[</code>/<code>{</code> 开头的**原始 JSON**：直接取其中的注册名，
     * 便于从日志或命令输出里复制粘贴整串物品信息。
     *
     * @throws IllegalArgumentException 无法识别
     */
    public static String[] parseEntry(String source) {
        if (source == null) {
            throw new IllegalArgumentException("null");
        }
        String trimmed = source.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("blank");
        }

        // 原始 JSON（日志里的 {"id":"mod:item",...} 或 ["mod:item"] 之类）
        char first = trimmed.charAt(0);
        if (first == '{' || first == '[') {
            String id = extractRegistryName(trimmed);
            if (id == null) {
                throw new IllegalArgumentException(trimmed);
            }
            trimmed = id;
        }

        if (trimmed.startsWith("!")) {
            throw new IllegalArgumentException(trimmed);
        }

        int colon = trimmed.indexOf(':');
        if (colon < 0) {
            // 只有命名空间 → 整个模组让位
            if (!isValidNamespace(trimmed)) {
                throw new IllegalArgumentException(trimmed);
            }
            return new String[]{trimmed, null};
        }

        String namespace = trimmed.substring(0, colon);
        String path = trimmed.substring(colon + 1);
        if (!isValidNamespace(namespace)) {
            throw new IllegalArgumentException(trimmed);
        }
        if ("*".equals(path)) {
            // namespace:* → 等价于整个模组
            return new String[]{namespace, null};
        }
        if (!isValidPath(path)) {
            throw new IllegalArgumentException(trimmed);
        }
        return new String[]{null, namespace + ":" + path};
    }

    /**
     * 从原始 JSON 文本里抽取 {@code id} 字段或单个字符串元素。
     *
     * @return 注册名，抽取失败返回 null
     */
    static String extractRegistryName(String jsonText) {
        try {
            JsonElement root = GSON.fromJson(jsonText, JsonElement.class);
            if (root == null) {
                return null;
            }
            if (root.isJsonArray()) {
                for (JsonElement element : root.getAsJsonArray()) {
                    String found = extractRegistryName(element);
                    if (found != null) {
                        return found;
                    }
                }
                return null;
            }
            return extractRegistryName(root);
        } catch (JsonSyntaxException e) {
            return null;
        }
    }

    private static String extractRegistryName(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        JsonObject obj = element.getAsJsonObject();
        JsonElement idElement = obj.get("id");
        if (idElement == null || !idElement.isJsonPrimitive()) {
            return null;
        }
        String id = idElement.getAsString().trim();
        return id.isEmpty() ? null : id;
    }

    private static boolean isValidNamespace(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9') && c != '_' && c != '-' && c != '.') {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidPath(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9') && c != '_' && c != '-' && c != '.' && c != '/') {
                return false;
            }
        }
        return true;
    }

    // ══════════════════════════════════════════════════════════
    // 内部实现
    // ══════════════════════════════════════════════════════════

    private static Result mutate(Path file, String source, boolean add) throws IOException {
        String[] entry = parseEntry(source);
        String namespace = entry[0];
        String registryName = entry[1];

        JsonObject root = readRoot(file);
        boolean enabled = getBoolean(root.getAsJsonObject("bypass"), "enabled", true);
        Set<String> mods = readList(root, "mods");
        Set<String> items = readList(root, "items");

        boolean changed = add
                ? (namespace != null ? mods.add(namespace) : items.add(registryName))
                : (namespace != null ? mods.remove(namespace) : items.remove(registryName));

        Status status;
        if (add) {
            status = changed ? Status.ADDED : Status.ALREADY_PRESENT;
        } else {
            status = changed ? Status.REMOVED : Status.NOT_PRESENT;
        }

        if (changed) {
            try {
                writeLists(file, root, mods, items);
            } catch (IOException e) {
                // 内存中的名单已经变更，但磁盘没写上：如实报告失败，调用方决定如何提示
                return new Result(Status.WRITE_FAILED, enabled, mods, items);
            }
        }
        return new Result(status, enabled, mods, items);
    }

    /** 读取并校验根对象。 */
    private static JsonObject readRoot(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = GSON.fromJson(reader, JsonElement.class);
            if (parsed == null || !parsed.isJsonObject()) {
                throw new JsonSyntaxException("root is not a JSON object: " + file);
            }
            return parsed.getAsJsonObject();
        }
    }

    /** 读取 {@code bypass.<key>} 字符串数组；缺失或类型不符时返回空集合。 */
    private static Set<String> readList(JsonObject root, String key) {
        Set<String> result = new LinkedHashSet<>();
        JsonObject bypass = root.getAsJsonObject("bypass");
        if (bypass == null) {
            return result;
        }
        JsonElement element = bypass.get(key);
        if (element == null || !element.isJsonArray()) {
            return result;
        }
        for (JsonElement entry : element.getAsJsonArray()) {
            if (entry == null || entry.isJsonNull() || !entry.isJsonPrimitive()) {
                continue;
            }
            String value = entry.getAsString().trim();
            if (!value.isEmpty()) {
                result.add(value);
            }
        }
        return result;
    }

    /**
     * 把两个集合写回 {@code bypass.mods} / {@code bypass.items}，其余内容保持原样。
     * <p>
     * 按“先删除后插入”替换数组节点，保证新数组出现在 {@code bypass} 块内且位置稳定。
     */
    private static void writeLists(Path file, JsonObject root, Set<String> mods, Set<String> items) throws IOException {
        JsonObject bypass = root.getAsJsonObject("bypass");
        if (bypass == null) {
            bypass = new JsonObject();
            root.add("bypass", bypass);
        }
        if (!bypass.has("enabled")) {
            bypass.addProperty("enabled", true);
        }
        // 保持 enabled 在最前、mods 次之、items 最后，便于人读
        boolean enabledValue = getBoolean(bypass, "enabled", true);
        bypass.remove("enabled");
        bypass.remove("mods");
        bypass.remove("items");

        JsonObject rebuilt = new JsonObject();
        rebuilt.addProperty("enabled", enabledValue);
        rebuilt.add("mods", toJsonArray(mods));
        rebuilt.add("items", toJsonArray(items));

        JsonObject newRoot = new JsonObject();
        boolean replaced = false;
        for (var entry : root.entrySet()) {
            if ("bypass".equals(entry.getKey())) {
                newRoot.add("bypass", rebuilt);
                replaced = true;
            } else {
                newRoot.add(entry.getKey(), entry.getValue());
            }
        }
        if (!replaced) {
            newRoot.add("bypass", rebuilt);
        }

        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(newRoot, writer);
            writer.write(System.lineSeparator());
        }
    }

    private static JsonArray toJsonArray(Set<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    private static boolean getBoolean(JsonObject obj, String key, boolean defaultValue) {
        if (obj == null) {
            return defaultValue;
        }
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) {
            return defaultValue;
        }
        try {
            return element.getAsBoolean();
        } catch (RuntimeException e) {
            return defaultValue;
        }
    }

    /**
     * 便捷入口：与 {@link #add(Path, String)} 相同，但读取失败也归一化为
     * {@link Status#WRITE_FAILED}，绝不抛出 IO 异常（命令层直接用这个）。
     */
    public static Result addQuietly(Path file, String source) {
        try {
            return add(file, source);
        } catch (IOException e) {
            return failed();
        }
    }

    /**
     * 便捷入口：与 {@link #remove(Path, String)} 相同，但读取失败也归一化为
     * {@link Status#WRITE_FAILED}。
     */
    public static Result removeQuietly(Path file, String source) {
        try {
            return remove(file, source);
        } catch (IOException e) {
            return failed();
        }
    }

    /** 读写失败时的空结果。 */
    private static Result failed() {
        return new Result(Status.WRITE_FAILED, true, new LinkedHashSet<>(), new LinkedHashSet<>());
    }
}
