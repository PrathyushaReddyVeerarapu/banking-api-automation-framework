package com.prathyusha.ecommerceapi.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderRequest {

    @JsonProperty("productId")
    private String productId;

    @JsonProperty("quantity")
    private Integer quantity;

    public OrderRequest() {
    }

    public OrderRequest(String productId, Integer quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

    public String getProductId() {
        return productId;
    }

    public Integer getQuantity() {
        return quantity;
    }
}
