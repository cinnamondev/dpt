package com.github.cinnamondev.dpt.client;

public enum PowerState {
    OFFLINE,
    STARTING,
    RUNNING,
    STOPPING;

    public static PowerState fromString(String state) {
        for (PowerState p : PowerState.values()) {
            if (p.name().equalsIgnoreCase(state)) {
                return p;
            }
        }
        return OFFLINE;
    }
}
