package com.custom.openapi.model.kyc;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfileSubmissionRequest {
    private String firstName;
    private String lastName;
    private String dob;           // Date of birth (YYYY-MM-DD)
    private String address;
    private String postcode;
    private String city;
    private String country;
}
