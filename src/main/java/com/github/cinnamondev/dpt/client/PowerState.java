package com.github.cinnamondev.dpt.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum PowerState {
    @JsonProperty("offline") OFFLINE,
    @JsonProperty("starting") STARTING,
    @JsonProperty("running") RUNNING,
    @JsonProperty("stopping") STOPPING;

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

