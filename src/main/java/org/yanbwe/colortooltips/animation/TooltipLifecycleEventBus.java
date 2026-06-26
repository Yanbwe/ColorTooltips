package org.yanbwe.colortooltips.animation;

import java.util.ArrayDeque;
import java.util.Queue;

public final class TooltipLifecycleEventBus {
    private static final Queue<TooltipLifecycleEventType> EVENTS = new ArrayDeque<>();

    private TooltipLifecycleEventBus() {
    }

    public static void publish(TooltipLifecycleEventType eventType) {
        EVENTS.offer(eventType);
    }

    public static TooltipLifecycleEventType poll() {
        return EVENTS.poll();
    }

    public static void clear() {
        EVENTS.clear();
    }
}
