package org.yanbwe.colortooltips.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import org.yanbwe.colortooltips.ColorTooltips;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
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
 * Core singleton for JSON config loading, management, and hot-reload.
 * Replaces the legacy ForgeConfigSpec system.
 */
@Mod.EventBusSubscriber(modid = ColorTooltips.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ConfigManager {

    // ══════════════════════════════════════════════════════════
    // 单例
    // ══════════════════════════════════════════════════════════

    private static ConfigManager INSTANCE;

    /** Gson 实例（宽松模式） */
    private static final Gson GSON = new GsonBuilder().setLenient().setPrettyPrinting().create();

    // ══════════════════════════════════════════════════════════
    // 路径常量
    // ══════════════════════════════════════════════════════════

    private final Path commonConfigPath;
    private final Path stylesDirPath;

    // ══════════════════════════════════════════════════════════
    // 全局设置（来自 common.json）
    // ══════════════════════════════════════════════════════════

    private boolean smoothColor = true;
    private boolean tooltipLockEnabled = true;
    private double lockSensitivity = 10.0;
    private boolean onlyTextTooltipsEnabled = true;

    // ══════════════════════════════════════════════════════════
    // 选择器引擎（来自 common.json styleSelector 块）
    // ══════════════════════════════════════════════════════════

    private StyleSelector styleSelector;

    // ══════════════════════════════════════════════════════════
    // 样式映射：样式名 → StyleDefinition
    // LinkedHashMap 保持插入顺序，首个样式作为降级默认
    // ══════════════════════════════════════════════════════════

    private final Map<String, StyleDefinition> styles = new LinkedHashMap<>();

    // ══════════════════════════════════════════════════════════
    // 颜色流动引擎
    // ══════════════════════════════════════════════════════════

    private final ColorFlowAnimator animator = new ColorFlowAnimator();

    // ══════════════════════════════════════════════════════════
    // 构造与初始化
    // ══════════════════════════════════════════════════════════

    private ConfigManager() {
        this.commonConfigPath = FMLPaths.CONFIGDIR.get().resolve("colortooltips").resolve("common.json");
        this.stylesDirPath = FMLPaths.CONFIGDIR.get().resolve("colortooltips").resolve("styles");
    }

    /**
     * 获取全局唯一实例，首次调用时触发自动加载。
     */
    public static ConfigManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ConfigManager();
            INSTANCE.load();
        }
        return INSTANCE;
    }

    /**
     * 首次加载：确保目录和默认文件存在，然后解析所有 JSON 配置。
     */
    private void load() {
        ensureDirectoriesExist();
        ensureDefaultFilesExist();
        loadCommonConfig();
        loadStyleFiles();
    }

    // ══════════════════════════════════════════════════════════
    // 公共 API — 兼容方法（与旧 Config.java 默认值一致）
    // ══════════════════════════════════════════════════════════

    /** @return 始终 true（同旧版 Config.ENABLED 默认值） */
    public boolean isEnabled() { return true; }

    /** @return 始终 true（同旧版 Config.CUSTOM_HEADER_ENABLED 默认值） */
    public boolean isCustomHeaderEnabled() { return true; }

    /** @return 背景透明度 0.97（同旧版 Config.BG_ALPHA 默认值） */
    public double getBgAlpha() { return 0.97; }

    /** @return 背景暗度因子 0.7（同旧版 Config.BG_DARKEN 默认值） */
    public double getBgDarken() { return 0.7; }

    // --- 兼容 stub（旧版 Config 边框/标题栏/颜色变化参数，默认值与原 Config.java 一致）---

    /** @return 边框渐变开关 */
    public boolean isBorderGradientEnabled() { return true; }

    /** @return 标题栏渐变开关 */
    public boolean isTitlebarGradientEnabled() { return true; }

    /** @return 色相变化范围 */
    public double getHueVariation() { return 0.1; }

    /** @return 明度变化范围 */
    public double getValueVariation() { return 0.4; }

    /** @return 饱和度变化范围 */
    public double getSaturationVariation() { return 0.1; }

    /** @return 拖尾分段数 */
    public int getSwitchTailSegments() { return 50; }

    /** @return 拖尾长度因子 */
    public double getSwitchTailLength() { return 1.6; }

    /** @return 拖尾透明度衰减指数 */
    public double getSwitchTailAlphaExponent() { return 0.5; }

    /** @return 淡出延迟/ms */
    public int getFadeOutDelay() { return 100; }

    /** @return 淡入时长/ms */
    public int getFadeInDuration() { return 100; }

    /** @return 闪光动画时长/ms */
    public int getSwitchFlashDuration() { return 500; }

    /** @return 物品出现最小缩放 */
    public double getItemScaleMin() { return 0.5; }

    /** @return 颜色渐变周期 */
    public double getGradientPeriod() { return 200.0; }

    /** @return 颜色滚动速度 */
    public double getScrollSpeed() { return 0.05; }

    // ══════════════════════════════════════════════════════════
    // 公共 API — 全局设置
    // ══════════════════════════════════════════════════════════

    /** @return 物品切换时颜色是否平滑过渡 */
    public boolean isSmoothColor() { return smoothColor; }

    /** @return Shift+滚轮移动提示框功能是否启用 */
    public boolean isTooltipLockEnabled() { return tooltipLockEnabled; }

    /** @return 滚轮移动提示框灵敏度 (1.0~20.0) */
    public double getLockSensitivity() { return lockSensitivity; }

    /** @return 纯文本提示框模式是否启用 */
    public boolean isOnlyTextTooltipsEnabled() { return onlyTextTooltipsEnabled; }

    // ══════════════════════════════════════════════════════════
    // 公共 API — 样式查询
    // ══════════════════════════════════════════════════════════

    /**
     * 根据样式名获取样式定义。
     *
     * @param styleName 样式名称（如 "Vanilla", "RarityCoreStyles"），区分大小写
     * @return 对应的 StyleDefinition，未找到时返回默认样式
     */
    public StyleDefinition getStyle(String styleName) {
        if (styleName == null || styleName.isEmpty()) {
            return getFallbackStyle();
        }
        StyleDefinition style = styles.get(styleName);
        return style != null ? style : getFallbackStyle();
    }

    /**
     * 根据物品栈和提示框模式选择最匹配的样式定义。
     * <p>
     * 委托给 {@link StyleSelector#selectStyleName(ItemStack, boolean)} 获取样式名，
     * 然后从样式映射中查找对应的 StyleDefinition。
     *
     * @param stack      物品栈
     * @param isTextOnly 是否为纯文本提示框模式
     * @return 匹配合适的样式定义，永不为 null
     */
    public StyleDefinition getStyleForStack(ItemStack stack, boolean isTextOnly) {
        String styleName;
        if (isTextOnly) {
            // 纯文本提示框：即使 stack 为空也走选择器（使用 onlyText 配置）
            styleName = styleSelector.selectStyleName(stack, true);
        } else if (stack == null || stack.isEmpty()) {
            return getFallbackStyle();
        } else {
            styleName = styleSelector.selectStyleName(stack, false);
        }
        StyleDefinition style = styles.get(styleName);
        return style != null ? style : getFallbackStyle();
    }

    // ══════════════════════════════════════════════════════════
    // 公共 API — 动画 & 重载
    // ══════════════════════════════════════════════════════════

    /** @return 颜色流动引擎单例 */
    public ColorFlowAnimator getColorFlowAnimator() { return animator; }

    /**
     * 热重载所有配置（common.json + styles/*.json）。
     * <p>
     * 清空现有样式映射，重新读取并解析所有 JSON 文件。
     * 同时重置颜色流动引擎状态。
     */
    public void reload() {
        styles.clear();
        animator.fullReset();
        load();
        ColorTooltips.LOGGER.info("ConfigManager: configuration reloaded");
    }

    // ══════════════════════════════════════════════════════════
    // 文件初始化（创建目录和默认文件）
    // ══════════════════════════════════════════════════════════

    private void ensureDirectoriesExist() {
        try {
            Path configDir = commonConfigPath.getParent();
            if (configDir != null) {
                Files.createDirectories(configDir);
            }
            Files.createDirectories(stylesDirPath);
        } catch (IOException e) {
            ColorTooltips.LOGGER.error("ConfigManager: failed to create config directories", e);
        }
    }

    private void ensureDefaultFilesExist() {
        // 确保 common.json 存在
        if (!Files.exists(commonConfigPath)) {
            try {
                Files.writeString(commonConfigPath, getDefaultCommonJson());
                ColorTooltips.LOGGER.info("ConfigManager: created default common.json");
            } catch (IOException e) {
                ColorTooltips.LOGGER.error("ConfigManager: failed to create default common.json", e);
            }
        }
        // 确保默认样式文件存在（从资源文件复制）
        ensureDefaultStyleFile("Vanilla.json");
        ensureDefaultStyleFile("RarityCoreStyles.json");
        ensureDefaultStyleFile("RGB.json");
        ensureDefaultStyleFile("VanillaRarity.json");
    }

    private void ensureDefaultStyleFile(String fileName) {
        Path filePath = stylesDirPath.resolve(fileName);
        if (!Files.exists(filePath)) {
            try {
                // 优先从类路径资源文件复制（保留预置的完整样式内容）
                String resourcePath = "/config/colortooltips/styles/" + fileName;
                java.io.InputStream in = ConfigManager.class.getResourceAsStream(resourcePath);
                if (in != null) {
                    Files.copy(in, filePath);
                    in.close();
                    ColorTooltips.LOGGER.info("ConfigManager: copied default style from resource: {}", fileName);
                } else {
                    // 资源缺失时降级使用内存默认样式
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
            if (root == null) {
                applyDefaults();
                return;
            }
            parseCommonConfig(root);
        } catch (IOException | JsonSyntaxException e) {
            ColorTooltips.LOGGER.error("ConfigManager: failed to load common.json, using defaults", e);
            applyDefaults();
        }
    }

    private void parseCommonConfig(JsonObject root) {
        // smoothColor
        JsonElement smoothEl = root.get("smoothColor");
        smoothColor = (smoothEl != null && !smoothEl.isJsonNull()) ? smoothEl.getAsBoolean() : true;

        // tooltipLock
        JsonObject lockObj = root.getAsJsonObject("tooltipLock");
        if (lockObj != null) {
            tooltipLockEnabled = getJsonBoolean(lockObj, "enabled", true);
            lockSensitivity = getJsonDouble(lockObj, "sensitivity", 10.0);
        } else {
            tooltipLockEnabled = true;
            lockSensitivity = 10.0;
        }

        // onlyTextTooltips
        JsonObject textObj = root.getAsJsonObject("onlyTextTooltips");
        if (textObj != null) {
            onlyTextTooltipsEnabled = getJsonBoolean(textObj, "enabled", true);
        } else {
            onlyTextTooltipsEnabled = true;
        }

        // styleSelector
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

    /**
     * 解析单个选择器块（common 或 rarityCore）。
     * 除 "onlyText" 和 "items" 外，其余键均视为稀有度映射（"*" 用作 fallback）。
     */
    private StyleSelector.SelectorBlock parseSelectorBlock(JsonObject blockObj) {
        StyleSelector.SelectorBlock block = new StyleSelector.SelectorBlock();
        if (blockObj == null) return block;

        // onlyText
        JsonElement onlyTextEl = blockObj.get("onlyText");
        block.onlyText = (onlyTextEl != null && !onlyTextEl.isJsonNull()) ? onlyTextEl.getAsString() : null;

        // items
        JsonObject itemsObj = blockObj.getAsJsonObject("items");
        if (itemsObj != null) {
            for (Map.Entry<String, JsonElement> entry : itemsObj.entrySet()) {
                JsonElement val = entry.getValue();
                if (val != null && !val.isJsonNull()) {
                    block.items.put(entry.getKey(), val.getAsString());
                }
            }
        }

        // 其余键 → 稀有度映射，首个非 onlyText/items 的键也视为稀有度
        for (Map.Entry<String, JsonElement> entry : blockObj.entrySet()) {
            String key = entry.getKey();
            if ("onlyText".equals(key) || "items".equals(key)) continue;
            JsonElement val = entry.getValue();
            if (val != null && val.isJsonNull()) continue;
            // 跳过嵌套对象（非字符串值）
            if (val == null || !val.isJsonPrimitive()) continue;
            String styleName = val.getAsString();
            block.rarity.put(key, styleName);
            // "*" 保存为 fallback
            if ("*".equals(key)) {
                block.fallback = styleName;
            }
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

        // 如果没有任何样式加载成功，添加内存中的默认样式作为降级
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
            // 降级：使用内存默认样式
            styles.put(styleName, StyleDefinition.createDefault());
        }
    }

    // ══════════════════════════════════════════════════════════
    // 默认配置 JSON 生成
    // ══════════════════════════════════════════════════════════

    /** 生成默认 common.json 内容 */
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

    /** 创建默认 common 选择器块 */
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

    /** 创建默认 rarityCore 选择器块 */
    private StyleSelector.SelectorBlock createDefaultRarityCoreBlock() {
        StyleSelector.SelectorBlock block = new StyleSelector.SelectorBlock();
        block.onlyText = "Vanilla";
        block.fallback = "RarityCoreStyles";
        block.rarity.put("*", "RarityCoreStyles");
        return block;
    }

    // ══════════════════════════════════════════════════════════
    // 辅助方法
    // ══════════════════════════════════════════════════════════

    /**
     * 从 JSON 对象获取布尔值，缺失或错误时返回默认值。
     */
    private static boolean getJsonBoolean(JsonObject obj, String key, boolean defaultValue) {
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) return defaultValue;
        try {
            return el.getAsBoolean();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 从 JSON 对象获取 double 值，缺失或错误时返回默认值。
     */
    private static double getJsonDouble(JsonObject obj, String key, double defaultValue) {
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) return defaultValue;
        try {
            return el.getAsDouble();
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 获取物品栈对应的样式名，供跨样式 crossfade 检测使用。
     *
     * @param stack      物品栈
     * @param isTextOnly 是否为纯文本提示框模式
     * @return 样式名，空栈或空时返回 null
     */
    public String getStyleNameForStack(ItemStack stack, boolean isTextOnly) {
        if (styleSelector == null) return null;
        if (isTextOnly) return styleSelector.selectStyleName(stack, true);
        if (stack == null || stack.isEmpty()) return null;
        return styleSelector.selectStyleName(stack, false);
    }

    /**
     * 降级：返回第一个加载的样式，无样式时返回内存默认样式。
     */
    private StyleDefinition getFallbackStyle() {
        if (!styles.isEmpty()) {
            return styles.values().iterator().next();
        }
        return StyleDefinition.createDefault();
    }

    // ══════════════════════════════════════════════════════════
    // FML 生命周期 — 在客户端初始化时预加载配置
    // ══════════════════════════════════════════════════════════

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // 触发单例初始化，确保配置在客户端渲染前已就绪
        getInstance();
        ColorTooltips.LOGGER.info("ConfigManager: initialized during FMLClientSetupEvent");
    }

}
