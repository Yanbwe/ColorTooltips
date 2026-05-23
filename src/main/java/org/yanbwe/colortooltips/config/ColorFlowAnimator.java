package org.yanbwe.colortooltips.config;

import net.minecraft.Util;

/**
 * 颜色流动动画引擎。
 * 维护一个全局时间基准偏移值，供 TooltipRenderer 中各渲染方法根据 direction
 * 计算实际相位。替代原有的 scrollOffset 静态变量和 updateScrollOffset() 方法。
 * <p>
 * 方向逻辑（Clockwise/CounterClockwise/TopToDown 等）在 TooltipRenderer 中使用
 * {@link #getFlowOffset()} 时，根据样式配置的 direction 自行计算实际相位偏移。
 * 此引擎只负责时间驱动的 offset 累积。
 */
public class ColorFlowAnimator {

    /** 流动速度。0 = 不流动，1 = 正常速度，>1 = 加速。 */
    private double speed;

    /** 当前时间基准偏移值（归一化，0~1 为一个完整周期） */
    private float flowOffset;
    /** 上一帧的 delta 毫秒，用于帧间插值消除低流速时的卡顿感 */
    private long lastDelta;
    /** 上次 update() 调用的时间戳（毫秒） */
    private long lastUpdateTime;

    public ColorFlowAnimator() {
        this(0.0);
    }

    public ColorFlowAnimator(double initialSpeed) {
        this.speed = initialSpeed;
        this.flowOffset = 0f;
        this.lastUpdateTime = 0L;
    }

    /**
     * 每帧调用，基于时间 delta 推进 flowOffset（归一化值 0~1 为一个完整流动周期）。
     * 基准 2 秒/周期。各组件渲染时乘以各自的 colorFlowSpeed 得到实际速度。
     * 若 delta >= 100ms（如暂停/切窗），跳过该帧避免视觉跳变。
     */
    public void update() {
        long currentTime = Util.getMillis();
        if (lastUpdateTime != 0) {
            long delta = currentTime - lastUpdateTime;
            if (delta < 100 && delta > 0) {
                // speed=1 基准 → 2000ms 一个完整周期
                flowOffset += (float)(delta / 2000.0);
                lastDelta = delta;
            }
        }
        lastUpdateTime = currentTime;
    }

    /**
     * @return 帧间插值后的流动进度（归一化值），消除低流速时的卡顿感。
     * @param partialTicks 当前帧的部分刻度（Minecraft.getInstance().getFrameTime()）
     */
    public float getInterpolatedFlowFraction(float partialTicks) {
        if (lastDelta <= 0) return flowOffset;
        return flowOffset + (float)(partialTicks * lastDelta / 2000.0);
    }

    /**
     * @return 当前流动进度（归一化值，0~1 为一个完整周期），供渲染方法根据 direction 计算相位。
     *         渲染时需乘以实际路径总长度（P 或 barLen）转换为像素偏移。
     */
    public float getFlowFraction() {
        return flowOffset;
    }

    /**
     * @return 当前时间基准偏移值（已弃用，改用 {@link #getFlowFraction()}）
     * @deprecated 改用 {@link #getFlowFraction()}，内部值相同，
     *             但渲染方法需要自行乘以路径长度转换为像素偏移
     */
    @Deprecated
    public float getFlowOffset() {
        return flowOffset;
    }

    /**
     * 动态设置流动速度（从样式配置读取 colorFlowSpeed）。
     * @param speed 流动速度，0 = 不流动，1 = 正常速度，>1 = 加速
     */
    public void setSpeed(double speed) {
        this.speed = speed;
    }

    /**
     * @return 当前设置的流动速度
     */
    public double getSpeed() {
        return speed;
    }

    /**
     * @return 是否正在流动（speed > 0）
     */
    public boolean isFlowing() {
        return speed > 0.0;
    }

    /**
     * 重置偏移值为 0，但不重置时间基准。
     * 在切换物品时调用，使流动从起点重新开始，同时避免因时间基准重置造成的跳变。
     */
    public void reset() {
        this.flowOffset = 0f;
    }

    /**
     * 完全重置（含时间基准和速度），用于配置重载等需要完全重新初始化的场景。
     */
    public void fullReset() {
        this.flowOffset = 0f;
        this.lastUpdateTime = 0L;
        this.speed = 0.0;
    }
}
