package com.chpc.backend.controller;

import com.chpc.backend.dto.UserRequest;
import com.chpc.backend.dto.UserResponse;
import com.chpc.backend.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController controller;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void getAllReturnsUsers() throws Exception {
        UserResponse user = UserResponse.builder()
                .id(1L)
                .username("admin")
                .fullName("Admin Demo")
                .roles(Set.of("ADMIN"))
                .build();

        when(userService.getAll()).thenReturn(List.of(user));

        mockMvc.perform(get("/api/users").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].username").value("admin"));
    }

    @Test
    void createReturnsCreatedUser() throws Exception {
        UserRequest request = new UserRequest();
        request.setUsername("admin");
        request.setFullName("Admin Demo");
        request.setEmail("admin@test.com");
        request.setPassword("password123");
        request.setRoles(Set.of("ADMIN"));

        UserResponse response = UserResponse.builder()
                .id(2L)
                .username("admin")
                .fullName("Admin Demo")
                .email("admin@test.com")
                .roles(Set.of("ADMIN"))
                .build();

        when(userService.create(any(UserRequest.class), eq("127.0.0.1"))).thenReturn(response);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(req -> {
                            req.setRemoteAddr("127.0.0.1");
                            return req;
                        }))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.email").value("admin@test.com"));
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        doNothing().when(userService).delete(5L, "127.0.0.1");

        mockMvc.perform(delete("/api/users/5").with(req -> {
                    req.setRemoteAddr("127.0.0.1");
                    return req;
                }))
                .andExpect(status().isNoContent());
    }
}
