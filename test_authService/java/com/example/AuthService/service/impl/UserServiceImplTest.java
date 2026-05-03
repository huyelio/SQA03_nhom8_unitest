package com.example.AuthService.service.impl;

import com.example.AuthService.dto.request.UserUpdateRequestDTO;
import com.example.AuthService.dto.response.UserProfileResponse;
import com.example.AuthService.dto.response.UserResponseDTO;
import com.example.AuthService.entity.Country;
import com.example.AuthService.entity.Role;
import com.example.AuthService.entity.User;
import com.example.AuthService.repository.CountryRepository;
import com.example.AuthService.repository.RoleRepository;
import com.example.AuthService.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test {@link UserServiceImpl}. CheckDB: verify {@link UserRepository#save} / captor mật khẩu (mock).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl")
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private CountryRepository countryRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    private User sampleUser() {
        Role role = Role.builder().id(1L).name("USER").build();
        return User.builder()
                .id(5L)
                .email("a@a.com")
                .name("A")
                .password("hash")
                .role(role)
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();
    }

    /** Test Case ID: TC-USER-PROFILE-01 */
    @Test
    @DisplayName("getUserProfileByEmail maps fields")
    void getUserProfileByEmailMapsFields() {
        when(userRepository.findByEmail("a@a.com")).thenReturn(Optional.of(sampleUser()));
        UserProfileResponse profile = userService.getUserProfileByEmail("a@a.com");
        assertEquals("a@a.com", profile.getEmail());
        assertEquals("USER", profile.getRoleName());
    }

    /** Test Case ID: TC-USER-LIST-01 */
    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("getAllUsers delegates to repository with specification")
    void getAllUsersDelegatesToRepository() {
        Page<User> page = new PageImpl<>(List.of(sampleUser()));
        when(userRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        List<UserResponseDTO> list = userService.getAllUsers(0, 10, null, null, null);

        assertEquals(1, list.size());
        assertEquals(5L, list.get(0).getId());
    }

    /** Test Case ID: TC-USER-UPDATE-01 */
    @Test
    @DisplayName("updateUser encodes password when provided")
    void updateUserEncodesPasswordWhenProvided() {
        User user = sampleUser();
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newpass")).thenReturn("ENC(newpass)");
        UserUpdateRequestDTO dto = new UserUpdateRequestDTO();
        dto.setPassword("newpass");

        userService.updateUser(5L, dto);

        verify(userRepository).save(user);
        assertEquals("ENC(newpass)", user.getPassword());
    }

    /** Test Case ID: TC-USER-CREATE-01 */
    @Test
    @DisplayName("createUser throws when email null")
    void createUserThrowsWhenEmailNull() {
        UserUpdateRequestDTO dto = new UserUpdateRequestDTO();
        dto.setPassword("p");
        assertThrows(RuntimeException.class, () -> userService.createUser(dto));
    }

    /** Test Case ID: TC-USER-MYPROFILE-01 */
    @Test
    @DisplayName("updateMyProfile updates fields and optional password")
    void updateMyProfileUpdatesFields() {
        User user = sampleUser();
        when(userRepository.findByEmail("a@a.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("x")).thenReturn("ENC(x)");
        UserUpdateRequestDTO dto = new UserUpdateRequestDTO();
        dto.setName("New");
        dto.setPassword("x");

        UserProfileResponse res = userService.updateMyProfile("a@a.com", dto);

        assertEquals("New", res.getName());
        verify(userRepository).save(user);
    }
}
