package org.yanbwe.colortooltips.client;

import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.yanbwe.colortooltips.Config;

import java.net.URI;

/**
 * 当 RarityCore 未安装时显示的警告界面。
 * 提示用户 RarityCore 的重要性，并提供四个按钮：
 * 1. 确认并进入游戏
 * 2. 打开 Modrinth 页面
 * 3. 打开 CurseForge 页面
 * 4. 禁用此提示
 */
public class MissingRarityScreen extends Screen {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int BUTTON_WIDTH = 300;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 24;

    private MultiLineLabel messageLabel;

    public MissingRarityScreen() {
        super(Component.translatable("gui.colortooltips.missing_raritycore.title"));
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int startY = Math.max(40, this.height / 2 - 60);

        this.messageLabel = MultiLineLabel.create(
            this.font,
            Component.translatable("gui.colortooltips.missing_raritycore.message"),
            this.width - 80
        );

        int buttonY = startY + 60;

        // 按钮1：已告知，进入游戏
        this.addRenderableWidget(
            Button.builder(
                    Component.translatable("gui.colortooltips.missing_raritycore.btn.acknowledge"),
                    button -> this.minecraft.setScreen(new TitleScreen())
                )
                .pos(centerX - BUTTON_WIDTH / 2, buttonY)
                .size(BUTTON_WIDTH, BUTTON_HEIGHT)
                .build()
        );

        // 按钮2：RarityCore on Modrinth
        this.addRenderableWidget(
            Button.builder(
                    Component.translatable("gui.colortooltips.missing_raritycore.btn.modrinth"),
                    button -> openUrl("https://modrinth.com/mod/raritycore")
                )
                .pos(centerX - BUTTON_WIDTH / 2, buttonY + BUTTON_SPACING)
                .size(BUTTON_WIDTH, BUTTON_HEIGHT)
                .build()
        );

        // 按钮3：RarityCore on CurseForge
        this.addRenderableWidget(
            Button.builder(
                    Component.translatable("gui.colortooltips.missing_raritycore.btn.curseforge"),
                    button -> openUrl("https://www.curseforge.com/minecraft/mc-mods/raritycore")
                )
                .pos(centerX - BUTTON_WIDTH / 2, buttonY + BUTTON_SPACING * 2)
                .size(BUTTON_WIDTH, BUTTON_HEIGHT)
                .build()
        );

        // 按钮4：不再提示
        this.addRenderableWidget(
            Button.builder(
                    Component.translatable("gui.colortooltips.missing_raritycore.btn.disable"),
                    button -> {
                        Config.SHOW_RARITY_CORE_WARNING.set(false);
                        Config.SPEC.save();
                        this.minecraft.setScreen(new TitleScreen());
                    }
                )
                .pos(centerX - BUTTON_WIDTH / 2, buttonY + BUTTON_SPACING * 3)
                .size(BUTTON_WIDTH, BUTTON_HEIGHT)
                .build()
        );
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int textY = Math.max(20, this.height / 2 - 80);

        // 渲染标题
        guiGraphics.drawCenteredString(this.font, this.title, centerX, textY, 0xFFFFFF);

        // 渲染消息正文
        this.messageLabel.renderCentered(guiGraphics, centerX, textY + 15, 9, 0xCCCCCC);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    private static void openUrl(String url) {
        try {
            Util.getPlatform().openUri(new URI(url));
        } catch (Exception e) {
            LOGGER.error("Failed to open URL: {}", url, e);
        }
    }
}
