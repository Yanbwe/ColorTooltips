package org.yanbwe.colortooltips.events;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

public class ColorTooltipPostEvent extends RenderTooltipEvent
{
    private final int width;
    private final int height;
    private boolean shouldRender = true;

    public ColorTooltipPostEvent(ItemStack stack, GuiGraphics graphics, int x, int y, Font font, int width, int height, List<ClientTooltipComponent> components)
    {
        super(stack, graphics, x, y, font, components);
        this.width = width;
        this.height = height;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public boolean shouldRender() { return shouldRender; }
    public void setShouldRender(boolean shouldRender) { this.shouldRender = shouldRender; }
}
