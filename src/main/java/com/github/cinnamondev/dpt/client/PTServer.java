package com.github.cinnamondev.dpt.client;

import org.slf4j.Logger;

public class PTServer {
    private PTClient client;
    protected Logger getLogger() { return client.getLogger(); }
    protected final String uuid;
    protected final String identifier;
    public PTServer(PTClient client, String uuid) {
        this.client = client;
        this.uuid = uuid;
        this.identifier = uuid.substring(0, 8);
    }

    public String getUUID() { return this.uuid; }
    public PowerState getPowerState() {
        return client.state(uuid);
    }

    public boolean online() {
        return getPowerState().equals(PowerState.RUNNING);
    };

    public boolean power(PowerAction powerAction) {
        return power(powerAction);
    }

}