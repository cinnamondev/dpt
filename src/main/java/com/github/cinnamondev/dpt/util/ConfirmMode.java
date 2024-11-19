package com.github.cinnamondev.dpt.util;

public enum ConfirmMode {
    NEVER("never"),  // never send confirm
    MASS("mass"), // send user confirm to SENDER if they use /dptsend all/here <server>
    PERSONAL("personal"), // send user confirm if they use /dptsend <server>
    ALWAYS("always") // always send user confirm to individuals
    ;

    private final String modeString;
    private ConfirmMode(String modeString) {
        this.modeString = modeString;
    }

    @Override
    public String toString() {
        return modeString;
    }

    public static ConfirmMode fromString(String mode) {
        return switch (mode.toLowerCase()) {
            case "never"    -> NEVER;
            case "mass"     -> MASS;
            case "personal" -> PERSONAL;
            case "always"   -> ALWAYS;
            default        -> NEVER;
        };
    }
}
