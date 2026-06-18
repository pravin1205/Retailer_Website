package com.marketly.identity.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class OtpVerifyResponse {

    private boolean verified;
    private boolean isNewUser;
    private String  accessToken;
    private String  refreshToken;
    private String  tokenType;
    private long    expiresIn;
    private UserInfo user;

    @Data
    @Builder
    public static class UserInfo {
        private UUID        id;
        private String      phone;
        private List<String> roles;
        private String      tenantSlug;
    }
}
