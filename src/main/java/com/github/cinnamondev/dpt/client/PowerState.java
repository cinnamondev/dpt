package com.github.cinnamondev.dpt.client;

public enum PowerState {
    OFFLINE("offline"),
    STARTING("starting"),
    RUNNING("running"),
    STOPPING("stopping");

    private String str;
    PowerState(String str) {
        this.str = str;
    }

    @Override
    public String toString() {
        return this.str;
    }

    public static PowerState fromString(String str) {
        return switch (str.toLowerCase()) {
            case "offline"  -> OFFLINE;
            case "starting" -> STARTING;
            case "running"  -> RUNNING;
            case "stopping" -> STOPPING;
            default         -> OFFLINE;
        };
    }
}

