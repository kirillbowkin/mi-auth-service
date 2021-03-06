package com.mathinjection.authservice.controller;

import com.mathinjection.authservice.dto.*;
import com.mathinjection.authservice.entity.RoleEntity;
import com.mathinjection.authservice.entity.UserEntity;
import com.mathinjection.authservice.model.UserModel;
import com.mathinjection.authservice.repository.RoleRepository;
import com.mathinjection.authservice.service.UserService;
import com.mathinjection.authservice.util.JwtUtil;
import com.mathinjection.authservice.util.validators.RegisterReqDtoValidator;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/auth/v1/")
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;
    private final RegisterReqDtoValidator registerReqDtoValidator;

    @PostMapping("register")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = RegisterResponseDto.class))}),
            @ApiResponse(responseCode = "400", description = "BAD_REQUEST", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDto.class))}),

    })
    public ResponseEntity<? extends BaseResponseDto> register(@RequestBody RegisterReqDto regReqDto) {

        List<Map<String, String>> errors = registerReqDtoValidator.validate(regReqDto);
        if (!errors.isEmpty()) {
            return ResponseEntity
                    .badRequest()
                    .body(
                            new ErrorResponseDto()
                                    .setErrors(errors)
                                    .setStatus(ResponseStatus.ERROR)
                                    .setPath("/api/auth/v1/register")
                                    .setTimestamp(LocalDateTime.now())
                    );
        }

        try {
            RoleEntity role = roleRepository.findRoleByName("ROLE_USER").orElse(null);
            UserEntity savedUser = userService.save(
                    new UserEntity()
                            .setUsername(regReqDto.getUsername())
                            .setPassword(passwordEncoder.encode(regReqDto.getPassword()))
                            .setEmail(regReqDto.getEmail())
                            .setRoles(Collections.singletonList(role))
            );

            UserModel userModel = UserModel.EntityToModel(savedUser);

            return ResponseEntity
                    .ok()
                    .body(
                            new RegisterResponseDto()
                                    .setUser(userModel)
                                    .setStatus(ResponseStatus.SUCCESS)
                                    .setPath("/api/auth/v1/register")
                                    .setTimestamp(LocalDateTime.now())
                    );
        } catch (Exception e) {
            return ResponseEntity
                    .badRequest()
                    .body(
                            //TODO: Implement ErrorService
                            new ErrorResponseDto()
                                    .setErrors(new ArrayList<>() {{
                                        add(new HashMap<>() {{
                                            put("error", "registration error");
                                            put("message", e.getMessage());
                                        }});
                                    }})
                                    .setStatus(ResponseStatus.ERROR)
                                    .setPath("/api/auth/v1/register")
                                    .setTimestamp(LocalDateTime.now())
                    );
        }

    }

    @PostMapping("authenticate")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = AuthResponseDto.class))}),
            @ApiResponse(responseCode = "400", description = "BAD_REQUEST", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDto.class))}),

    })
    public ResponseEntity<? extends BaseResponseDto> authenticate(@RequestBody AuthenticationReqDto authReqDto) throws Exception {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(authReqDto.getUsername(), authReqDto.getPassword()));
        } catch (BadCredentialsException e) {
            return ResponseEntity
                    .badRequest()
                    .body(
                            new ErrorResponseDto()
                                    .setErrors(new ArrayList<>() {{
                                        add(new HashMap<>() {{
                                            put("error", "bad credentials");
                                            put("message", "incorrect username or password");
                                        }});
                                    }})
                                    .setStatus(ResponseStatus.ERROR)
                                    .setPath("/api/auth/v1/register")
                                    .setTimestamp(LocalDateTime.now())
                    );
        }

        //TODO: Refactor
        final UserDetails userDetails = userDetailsService.loadUserByUsername(authReqDto.getUsername());
        final Map<String, String> tokens = jwtUtil.generateTokens(userDetails.getUsername(), userDetails.getAuthorities());

        return ResponseEntity
                .ok()
                .body(
                        new AuthResponseDto()
                                .setTokens(tokens)
                                .setPath("/api/auth/v1/authenticate")
                                .setTimestamp(LocalDateTime.now())
                                .setStatus(ResponseStatus.SUCCESS)
                );
    }

    @PostMapping("refresh")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = AuthResponseDto.class))}),
            @ApiResponse(responseCode = "400", description = "BAD_REQUEST", content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDto.class))}),

    })
    public ResponseEntity<? extends BaseResponseDto> refresh(@RequestBody RefreshReqDto refreshReqDto) throws Exception {
        String username = jwtUtil.getSubject(refreshReqDto.getRefreshToken());

        try {

            //TODO: Refactor
            final UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            final Map<String, String> tokens = jwtUtil.generateTokens(userDetails.getUsername(), userDetails.getAuthorities());

            return ResponseEntity
                    .ok()
                    .body(
                            new AuthResponseDto()
                                    .setTokens(tokens)
                                    .setPath("/api/auth/v1/authenticate")
                                    .setTimestamp(LocalDateTime.now())
                                    .setStatus(ResponseStatus.SUCCESS)
                    );

        } catch (Exception e) {
            return ResponseEntity
                    .badRequest()
                    .body(
                            //TODO: Implement ErrorService
                            new ErrorResponseDto()
                                    .setErrors(new ArrayList<>() {{
                                        add(new HashMap<>() {{
                                            put("error", "registration error");
                                            put("message", e.getMessage());
                                        }});
                                    }})
                                    .setStatus(ResponseStatus.ERROR)
                                    .setPath("/api/auth/v1/register")
                                    .setTimestamp(LocalDateTime.now())
                    );
        }
    }
}
