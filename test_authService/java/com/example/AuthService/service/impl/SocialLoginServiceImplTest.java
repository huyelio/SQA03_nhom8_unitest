package com.example.AuthService.service.impl;

import com.example.AuthService.entity.Role;
import com.example.AuthService.entity.User;
import com.example.AuthService.repository.RoleRepository;
import com.example.AuthService.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit test {@link SocialLoginServiceImpl}. CheckDB: captor {@link UserRepository#save(User)}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SocialLoginServiceImpl")
class SocialLoginServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private OAuth2User oauth2User;

    @InjectMocks
    private SocialLoginServiceImpl socialLoginService;

    private Map<String, Object> googleAttrs(String sub, String email) {
        Map<String, Object> m = new HashMap<>();
        m.put("sub", sub);
        m.put("email", email);
        m.put("name", "N");
        m.put("picture", "pic");
        return m;
    }

    /** Test Case ID: TC-SOCIAL-01 */
    @Test
    @DisplayName("throws when Google sub missing")
    void throwsWhenSubMissing() {
        when(oauth2User.getAttribute("sub")).thenReturn("");
        when(oauth2User.getAttribute("email")).thenReturn("a@a.com");
        assertThrows(IllegalStateException.class, () -> socialLoginService.upsertGoogleUser(oauth2User));
    }

    /** Test Case ID: TC-SOCIAL-02 */
    @Test
    @DisplayName("updates existing user found by google id")
    void updatesExistingUserByGoogleId() {
        when(oauth2User.getAttribute("sub")).thenReturn("sub-1");
        when(oauth2User.getAttribute("email")).thenReturn("  E@E.COM  ");
        when(oauth2User.getAttribute("name")).thenReturn("Name");
        when(oauth2User.getAttribute("picture")).thenReturn("url");
        User existing = new User();
        existing.setEmail("old@e.com");
        when(userRepository.findByGoogleAccountId("sub-1")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User out = socialLoginService.upsertGoogleUser(oauth2User);

        assertEquals("sub-1", out.getGoogleAccountId());
        assertEquals("e@e.com", out.getEmail());
        assertTrue(out.isEnabled());
        verify(userRepository).save(existing);
    }

    /** Test Case ID: TC-SOCIAL-03 */
    @Test
    @DisplayName("creates new user when not found by sub or email")
    void createsNewUserWhenNotFound() {
        when(oauth2User.getAttribute("sub")).thenReturn("new-sub");
        when(oauth2User.getAttribute("email")).thenReturn("new@n.com");
        when(oauth2User.getAttribute("name")).thenReturn("New");
        when(oauth2User.getAttribute("picture")).thenReturn("p");
        when(userRepository.findByGoogleAccountId("new-sub")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("new@n.com")).thenReturn(Optional.empty());
        Role role = Role.builder().id(1L).name("USER").build();
        when(roleRepository.findByName("USER")).thenReturn(Optional.of(role));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User out = socialLoginService.upsertGoogleUser(oauth2User);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("new-sub", captor.getValue().getGoogleAccountId());
        assertEquals(role, captor.getValue().getRole());
        assertNotNull(out);
    }

    /** Test Case ID: TC-SOCIAL-04 */
    @Test
    @DisplayName("throws when default USER role missing for new account")
    void throwsWhenUserRoleMissing() {
        when(oauth2User.getAttribute("sub")).thenReturn("s");
        when(oauth2User.getAttribute("email")).thenReturn("x@x.com");
        when(oauth2User.getAttribute("name")).thenReturn("X");
        when(oauth2User.getAttribute("picture")).thenReturn(null);
        when(userRepository.findByGoogleAccountId("s")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("x@x.com")).thenReturn(Optional.empty());
        when(roleRepository.findByName("USER")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> socialLoginService.upsertGoogleUser(oauth2User));
    }
}
