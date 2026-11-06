package com.project.back_end.services;

import com.project.back_end.repo.AdminRepository;
import com.project.back_end.repo.DoctorRepository;
import com.project.back_end.repo.PatientRepository;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TokenService {

  private static final long EXPIRATION_MS = 7L * 24 * 60 * 60 * 1000; // 7 days

  private final AdminRepository adminRepository;
  private final DoctorRepository doctorRepository;
  private final PatientRepository patientRepository;
  private final String jwtSecret;

  public TokenService(AdminRepository adminRepository,
      DoctorRepository doctorRepository,
      PatientRepository patientRepository,
      @Value("${jwt.secret}") String jwtSecret) {
    this.adminRepository = adminRepository;
    this.doctorRepository = doctorRepository;
    this.patientRepository = patientRepository;
    this.jwtSecret = jwtSecret;
  }

  public SecretKey getSigningKey() {
    return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
  }

  public String generateToken(String email) {
    Date now = new Date();
    return Jwts.builder()
        .subject(email)
        .issuedAt(now)
        .expiration(new Date(now.getTime() + EXPIRATION_MS))
        .signWith(getSigningKey())
        .compact();
  }

  public String extractEmail(String token) {
    return Jwts.parser()
        .verifyWith(getSigningKey())
        .build()
        .parseSignedClaims(token)
        .getPayload()
        .getSubject();
  }

  public boolean validateToken(String token, String user) {
    try {
      String email = extractEmail(token);
      return switch (user) {
        case "admin" -> adminRepository.findByUsername(email) != null;
        case "doctor" -> doctorRepository.findByEmail(email) != null;
        case "patient" -> patientRepository.findByEmail(email) != null;
        default -> false;
      };
    } catch (JwtException | IllegalArgumentException e) {
      return false;
    }
  }
}
