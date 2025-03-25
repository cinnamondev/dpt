package com.github.cinnamondev.dpt.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PTObject {
    @JsonProperty("object")
    public String object;

    @JsonProperty("attributes")
    public Resources attributes;
}
