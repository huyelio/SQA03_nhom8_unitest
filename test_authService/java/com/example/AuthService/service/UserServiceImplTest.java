package com.example.AuthService.service;

import com.example.AuthService.dto.request.UserUpdateRequestDTO;
import com.example.AuthService.dto.response.UserProfileResponse;
import com.example.AuthService.dto.response.UserResponseDTO;
import com.example.AuthService.entity.Role;
import com.example.AuthService.entity.User;
import com.example.AuthService.repository.CountryRepository;
import com.example.AuthService.repository.RoleRepository;
import com.example.AuthService.repository.UserRepository;
import com.example.AuthService.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Unit tests cho UserServiceImpl.
 * Sử dụng Mockito (không cần Spring context).
 *
 * Test cases:
 *   TC-USER-01: getUserProfileByEmail – user tồn tại
 *   TC-USER-02: getUserProfileByEmail – user không tồn tại → exception
 *   TC-USER-03: updateUser – đổi email trùng → exception
 *   TC-USER-04: createUser – thành công → CheckDB save được gọi
 *   TC-USER-05: deleteUser – user tồn tại → CheckDB delete được gọi
 *   TC-USER-06: updateMyProfile – cập nhật tên và giới tính → CheckDB save được gọi
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    // ==================== MOCKS ====================

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private CountryRepository countryRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    // ==================== TEST DATA ====================

    private User mockUser;
    private Role roleUser;

    @BeforeEach
    void setUp() {
        roleUser = Role.builder().id(1L).name("USER").build();

        mockUser = User.builder()
                .id(1L)
                .email("user@test.com")
                .name("Nguyen Van A")
                .gender("male")
                .phoneNumber("0909000001")
                .password("encoded_pass")
                .role(roleUser)
                .enabled(true)
                .accountNonLocked(true)
                .accountNonExpired(true)
                .credentialsNonExpired(true)
                .build();
    }

    // ==================== TEST METHODS ====================

    /**
     * TC-USER-01: Lấy profile user tồn tại → trả UserProfileResponse đúng dữ liệu.
     * Input    : email = "user@test.com" tồn tại trong DB
     * Expected : UserProfileResponse với email, name, gender đúng
     * CheckDB  : userRepository.findByEmail được gọi đúng 1 lần (truy vấn DB)
     */
    @Test
    @DisplayName("TC-USER-01: getUserProfileByEmail trả đúng dữ liệu")
    void getUserProfileByEmail_withExistingUser_returnsProfile() {
        // Arrange
        given(userRepository.findByEmail("user@test.com"))
                .willReturn(Optional.of(mockUser));

        // Act
        UserProfileResponse response = userService.getUserProfileByEmail("user@test.com");

        // Assert
        assertThat(response.getEmail()).isEqualTo("user@test.com");
        assertThat(response.getName()).isEqualTo("Nguyen Van A");
        assertThat(response.getGender()).isEqualTo("male");
        assertThat(response.getRoleName()).isEqualTo("USER");

        // CheckDB: truy vấn DB đúng 1 lần
        verify(userRepository).findByEmail("user@test.com");
    }

    /**
     * TC-USER-02: User không tồn tại → ném RuntimeException.
     * Input    : email không có trong DB
     * Expected : RuntimeException với message "User not found"
     */
    @Test
    @DisplayName("TC-USER-02: getUserProfileByEmail user không tồn tại ném exception")
    void getUserProfileByEmail_withNonExistingUser_throwsException() {
        // Arrange
        given(userRepository.findByEmail("ghost@test.com"))
                .willReturn(Optional.empty());

        // Act + Assert
        assertThatThrownBy(() ->
                userService.getUserProfileByEmail("ghost@test.com"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User not found");
    }

    /**
     * TC-USER-03: Cập nhật user với email đã tồn tại → ném RuntimeException.
     * Input    : id hợp lệ, email trùng với user khác
     * Expected : RuntimeException "Email already exists"
     * CheckDB  : userRepository.save KHÔNG được gọi
     */
    @Test
    @DisplayName("TC-USER-03: updateUser đổi email trùng ném exception")
    void updateUser_withDuplicateEmail_throwsException() {
        // Arrange
        given(userRepository.findById(1L))
                .willReturn(Optional.of(mockUser));
        given(userRepository.existsByEmail("other@test.com"))
                .willReturn(true);

        UserUpdateRequestDTO request = new UserUpdateRequestDTO();
        request.setEmail("other@test.com");

        // Act + Assert
        assertThatThrownBy(() -> userService.updateUser(1L, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Email already exists");

        // CheckDB: không lưu vì email trùng
        verify(userRepository, org.mockito.Mockito.never()).save(any());
    }

    /**
     * TC-USER-04: Tạo user mới thành công → CheckDB: save được gọi với dữ liệu đúng.
     * Input    : email mới, password, roleId hợp lệ
     * Expected : UserResponseDTO được trả về; password đã encode
     * CheckDB  : userRepository.save được gọi với user có password hash
     * Rollback : Dùng mock nên không cần rollback thực tế
     */
    @Test
    @DisplayName("TC-USER-04: createUser thành công lưu user mới vào DB")
    void createUser_withValidData_savesAndReturnsUser() {
        // Arrange
        given(userRepository.existsByEmail("brand_new@test.com")).willReturn(false);
        given(roleRepository.findById(1L)).willReturn(Optional.of(roleUser));
        given(passwordEncoder.encode("StrongPass123"))
                .willReturn("hashed_StrongPass123");

        User savedUser = User.builder()
                .id(99L).email("brand_new@test.com")
                .name("Brand New").password("hashed_StrongPass123")
                .role(roleUser).enabled(true)
                .accountNonLocked(true).accountNonExpired(true)
                .credentialsNonExpired(true)
                .build();
        given(userRepository.save(any(User.class))).willReturn(savedUser);

        UserUpdateRequestDTO request = new UserUpdateRequestDTO();
        request.setEmail("brand_new@test.com");
        request.setPassword("StrongPass123");
        request.setName("Brand New");
        request.setRoleId(1L);

        // Act
        UserResponseDTO result = userService.createUser(request);

        // Assert
        assertThat(result.getEmail()).isEqualTo("brand_new@test.com");

        // CheckDB: save được gọi với password đã được encode
        verify(userRepository).save(argThat(user ->
                "hashed_StrongPass123".equals(user.getPassword())
                && "brand_new@test.com".equals(user.getEmail())
        ));
    }

    /**
     * TC-USER-05: Xóa user tồn tại → CheckDB: delete được gọi đúng.
     * Input    : id hợp lệ của user tồn tại
     * Expected : userRepository.delete() được gọi đúng 1 lần
     * CheckDB  : Xác minh delete được gọi với đúng user object
     * Rollback : Dùng mock nên không cần rollback thực tế
     */
    @Test
    @DisplayName("TC-USER-05: deleteUser xóa user từ DB")
    void deleteUser_withExistingId_callsDelete() {
        // Arrange
        given(userRepository.findById(1L)).willReturn(Optional.of(mockUser));

        // Act
        userService.deleteUser(1L);

        // Assert CheckDB: delete được gọi với đúng user
        verify(userRepository).delete(argThat((User user) -> user.getId().equals(1L)));
    }

    /**
     * TC-USER-06: Cập nhật profile của chính mình → CheckDB: save với dữ liệu mới.
     * Input    : email hợp lệ, name mới = "Nguyen Thi B", gender mới = "female"
     * Expected : UserProfileResponse với dữ liệu đã cập nhật
     * CheckDB  : userRepository.save gọi với user có name và gender mới
     */
    @Test
    @DisplayName("TC-USER-06: updateMyProfile cập nhật tên và giới tính")
    void updateMyProfile_withNewNameAndGender_updatesAndSaves() {
        // Arrange
        given(userRepository.findByEmail("user@test.com"))
                .willReturn(Optional.of(mockUser));
        given(userRepository.save(any(User.class))).willReturn(mockUser);

        UserUpdateRequestDTO request = new UserUpdateRequestDTO();
        request.setName("Nguyen Thi B");
        request.setGender("female");

        // Act
        UserProfileResponse response = userService.updateMyProfile("user@test.com", request);

        // Assert
        assertThat(response).isNotNull();

        // CheckDB: save gọi với user có name và gender mới
        verify(userRepository).save(argThat(user ->
                "Nguyen Thi B".equals(user.getName())
                && "female".equals(user.getGender())
        ));
    }

    // ─────────────────── Bổ sung test case (edge cases) ────────────────────

    /**
     * TC-USER-07
     * Mục đích : getUserProfileByEmail — email rỗng ("") → ném exception
     * Input     : email = "" (empty string)
     * Expected  : Ném RuntimeException (UserNotFoundException hoặc tương đương)
     * CheckDB   : Yes — verify userRepository.findByEmail gọi với ""
     */
    @Test
    @DisplayName("TC-USER-07: getUserProfileByEmail email rỗng → ném exception")
    void getUserProfileByEmail_emptyEmail_throwsException() {
        // Arrange
        given(userRepository.findByEmail("")).willReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.getUserProfileByEmail(""))
                .isInstanceOf(RuntimeException.class);

        // CheckDB: verify findByEmail gọi với string rỗng
        verify(userRepository).findByEmail("");
    }

    /**
     * TC-USER-08
     * Mục đích : createUser — email đã tồn tại → ném exception (conflict)
     * Input     : email = "existing@test.com"; userRepo.existsByEmail → true
     * Expected  : Ném RuntimeException hoặc ResponseStatusException
     * CheckDB   : Yes — verify existsByEmail
     */
    @Test
    @DisplayName("TC-USER-08: createUser email đã tồn tại → ném exception conflict")
    void createUser_duplicateEmail_throwsException() {
        // Arrange: email đã tồn tại
        given(userRepository.existsByEmail("existing@test.com")).willReturn(true);

        UserUpdateRequestDTO req = new UserUpdateRequestDTO();
        req.setEmail("existing@test.com");
        req.setName("Dup User");
        req.setPassword("Pass1234!"); // createUser requires non-null password

        // Act & Assert
        assertThatThrownBy(() -> userService.createUser(req))
                .isInstanceOf(RuntimeException.class);

        verify(userRepository).existsByEmail("existing@test.com");
    }
}
