package com.example.AuthService.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test {@link CloudinaryServiceImpl}. CheckDB: mock Cloudinary API — không gọi mạng thật.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CloudinaryServiceImpl")
class CloudinaryServiceImplTest {

    @Mock
    private Cloudinary cloudinary;
    @Mock
    private Uploader uploader;

    @InjectMocks
    private CloudinaryServiceImpl cloudinaryService;

    /** Test Case ID: TC-CLOUD-UPLOAD-01 */
    @Test
    @DisplayName("uploadImage returns secure_url from Cloudinary response")
    void uploadImageReturnsSecureUrl() throws Exception {
        MultipartFile multipartFile = mock(MultipartFile.class);
        when(multipartFile.getBytes()).thenReturn(new byte[]{1, 2});
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of("secure_url", "https://res.cloudinary.com/x"));

        String secureUrl = cloudinaryService.uploadImage(multipartFile);

        assertEquals("https://res.cloudinary.com/x", secureUrl);
        verify(uploader, times(1)).upload(any(byte[].class), argThat(m ->
                "drugs".equals(m.get("folder")) && "image".equals(m.get("resource_type"))));
    }

    /** Test Case ID: TC-CLOUD-UPLOAD-02 */
    @Test
    @DisplayName("uploadImage wraps IOException as RuntimeException")
    void uploadImageWrapsIOException() throws Exception {
        MultipartFile multipartFile = mock(MultipartFile.class);
        when(multipartFile.getBytes()).thenThrow(new IOException("disk"));
        when(cloudinary.uploader()).thenReturn(uploader);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> cloudinaryService.uploadImage(multipartFile));
        assertEquals("Upload image failed", ex.getMessage());
        assertInstanceOf(IOException.class, ex.getCause());
    }
}
