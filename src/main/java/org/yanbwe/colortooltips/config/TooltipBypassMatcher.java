package org.yanbwe.colortooltips.config;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 提示框让位名单匹配器。
 * <p>
 * 用于判定某个物品是否「已由其它模组作者魔改过提示框」，从而让本模组放弃接管、
 * 交还原版渲染（对应 issue #16）。
 * <p>
 * 本类刻意不依赖任何 Minecraft 类：调用方负责从 {@code ItemStack} 取出命名空间与
 * 注册名后传入，因此可脱离游戏环境做单元测试。
 * <p>
 * Bypass list matcher for tooltips. Deliberately free of Minecraft types so it can be
 * unit tested outside the game; the caller extracts namespace and registry name from the stack.
 */
public final class TooltipBypassMatcher {

    private boolean enabled = true;
    private final Set<String> mods = new LinkedHashSet<>();
    private final Set<String> items = new LinkedHashSet<>();

    /**
     * 创建一个空名单匹配器（默认启用，但无任何条目 → 不匹配任何物品）。
     * <p>
     * 每次返回新实例：本类是可变的（{@link #setLists}），共享实例会互相污染。
     */
    public static TooltipBypassMatcher empty() {
        return new TooltipBypassMatcher();
    }

    /** @return 让位名单总开关是否启用 */
    public boolean isEnabled() {
        return enabled;
    }

    /** @return 命中即整体让位的命名空间集合（副本，按加载顺序） */
    public Set<String> getMods() {
        return new LinkedHashSet<>(mods);
    }

    /** @return 命中即让位的物品注册名集合（副本，按加载顺序） */
    public Set<String> getItems() {
        return new LinkedHashSet<>(items);
    }

    /** @return 名单是否为空（无任何条目） */
    public boolean isEmpty() {
        return mods.isEmpty() && items.isEmpty();
    }

    /**
     * 替换名单内容并设置总开关。
     *
     * @param enabled 总开关
     * @param mods    命名空间列表，可为 null
     * @param items   物品注册名列表，可为 null
     */
    public void setLists(boolean enabled, Collection<String> mods, Collection<String> items) {
        this.enabled = enabled;
        this.mods.clear();
        this.items.clear();
        addAll(this.mods, mods);
        addAll(this.items, items);
    }

    /**
     * 判断给定物品是否在让位名单内。
     *
     * @param namespace    物品命名空间，如 {@code "slashblade"}
     * @param registryName 物品完整注册名，如 {@code "slashblade:slashblade"}
     * @return 命中且总开关开启时返回 true
     */
    public boolean matches(String namespace, String registryName) {
        if (!enabled) {
            return false;
        }
        if (namespace != null && !namespace.isEmpty() && mods.contains(namespace)) {
            return true;
        }
        return registryName != null && !registryName.isEmpty() && items.contains(registryName);
    }

    /**
     * 写入集合时统一裁剪空白并跳过空串，避免配置文件里的多余空格导致静默不生效。
     */
    private static void addAll(Set<String> target, Collection<String> source) {
        if (source == null) {
            return;
        }
        for (String value : source) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                target.add(trimmed);
            }
        }
    }
}
