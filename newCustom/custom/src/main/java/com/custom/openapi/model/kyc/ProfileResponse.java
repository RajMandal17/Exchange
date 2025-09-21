package com.custom.openapi.model.kyc;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfileResponse {
    private String id;
    private String firstName;
    private String lastName;
    private String dob;
    private String address;
    private String postcode;
    private String city;
    private String country;
    private String state;         // submitted, verified, rejected
    private String createdAt;
    private String updatedAt;
}
