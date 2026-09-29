package com.prathyusha.bankingapi.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthToken {

    @JsonProperty("token")
    private String token;

    @JsonProperty("expiresInSeconds")
    private long expiresInSeconds;

    public String getToken() {
        return token;
    }

    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }
}
