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
        memory = Integer.parseInt(resources.get("memory_bytes"));
        cpu = Integer.parseInt(resources.get("cpu_absolute"));
        disk = Integer.parseInt(resources.get("disk_bytes"));
        networkRx = Integer.parseInt(resources.get("network_rx_bytes"));
        networkTx = Integer.parseInt(resources.get("network_tx_bytes"));

    }
    private int memory;
    public int memory() { return memory; }
    private int cpu;
    public int cpu() { return cpu; }
    private int disk;
    public int disk() { return disk; }
    private int networkRx;
    public int networkRx() { return networkRx; }
    private int networkTx;
    public int networkTx() { return networkTx; }
}


