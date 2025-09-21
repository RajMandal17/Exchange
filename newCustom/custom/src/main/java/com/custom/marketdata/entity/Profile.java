package com.custom.marketdata.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class Profile {
    private String id;
    private String userId;
    private String firstName;
    private String lastName;
    private String dob;               // Date of birth (YYYY-MM-DD)
    private String address;
    private String postcode;
    private String city;
    private String country;
    private String state = "submitted"; // submitted, verified, rejected
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
