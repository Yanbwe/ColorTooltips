package org.yanbwe.colortooltips.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;
import org.yanbwe.colortooltips.ColorTooltips;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置管理器 — JSON 配置加载、管理与热重载的核心单例。
 * <p>
 * 替代原有的 ForgeConfigSpec 配置系统，从 {@code config/colortooltips/common.json}
 * 和 {@code config/colortooltips/styles/*.json} 加载配置。
 * <p>
 * 在构造时自动完成首次加载，提供 {@link #reload()} 用于热重载。
 * 通过 {@link #getInstance()} 获取全局唯一实例。
 * <p>
 * NeoForge 1.21.1 适配：FMLPaths 路径不变，移除了 @Mod.EventBusSubscriber，
 * 改用 ColorTooltipsClient 中手动调用初始化。
 */
public final class ConfigManager {

    // ══════════════════════════════════════════════════════════
    // 单例
    // ══════════════════════════════════════════════════════════

    private static ConfigManager INSTANCE;

    private static final Gson GSON = new GsonBuilder().setLenient().setPrettyPrinting().create();

    // ══════════════════════════════════════════════════════════
    // 路径常量
    // ══════════════════════════════════════════════════════════

    private final Path commonConfigPath;
    private final Path stylesDirPath;

    // ══════════════════════════════════════════════════════════
    // 全局设置
    // ══════════════════════════════════════════════════════════

    private boolean smoothColor = true;
    private boolean tooltipLockEnabled = true;
    private double lockSensitivity = 10.0;
    private boolean onlyTextTooltipsEnabled = true;

    // ══════════════════════════════════════════════════════════
    // 选择器引擎
    // ══════════════════════════════════════════════════════════

    private StyleSelector styleSelector;

    // ══════════════════════════════════════════════════════════
    // 样式映射
    // ══════════════════════════════════════════════════════════

    private final Map<String, StyleDefinition> styles = new LinkedHashMap<>();

    // ══════════════════════════════════════════════════════════
    // 颜色流动引擎
    // ══════════════════════════════════════════════════════════

    private final ColorFlowAnimator animator = new ColorFlowAnimator();

    // ══════════════════════════════════════════════════════════
    // 加载警告
    // ══════════════════════════════════════════════════════════

    private final List<String> loadWarnings = new ArrayList<>();

    // ══════════════════════════════════════════════════════════
    // 构造与初始化
    // ══════════════════════════════════════════════════════════

    private ConfigManager() {
        this.commonConfigPath = FMLPaths.CONFIGDIR.get().resolve("colortooltips").resolve("common.json");
        this.stylesDirPath = FMLPaths.CONFIGDIR.get().resolve("colortooltips").resolve("styles");
    }

    public static ConfigManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ConfigManager();
            INSTANCE.load();
        }
        return INSTANCE;
    }

    /**
     * 强制初始化单例（客户端启动时调用）。
     * NeoForge 1.21.1 适配：替代 @Mod.EventBusSubscriber onClientSetup。
     */
    public static void init() {
        getInstance();
        ColorTooltips.LOGGER.info("ConfigManager: initialized");
    }

    private void load() {
        ensureDirectoriesExist();
        ensureDefaultFilesExist();
        loadCommonConfig();
        loadStyleFiles();
    }

    // ══════════════════════════════════════════════════════════
    // 公共 API
    // ══════════════════════════════════════════════════════════

    public boolean isEnabled() { return true; }

    public List<String> getLoadWarnings() {
        return new ArrayList<>(loadWarnings);
    }

    public List<String> consumeLoadWarnings() {
        List<String> copy = new ArrayList<>(loadWarnings);
        loadWarnings.clear();
        return copy;
    }

    public boolean isSmoothColor() { return smoothColor; }
    public boolean isTooltipLockEnabled() { return tooltipLockEnabled; }
    public double getLockSensitivity() { return lockSensitivity; }
    public boolean isOnlyTextTooltipsEnabled() { return onlyTextTooltipsEnabled; }

    public StyleDefinition getStyle(String styleName) {
        if (styleName == null || styleName.isEmpty()) return getFallbackStyle();
        StyleDefinition style = styles.get(styleName);
        return style != null ? style : getFallbackStyle();
    }

    public StyleDefinition getStyleForStack(ItemStack stack, boolean isTextOnly) {
        String styleName;
        if (isTextOnly) {
            styleName = styleSelector.selectStyleName(stack, true);
        } else if (stack == null || stack.isEmpty()) {
            return getFallbackStyle();
        } else {
            styleName = styleSelector.selectStyleName(stack, false);
        }
        StyleDefinition style = styles.get(styleName);
        return style != null ? style : getFallbackStyle();
    }

    public ColorFlowAnimator getColorFlowAnimator() { return animator; }

    public List<String> reload() {
        loadWarnings.clear();
        styles.clear();
        animator.fullReset();
        load();
        ColorTooltips.LOGGER.info("ConfigManager: configuration reloaded");
        return new ArrayList<>(loadWarnings);
    }

    // ══════════════════════════════════════════════════════════
    // 文件初始化
    // ══════════════════════════════════════════════════════════

    private void ensureDirectoriesExist() {
        try {
            Path configDir = commonConfigPath.getParent();
            if (configDir != null) Files.createDirectories(configDir);
            Files.createDirectories(stylesDirPath);
        } catch (IOException e) {
            ColorTooltips.LOGGER.error("ConfigManager: failed to create config directories", e);
        }
    }

    private void ensureDefaultFilesExist() {
        if (!Files.exists(commonConfigPath)) {
            try {
                Files.writeString(commonConfigPath, getDefaultCommonJson());
                ColorTooltips.LOGGER.info("ConfigManager: created default common.json");
            } catch (IOException e) {
                ColorTooltips.LOGGER.error("ConfigManager: failed to create default common.json", e);
            }
        }
        ensureDefaultStyleFile("Vanilla.json");
        ensureDefaultStyleFile("RarityCoreStyles.json");
        ensureDefaultStyleFile("RGB.json");
        ensureDefaultStyleFile("VanillaRarity.json");
    }

    private void ensureDefaultStyleFile(String fileName) {
        Path filePath = stylesDirPath.resolve(fileName);
        if (!Files.exists(filePath)) {
            try {
                String resourcePath = "/config/colortooltips/styles/" + fileName;
                java.io.InputStream in = ConfigManager.class.getResourceAsStream(resourcePath);
                if (in != null) {
                    Files.copy(in, filePath);
                    in.close();
                    ColorTooltips.LOGGER.info("ConfigManager: copied default style from resource: {}", fileName);
                } else {
                    StyleDefinition fallback = StyleDefinition.createDefault();
                    Files.writeString(filePath, GSON.toJson(fallback));
                    ColorTooltips.LOGGER.warn("ConfigManager: resource not found, using fallback style for {}", fileName);
                }
            } catch (IOException e) {
                ColorTooltips.LOGGER.error("ConfigManager: failed to create default style {}", fileName, e);
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    // JSON 加载 — common.json
    // ══════════════════════════════════════════════════════════

    private void loadCommonConfig() {
        try (BufferedReader reader = Files.newBufferedReader(commonConfigPath)) {
            JsonObject root = GSON.fromJson(new JsonReader(reader), JsonObject.class);
            if (root == null) { applyDefaults(); return; }
            parseCommonConfig(root);
        } catch (IOException | JsonSyntaxException e) {
            ColorTooltips.LOGGER.error("ConfigManager: failed to load common.json, using defaults", e);
            loadWarnings.add("common.json");
            applyDefaults();
        }
    }

    private void parseCommonConfig(JsonObject root) {
        JsonElement smoothEl = root.get("smoothColor");
        smoothColor = (smoothEl != null && !smoothEl.isJsonNull()) ? smoothEl.getAsBoolean() : true;

        JsonObject lockObj = root.getAsJsonObject("tooltipLock");
        if (lockObj != null) {
            tooltipLockEnabled = getJsonBoolean(lockObj, "enabled", true);
            lockSensitivity = getJsonDouble(lockObj, "sensitivity", 10.0);
        } else {
            tooltipLockEnabled = true;
            lockSensitivity = 10.0;
        }

        JsonObject textObj = root.getAsJsonObject("onlyTextTooltips");
        if (textObj != null) {
            onlyTextTooltipsEnabled = getJsonBoolean(textObj, "enabled", true);
        } else {
            onlyTextTooltipsEnabled = true;
        }

        JsonObject selectorRoot = root.getAsJsonObject("styleSelector");
        StyleSelector.SelectorBlock commonBlock;
        StyleSelector.SelectorBlock rarityCoreBlock;
        if (selectorRoot != null) {
            commonBlock = parseSelectorBlock(selectorRoot.getAsJsonObject("common"));
            rarityCoreBlock = parseSelectorBlock(selectorRoot.getAsJsonObject("rarityCore"));
        } else {
            commonBlock = createDefaultCommonBlock();
            rarityCoreBlock = createDefaultRarityCoreBlock();
        }
        styleSelector = new StyleSelector(commonBlock, rarityCoreBlock);
    }

    private StyleSelector.SelectorBlock parseSelectorBlock(JsonObject blockObj) {
        StyleSelector.SelectorBlock block = new StyleSelector.SelectorBlock();
        if (blockObj == null) return block;

        JsonElement onlyTextEl = blockObj.get("onlyText");
        block.onlyText = (onlyTextEl != null && !onlyTextEl.isJsonNull()) ? onlyTextEl.getAsString() : null;

        JsonObject itemsObj = blockObj.getAsJsonObject("items");
        if (itemsObj != null) {
            for (Map.Entry<String, JsonElement> entry : itemsObj.entrySet()) {
                JsonElement val = entry.getValue();
                if (val != null && !val.isJsonNull()) block.items.put(entry.getKey(), val.getAsString());
            }
        }

        for (Map.Entry<String, JsonElement> entry : blockObj.entrySet()) {
            String key = entry.getKey();
            if ("onlyText".equals(key) || "items".equals(key)) continue;
            JsonElement val = entry.getValue();
            if (val != null && val.isJsonNull()) continue;
            if (val == null || !val.isJsonPrimitive()) continue;
            String styleName = val.getAsString();
            block.rarity.put(key, styleName);
            if ("*".equals(key)) block.fallback = styleName;
        }
        return block;
    }

    private void applyDefaults() {
        smoothColor = true;
        tooltipLockEnabled = true;
        lockSensitivity = 10.0;
        onlyTextTooltipsEnabled = true;
        styleSelector = new StyleSelector(createDefaultCommonBlock(), createDefaultRarityCoreBlock());
    }

    // ══════════════════════════════════════════════════════════
    // JSON 加载 — styles/*.json
    // ══════════════════════════════════════════════════════════

    private void loadStyleFiles() {
        if (!Files.isDirectory(stylesDirPath)) return;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(stylesDirPath, "*.json")) {
            for (Path file : stream) {
                String fileName = file.getFileName().toString();
                String styleName = fileName.substring(0, fileName.length() - ".json".length());
                loadSingleStyle(file, styleName);
            }
        } catch (IOException e) {
            ColorTooltips.LOGGER.error("ConfigManager: failed to list style files", e);
        }

        if (styles.isEmpty()) {
            styles.put("Vanilla", StyleDefinition.createDefault());
            ColorTooltips.LOGGER.warn("ConfigManager: no style files loaded, using built-in default");
        }
    }

    private void loadSingleStyle(Path file, String styleName) {
        try {
            String json = Files.readString(file);
            StyleDefinition style = StyleDefinition.fromJson(json);
            styles.put(styleName, style);
        } catch (IOException e) {
            ColorTooltips.LOGGER.error("ConfigManager: failed to read style file {}", file, e);
            loadWarnings.add(styleName);
            styles.put(styleName, StyleDefinition.createDefault());
        }
    }

    // ══════════════════════════════════════════════════════════
    // 默认配置 JSON
    // ══════════════════════════════════════════════════════════

    private String getDefaultCommonJson() {
        return "{\n" +
               "  \"styleSelector\": {\n" +
               "    \"common\": {\n" +
               "      \"onlyText\": \"Vanilla\",\n" +
               "      \"Common\": \"Vanilla\",\n" +
               "      \"Uncommon\": \"VanillaRarity\",\n" +
               "      \"Rare\": \"VanillaRarity\",\n" +
               "      \"Epic\": \"VanillaRarity\",\n" +
               "      \"items\": {}\n" +
               "    },\n" +
               "    \"rarityCore\": {\n" +
               "      \"onlyText\": \"Vanilla\",\n" +
               "      \"*\": \"RarityCoreStyles\",\n" +
               "      \"items\": {}\n" +
               "    }\n" +
               "  },\n" +
               "  \"tooltipLock\": {\n" +
               "    \"enabled\": true,\n" +
               "    \"sensitivity\": 10.0\n" +
               "  },\n" +
               "  \"onlyTextTooltips\": {\n" +
               "    \"enabled\": true\n" +
               "  },\n" +
               "  \"smoothColor\": true\n" +
               "}\n";
    }

    private StyleSelector.SelectorBlock createDefaultCommonBlock() {
        StyleSelector.SelectorBlock block = new StyleSelector.SelectorBlock();
        block.onlyText = "Vanilla";
        block.fallback = "Vanilla";
        block.rarity.put("*", "Vanilla");
        block.rarity.put("Common", "Vanilla");
        block.rarity.put("Uncommon", "VanillaRarity");
        block.rarity.put("Rare", "VanillaRarity");
        block.rarity.put("Epic", "VanillaRarity");
        return block;
    }

    private StyleSelector.SelectorBlock createDefaultRarityCoreBlock() {
        StyleSelector.SelectorBlock block = new StyleSelector.SelectorBlock();
        block.onlyText = "Vanilla";
        block.fallback = "RarityCoreStyles";
        block.rarity.put("*", "RarityCoreStyles");
        return block;
    }

    private static boolean getJsonBoolean(JsonObject obj, String key, boolean defaultValue) {
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) return defaultValue;
        try { return el.getAsBoolean(); } catch (Exception e) { return defaultValue; }
    }

    private static double getJsonDouble(JsonObject obj, String key, double defaultValue) {
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) return defaultValue;
        try { return el.getAsDouble(); } catch (Exception e) { return defaultValue; }
    }

    public String getStyleNameForStack(ItemStack stack, boolean isTextOnly) {
        if (styleSelector == null) return null;
        if (isTextOnly) return styleSelector.selectStyleName(stack, true);
        if (stack == null || stack.isEmpty()) return null;
        return styleSelector.selectStyleName(stack, false);
    }

    private StyleDefinition getFallbackStyle() {
        if (!styles.isEmpty()) return styles.values().iterator().next();
        return StyleDefinition.createDefault();
    }
}
