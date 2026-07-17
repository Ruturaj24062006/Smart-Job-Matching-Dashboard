package com.careermatch.backend.auth.service;

import com.careermatch.backend.auth.dto.*;
import com.careermatch.backend.auth.entity.User;
import com.careermatch.backend.auth.entity.UserRole;
import com.careermatch.backend.auth.repository.UserRepository;
import com.careermatch.backend.company.entity.Company;
import com.careermatch.backend.company.repository.CompanyRepository;
import com.careermatch.backend.recruiter.entity.Recruiter;
import com.careermatch.backend.recruiter.repository.RecruiterRepository;
import com.careermatch.backend.security.JwtTokenProvider;
import com.careermatch.backend.student.entity.Student;
import com.careermatch.backend.student.repository.StudentRepository;
import com.careermatch.backend.exception.BadRequestException;
import com.careermatch.backend.exception.ResourceNotFoundException;
import com.careermatch.backend.util.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final RecruiterRepository recruiterRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;

    @Transactional
    public String register(RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new BadRequestException("Email is already registered");
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .isVerified(false)
                .verificationToken(UUID.randomUUID().toString())
                .build();

        User savedUser = userRepository.save(user);

        // Initialize empty profile depending on role
        if (request.getRole() == UserRole.ROLE_STUDENT) {
            Student student = Student.builder()
                    .id(savedUser.getId())
                    .user(savedUser)
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .profileCompletedPct(0)
                    .build();
            studentRepository.save(student);
        } else if (request.getRole() == UserRole.ROLE_RECRUITER) {
            Company company = null;
            if (request.getCompanyName() != null && !request.getCompanyName().isBlank()) {
                company = companyRepository.findByNameIgnoreCase(request.getCompanyName())
                        .orElseGet(() -> companyRepository.save(
                                Company.builder()
                                        .name(request.getCompanyName())
                                        .isVerified(false)
                                        .build()
                        ));
            }
            Recruiter recruiter = Recruiter.builder()
                    .id(savedUser.getId())
                    .user(savedUser)
                    .company(company)
                    .jobTitle(request.getJobTitle())
                    .isVerified(false)
                    .build();
            recruiterRepository.save(recruiter);
        }

        log.info("Registered user: {} with role: {}", savedUser.getEmail(), savedUser.getRole());
        
        String link = "http://localhost:8080/api/v1/auth/verify-email?token=" + savedUser.getVerificationToken();
        String html = "<p>Thank you for registering. Please verify your email by clicking the link below:</p>" +
                "<p><a href=\"" + link + "\">Verify Email</a></p>";
        emailService.sendEmail(savedUser.getEmail(), "Verify Your Email - CareerMatch AI", html);

        return "Registration successful. Please verify your email.";
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Invalid email or password");
        }

        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPasswordHash(),
                Collections.singletonList(new SimpleGrantedAuthority(user.getRole().name()))
        );

        String accessToken = jwtTokenProvider.generateToken(userDetails);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userDetails);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .email(user.getEmail())
                .role(user.getRole().name())
                .userId(user.getId())
                .build();
    }

    @Transactional
    public LoginResponse loginWithGoogle(GoogleLoginRequest request) {
        // Mock parsing Google ID Token
        // In production, verify with Google API Client library:
        // GoogleIdToken idToken = GoogleIdTokenVerifier.verify(request.getIdToken());
        // String email = idToken.getPayload().getEmail();
        
        // Mock verification: extract email from mock idToken (assume idToken is just the email for local test)
        String email = request.getIdToken().contains("@") ? request.getIdToken() : "student@gmail.com";
        
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User newUser = User.builder()
                    .email(email)
                    .role(UserRole.ROLE_STUDENT)
                    .isVerified(true)
                    .build();
            User saved = userRepository.save(newUser);
            
            Student student = Student.builder()
                    .id(saved.getId())
                    .user(saved)
                    .firstName("Google")
                    .lastName("User")
                    .profileCompletedPct(0)
                    .build();
            studentRepository.save(student);
            return saved;
        });

        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                "",
                Collections.singletonList(new SimpleGrantedAuthority(user.getRole().name()))
        );

        String accessToken = jwtTokenProvider.generateToken(userDetails);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userDetails);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .email(user.getEmail())
                .role(user.getRole().name())
                .userId(user.getId())
                .build();
    }

    public LoginResponse refreshToken(RefreshTokenRequest request) {
        String token = request.getRefreshToken();
        String email = jwtTokenProvider.extractUsername(token);
        
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("Invalid refresh token"));

        UserDetails userDetails = new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPasswordHash() != null ? user.getPasswordHash() : "",
                Collections.singletonList(new SimpleGrantedAuthority(user.getRole().name()))
        );

        if (!jwtTokenProvider.validateToken(token, userDetails)) {
            throw new BadRequestException("Expired or invalid refresh token");
        }

        String newAccessToken = jwtTokenProvider.generateToken(userDetails);
        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(token)
                .email(user.getEmail())
                .role(user.getRole().name())
                .userId(user.getId())
                .build();
    }

    @Transactional
    public String verifyEmail(String token) {
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid verification token"));

        user.setVerified(true);
        user.setVerificationToken(null);
        userRepository.save(user);

        return "Email verified successfully.";
    }

    @Transactional
    public String forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("No user found with email " + request.getEmail()));

        // Generate 6 digit numeric OTP
        String otp = String.format("%06d", new Random().nextInt(999999));
        user.setOtp(otp);
        user.setOtpExpiry(LocalDateTime.now().plusMinutes(15));
        userRepository.save(user);

        String html = "<p>Your password reset OTP is: <strong>" + otp + "</strong></p>" +
                "<p>Valid for 15 minutes.</p>";
        emailService.sendEmail(user.getEmail(), "Password Reset OTP - CareerMatch AI", html);

        return "OTP sent to your email. Valid for 15 minutes.";
    }

    @Transactional
    public String resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("No user found with email " + request.getEmail()));

        if (user.getOtp() == null || !user.getOtp().equals(request.getOtp())) {
            throw new BadRequestException("Invalid OTP");
        }

        if (user.getOtpExpiry().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("OTP expired");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setOtp(null);
        user.setOtpExpiry(null);
        userRepository.save(user);

        return "Password reset successful.";
    }
}
