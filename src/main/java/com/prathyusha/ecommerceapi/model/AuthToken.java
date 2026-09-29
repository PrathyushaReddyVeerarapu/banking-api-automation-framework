package com.prathyusha.ecommerceapi.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthToken {

    @JsonProperty("token")
    private String token;

    @JsonProperty("expiresInSeconds")
    private Integer expiresInSeconds;

    public String getToken() {
        return token;
    }

    public Integer getExpiresInSeconds() {
        return expiresInSeconds;
    }
}
