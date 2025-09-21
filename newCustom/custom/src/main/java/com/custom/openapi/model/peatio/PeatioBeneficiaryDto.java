package com.custom.openapi.model.peatio;

import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.Data;

@Data
public class PeatioBeneficiaryDto {
    private Long id;
    private String currency;
    private String name;
    private String state;
    private String description;
    @JsonRawValue
    private String data;
}