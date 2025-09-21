package com.custom.openapi.model.barong;

import com.custom.marketdata.entity.Label;
import com.custom.marketdata.entity.Phone;
import com.custom.marketdata.entity.Profile;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AuthenticationResponse {
    private String email;
    private String uid;
    private String role;
    private Integer level;
    private Boolean otp;
    private String state;
    private String referralUid;
    private String data;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String csrfToken;
    private List<Label> labels;
    private List<Phone> phones;
    private List<Profile> profiles;
}
