package com.custom.openapi.model;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

@Getter
@Setter
public class PlaceOrderRequest {

    private String clientOid;

    @NotBlank
    private String productId;

    @NotBlank
    private String size;

    private String funds;

    private String price;

    @NotBlank
    private String side;

    @NotBlank
    private String type;
    /**
     * [optional] GTC, GTT, IOC, or FOK (default is GTC)
     */
    private String timeInForce;

    private Boolean isBot;

    @Pattern(regexp = "^(NORMAL|API)$", message = " NORMAL or API")
    private String from;
}
