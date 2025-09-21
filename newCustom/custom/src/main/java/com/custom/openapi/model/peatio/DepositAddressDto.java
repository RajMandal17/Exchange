package com.custom.openapi.model.peatio;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DepositAddressDto {
    private String currency;
    private String address;
    private String state;
}
