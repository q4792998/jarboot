package com.mz.jarboot.event;

import org.springframework.context.ApplicationContext;

/**
 * @author majianzheng
 */
public class ApplicationContextUtils {
    private static ApplicationContext ctx;
    private ApplicationContextUtils() {}

    public static void publish(Object event) {
        if (null != ctx) {
            ctx.publishEvent(event);
        }
    }

    public static String getEnv(String name) {
        return ctx.getEnvironment().getProperty(name);
    }

    public static String getEnv(String name, String defaultValue) {
        return ctx.getEnvironment().getProperty(name, defaultValue);
    }

    public static ApplicationContext getContext() {
        return ctx;
    }

    public static void setContext(final ApplicationContext ctx) {
        ApplicationContextUtils.ctx = ctx;
    }
}
