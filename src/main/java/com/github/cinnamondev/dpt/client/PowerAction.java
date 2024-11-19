package com.github.cinnamondev.dpt.client;

public enum PowerAction {
    START("start"),
    STOP("stop"),
    RESTART("restart"),
    KILL("kill")
    ;

    private String str;
    PowerAction(String str) {
        this.str = str;
    }

    @Override
    public String toString() {
        return this.str;
    }
}
