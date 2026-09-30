package org.yanbwe.colortooltips.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.yanbwe.colortooltips.config.BypassListEditor;
import org.yanbwe.colortooltips.config.ConfigManager;
import org.yanbwe.colortooltips.config.TooltipBypassMatcher;

import java.util.List;

/**
 * {@code /colortooltips bypass} 客户端命令 — 用一条命令把物品加入/移出让位名单。
 * <p>
 * 让位名单命中的物品，ColorTooltips 完全不接管提示框（对应 issue #16）。名单写入
 * {@code config/colortooltips/common.json} 的 {@code bypass} 块并立即生效。
 * <p>
 * Client command tree for the tooltip bypass list. The list is persisted to
 * {@code common.json} and applied to the in-memory matcher right away.
 */
public final class ColorTooltipsCommand {

    private static final String ARG_ITEM = "item";
    private static final String ARG_ID = "id";

    /** 物品来源：手持 / 鼠标悬停槽位。 */
    private enum ItemSource {
        HELD("colortooltips.bypass.err.not_holding"),
        HOVERED("colortooltips.bypass.err.not_hovering");

        private final String errorKey;

        ItemSource(String errorKey) {
            this.errorKey = errorKey;
        }

        String errorKey() {
            return errorKey;
        }
    }

    private ColorTooltipsCommand() {
    }

    /**
     * 注册整个 {@code /colortooltips} 命令树（含原有的 {@code reload}）。
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("colortooltips")
                .then(buildReload())
                .then(buildBypass());
        dispatcher.register(root);
    }

    // ══════════════════════════════════════════════════════════
    // reload
    // ══════════════════════════════════════════════════════════

    private static LiteralArgumentBuilder<CommandSourceStack> buildReload() {
        return Commands.literal("reload").executes(ctx -> {
            List<String> warnings = ConfigManager.getInstance().reload();
            ctx.getSource().sendSuccess(() -> Component.translatable("colortooltips.config.reload_success"), false);
            for (String name : warnings) {
                ctx.getSource().sendFailure(Component.translatable("colortooltips.config.load_failed", name));
            }
            return 1;
        });
    }

    // ══════════════════════════════════════════════════════════
    // bypass 子树
    // ══════════════════════════════════════════════════════════

    private static LiteralArgumentBuilder<CommandSourceStack> buildBypass() {
        return Commands.literal("bypass")
                .executes(ColorTooltipsCommand::runStatus)
                .then(Commands.literal("status").executes(ColorTooltipsCommand::runStatus))
                .then(Commands.literal("list").executes(ColorTooltipsCommand::runList))
                .then(Commands.literal("check")
                        .executes(ctx -> runCheck(ctx, ItemSource.HELD))
                        .then(Commands.literal("hand").executes(ctx -> runCheck(ctx, ItemSource.HELD)))
                        .then(Commands.literal("hover").executes(ctx -> runCheck(ctx, ItemSource.HOVERED)))
                        .then(Commands.argument(ARG_ID, StringArgumentType.greedyString())
                                .executes(ctx -> runCheckId(ctx, StringArgumentType.getString(ctx, ARG_ID)))))
                .then(Commands.literal("add")
                        .then(Commands.literal("hand").executes(ctx -> runMutate(ctx, true, ItemSource.HELD)))
                        .then(Commands.literal("hover").executes(ctx -> runMutate(ctx, true, ItemSource.HOVERED)))
                        .then(Commands.argument(ARG_ITEM, StringArgumentType.greedyString())
                                .executes(ctx -> runMutate(ctx, true, StringArgumentType.getString(ctx, ARG_ITEM)))))
                .then(Commands.literal("remove")
                        .then(Commands.literal("hand").executes(ctx -> runMutate(ctx, false, ItemSource.HELD)))
                        .then(Commands.literal("hover").executes(ctx -> runMutate(ctx, false, ItemSource.HOVERED)))
                        .then(Commands.argument(ARG_ITEM, StringArgumentType.greedyString())
                                .executes(ctx -> runMutate(ctx, false, StringArgumentType.getString(ctx, ARG_ITEM)))));
    }

    // ══════════════════════════════════════════════════════════
    // 命令实现
    // ══════════════════════════════════════════════════════════

    /** {@code /colortooltips bypass} — 打印当前状态。 */
    private static int runStatus(CommandContext<CommandSourceStack> ctx) {
        ConfigManager cm = ConfigManager.getInstance();
        TooltipBypassMatcher matcher = cm.getTooltipBypass();

        ctx.getSource().sendSuccess(() -> header("colortooltips.bypass.title"), false);
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "colortooltips.bypass.status",
                onOff(matcher.isEnabled()),
                matcher.getMods().size(),
                matcher.getItems().size()), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("colortooltips.bypass.hint"), false);
        return 1;
    }

    /** {@code /colortooltips bypass list} — 逐条列出名单内容。 */
    private static int runList(CommandContext<CommandSourceStack> ctx) {
        ConfigManager cm = ConfigManager.getInstance();
        TooltipBypassMatcher matcher = cm.getTooltipBypass();

        ctx.getSource().sendSuccess(() -> header("colortooltips.bypass.title"), false);
        if (matcher.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("colortooltips.bypass.list_empty"), false);
            return 1;
        }
        for (String mod : matcher.getMods()) {
            ctx.getSource().sendSuccess(() -> line(Component.translatable("colortooltips.bypass.entry_mod", mod)), false);
        }
        for (String item : matcher.getItems()) {
            ctx.getSource().sendSuccess(() -> line(Component.translatable("colortooltips.bypass.entry_item", item)), false);
        }
        return 1;
    }

    /** {@code /colortooltips bypass check [hand|hover]} — 查询来源物品是否已让位。 */
    private static int runCheck(CommandContext<CommandSourceStack> ctx, ItemSource source) {
        ItemStack stack = resolve(source);
        if (stack == null) {
            ctx.getSource().sendFailure(Component.translatable(source.errorKey()));
            return 0;
        }
        ConfigManager cm = ConfigManager.getInstance();
        TooltipBypassMatcher matcher = cm.getTooltipBypass();
        String id = registryName(stack);
        int colon = id.indexOf(':');
        boolean bypassed = colon < 0
                ? matcher.matches(id, null)
                : matcher.matches(id.substring(0, colon), id);
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "colortooltips.bypass.check_result",
                id,
                yesNo(bypassed)), false);
        reportMatchedEntry(ctx, matcher, id, bypassed);
        return 1;
    }

    /** {@code /colortooltips bypass check <id>} — 按注册名查询（无需拿着物品）。 */
    private static int runCheckId(CommandContext<CommandSourceStack> ctx, String raw) {
        String[] entry;
        try {
            entry = BypassListEditor.parseEntry(raw);
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.translatable("colortooltips.bypass.err.bad_item", raw));
            return 0;
        }
        String namespace = entry[0];
        String registryName = entry[1];
        TooltipBypassMatcher matcher = ConfigManager.getInstance().getTooltipBypass();
        boolean bypassed = namespace != null ? matcher.matches(namespace, null) : matcher.matches(null, registryName);

        String display = namespace != null ? namespace + ":*" : registryName;
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "colortooltips.bypass.check_result",
                display,
                yesNo(bypassed)), false);
        reportMatchedEntry(ctx, matcher, registryName == null ? namespace : registryName, bypassed);
        return 1;
    }

    /** 已知物品注册名时补充说明命中的是哪条（mods 还是 items）。 */
    private static void reportMatchedEntry(CommandContext<CommandSourceStack> ctx,
                                           TooltipBypassMatcher matcher,
                                           String registryName,
                                           boolean bypassed) {
        if (!bypassed || registryName == null) {
            return;
        }
        // 去掉 ":*" 之类的修饰，取命名空间
        String namespace = registryName.contains(":") ? registryName.substring(0, registryName.indexOf(':')) : registryName;
        if (matcher.getMods().contains(namespace)) {
            ctx.getSource().sendSuccess(
                    () -> line(Component.translatable("colortooltips.bypass.matched_mod", namespace)), false);
        } else if (matcher.getItems().contains(registryName)) {
            ctx.getSource().sendSuccess(
                    () -> line(Component.translatable("colortooltips.bypass.matched_item", registryName)), false);
        }
    }

    /** {@code /colortooltips bypass add|remove hand} — 取手持物品。 */
    private static int runMutate(CommandContext<CommandSourceStack> ctx, boolean add, ItemSource source) {
        ItemStack stack = resolve(source);
        if (stack == null) {
            ctx.getSource().sendFailure(Component.translatable(source.errorKey()));
            return 0;
        }
        return mutate(ctx, add, registryName(stack));
    }

    /** {@code /colortooltips bypass add|remove <item>} — 用给定条目。 */
    private static int runMutate(CommandContext<CommandSourceStack> ctx, boolean add, String raw) {
        return mutate(ctx, add, raw == null ? "" : raw.trim());
    }

    private static int mutate(CommandContext<CommandSourceStack> ctx, boolean add, String entry) {
        ConfigManager cm = ConfigManager.getInstance();
        BypassListEditor.Result result;
        try {
            result = add
                    ? BypassListEditor.addQuietly(cm.getCommonConfigPath(), entry)
                    : BypassListEditor.removeQuietly(cm.getCommonConfigPath(), entry);
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.translatable("colortooltips.bypass.err.bad_item", entry));
            return 0;
        }

        switch (result.getStatus()) {
            case WRITE_FAILED -> {
                ctx.getSource().sendFailure(Component.translatable("colortooltips.bypass.err.write_failed"));
                return 0;
            }
            case ALREADY_PRESENT -> {
                ctx.getSource().sendSuccess(() -> Component.translatable("colortooltips.bypass.already", entry), false);
                return 1;
            }
            case NOT_PRESENT -> {
                ctx.getSource().sendSuccess(() -> Component.translatable("colortooltips.bypass.not_present", entry), false);
                return 1;
            }
            default -> {
                // ADDED / REMOVED
            }
        }

        // 写盘成功 → 同步内存，立即生效
        cm.applyBypassLists(result.isEnabled(), result.getMods(), result.getItems());
        String key = add ? "colortooltips.bypass.added" : "colortooltips.bypass.removed";
        ctx.getSource().sendSuccess(() -> Component.translatable(key, entry), false);
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "colortooltips.bypass.count",
                result.getMods().size(),
                result.getItems().size()), false);
        return 1;
    }

    // ══════════════════════════════════════════════════════════
    // 工具
    // ══════════════════════════════════════════════════════════

    /** 从手持或悬停槽位取出物品栈；取不到返回 null。 */
    private static ItemStack resolve(ItemSource source) {
        Minecraft minecraft = Minecraft.getInstance();
        if (source == ItemSource.HELD) {
            Player player = minecraft.player;
            if (player == null) {
                return null;
            }
            ItemStack main = player.getMainHandItem();
            if (!main.isEmpty()) {
                return main;
            }
            // 主手空时退回副手，避免“明明拿着东西却报错”
            ItemStack off = player.getOffhandItem();
            return off.isEmpty() ? null : off;
        }

        Screen screen = minecraft.screen;
        if (screen instanceof AbstractContainerScreen<?> containerScreen
                && containerScreen.getSlotUnderMouse() != null) {
            ItemStack hovered = containerScreen.getSlotUnderMouse().getItem();
            return hovered.isEmpty() ? null : hovered;
        }
        return null;
    }

    /** @return 物品注册名，未注册时返回其描述 id。 */
    private static String registryName(ItemStack stack) {
        var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? stack.getItem().toString() : key.toString();
    }

    private static MutableComponent header(String key) {
        return Component.translatable(key).withStyle(ChatFormatting.GOLD);
    }

    private static MutableComponent line(MutableComponent component) {
        return component.withStyle(ChatFormatting.GRAY);
    }

    private static MutableComponent onOff(boolean value) {
        return Component.translatable(value ? "colortooltips.bypass.on" : "colortooltips.bypass.off")
                .withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private static MutableComponent yesNo(boolean value) {
        return Component.translatable(value ? "colortooltips.bypass.yes" : "colortooltips.bypass.no")
                .withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED);
    }
}
