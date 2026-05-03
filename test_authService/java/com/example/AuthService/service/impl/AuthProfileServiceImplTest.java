package com.example.AuthService.service.impl;

import com.example.AuthService.dto.AuthProfileDto;
import com.example.AuthService.entity.Role;
import com.example.AuthService.entity.User;
import com.example.AuthService.security.OAuth2UserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test {@link AuthProfileServiceImpl} — không truy cập DB.
 */
@DisplayName("AuthProfileServiceImpl")
class AuthProfileServiceImplTest {

    private final AuthProfileServiceImpl authProfileService = new AuthProfileServiceImpl();

    /** Test Case ID: TC-AUTHPROFILE-01 */
    @Test
    @DisplayName("null principal yields unauthenticated profile")
    void nullPrincipalYieldsUnauthenticated() {
        AuthProfileDto dto = authProfileService.buildProfile(null, true);
        assertFalse(dto.isAuthenticated());
    }

    /** Test Case ID: TC-AUTHPROFILE-02 */
    @Test
    @DisplayName("OAuth2UserPrincipal maps Google fields")
    void oauth2UserPrincipalMapsGoogleFields() {
        User entity = User.builder()
                .id(1L)
                .email("u@u.com")
                .password("x")
                .name("U")
                .role(Role.builder().name("USER").build())
                .build();
        Map<String, Object> attrs = Map.of("email", "g@g.com", "name", "G", "picture", "p", "sub", "s");
        OAuth2UserPrincipal principal = new OAuth2UserPrincipal(entity, attrs);

        AuthProfileDto dto = authProfileService.buildProfile(principal, true);

        assertTrue(dto.isAuthenticated());
        assertEquals("google", dto.getProvider());
        assertEquals("g@g.com", dto.getEmail());
        assertNotNull(dto.getAuthorities());
    }

    /** Test Case ID: TC-AUTHPROFILE-03 */
    @Test
    @DisplayName("UserDetails yields local jwt profile")
    void userDetailsYieldsLocalJwtProfile() {
        UserDetails local = org.springframework.security.core.userdetails.User
                .withUsername("local@x.com")
                .password("pw")
                .roles("USER")
                .build();

        AuthProfileDto dto = authProfileService.buildProfile(local, false);

        assertTrue(dto.isAuthenticated());
        assertEquals("local/jwt", dto.getProvider());
        assertNull(dto.getAuthorities());
    }

    /** Test Case ID: TC-AUTHPROFILE-04 */
    @Test
    @DisplayName("generic OAuth2User maps attributes")
    void genericOAuth2UserMapsAttributes() {
        DefaultOAuth2User oauth2User = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("email", "o@o.com", "name", "O", "sub", "sub2", "picture", "pic2"),
                "email");

        AuthProfileDto dto = authProfileService.buildProfile(oauth2User, true);

        assertEquals("o@o.com", dto.getEmail());
        assertEquals("google", dto.getProvider());
    }
}
