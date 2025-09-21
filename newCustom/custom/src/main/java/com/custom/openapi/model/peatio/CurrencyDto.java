package com.custom.openapi.model.peatio;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CurrencyDto {
    private String id;
    private String name;
    private String description;
    private String homepage;
    private BigDecimal price;
    private String explorer_transaction;
    private String explorer_address;
    private String type;
    private Integer deposit_fee;
    private Integer min_deposit_amount;
    private Integer withdraw_fee;
    private Integer min_withdraw_amount;
    private Integer withdraw_limit_24h;
    private Integer withdraw_limit_72h;
    private String base_factor;
    private Integer precision;
    private String icon_url;
    private Integer min_confirmations;
    private String code;
    private String blockchain_key;
    private Boolean deposit_enabled;
    private Boolean withdrawal_enabled;
    private Boolean visible;
    private Integer position;
    private Boolean subunits;
    private String options;
}
