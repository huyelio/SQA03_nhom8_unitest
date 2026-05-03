package com.example.AuthService.service.impl;

import com.example.AuthService.dto.response.AuthTokenResponse;
import com.example.AuthService.security.jwt.JwtProperties;
import com.example.AuthService.security.jwt.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.User;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit test {@link OAuth2TokenServiceImpl}. JWT/claims mock — không lưu session DB.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OAuth2TokenServiceImpl")
class OAuth2TokenServiceImplTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private JwtProperties jwtProperties;

    @InjectMocks
    private OAuth2TokenServiceImpl oauth2TokenService;

    /** Test Case ID: TC-OAUTH2-TOKEN-01 */
    @Test
    @DisplayName("buildTokenResponse fills tokens and OAuth attributes")
    void buildTokenResponseFillsTokensAndAttributes() {
        when(jwtProperties.getAccessExpirationMs()).thenReturn(3600L);
        UserDetails principal = User.withUsername("db-user")
                .password("p")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();
        when(jwtService.generateAccessToken(principal)).thenReturn("access");
        when(jwtService.generateRefreshToken("db-user")).thenReturn("refresh");

        Map<String, Object> googleAttrs = Map.of(
                "email", "g@gmail.com",
                "name", "G User",
                "picture", "pic",
                "sub", "google-sub-1"
        );

        AuthTokenResponse response = oauth2TokenService.buildTokenResponse(principal, googleAttrs);

        assertEquals("Bearer", response.getTokenType());
        assertEquals("access", response.getAccessToken());
        assertEquals("refresh", response.getRefreshToken());
        assertEquals(3600L, response.getExpiresIn());
        assertEquals("google", response.getProvider());
        assertEquals("g@gmail.com", response.getEmail());
        assertEquals("G User", response.getName());
        assertEquals("pic", response.getPicture());
        assertEquals("google-sub-1", response.getSub());
        assertEquals(List.of("ROLE_USER"), response.getAuthorities());
    }

    /** Test Case ID: TC-OAUTH2-TOKEN-02 */
    @Test
    @DisplayName("buildTokenResponse uses empty map when attributes null and null authorities")
    void buildTokenResponseHandlesNullAttributesAndNullAuthorities() {
        when(jwtProperties.getAccessExpirationMs()).thenReturn(100L);
        UserDetails principal = mock(UserDetails.class);
        when(principal.getUsername()).thenReturn("only-user");
        when(principal.getAuthorities()).thenReturn(null);
        when(jwtService.generateAccessToken(principal)).thenReturn("a");
        when(jwtService.generateRefreshToken("only-user")).thenReturn("r");

        AuthTokenResponse response = oauth2TokenService.buildTokenResponse(principal, null);

        assertEquals("only-user", response.getEmail());
        assertTrue(response.getAuthorities().isEmpty());
    }
}
