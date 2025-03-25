package com.github.cinnamondev.dpt.client;


import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class Resources {
    @JsonProperty("current_state")
    public PowerState currentState;
    public PowerState getPowerState() { return currentState; }

    @JsonProperty("is_suspended")
    private boolean suspended;
    public boolean isSuspended() { return suspended; }

    @JsonProperty("resources")
    private void flattenResources(Map<String, String> resources) {
        memory = Long.parseLong(resources.get("memory_bytes"));
        cpu = Float.parseFloat(resources.get("cpu_absolute"));
        disk = Long.parseLong(resources.get("disk_bytes"));
        networkRx = Long.parseLong(resources.get("network_rx_bytes"));
        networkTx = Long.parseLong(resources.get("network_tx_bytes"));

    }
    private long memory;
    public long memory() { return memory; }
    private float cpu;
    public float cpu() { return cpu; }
    private long disk;
    public long disk() { return disk; }
    private long networkRx;
    public long networkRx() { return networkRx; }
    private long networkTx;
    public long networkTx() { return networkTx; }
}


