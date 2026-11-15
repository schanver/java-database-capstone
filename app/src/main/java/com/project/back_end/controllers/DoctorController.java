package com.project.back_end.controllers;

import com.project.back_end.DTO.Login;
import com.project.back_end.models.Doctor;
import com.project.back_end.services.DoctorService;
import com.project.back_end.services.Service;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${api.path}doctor")
public class DoctorController {

  private final DoctorService doctorService;
  private final Service service;

  public DoctorController(DoctorService doctorService, Service service) {
    this.doctorService = doctorService;
    this.service = service;
  }

  /** Free slots for a doctor on a date (YYYY-MM-DD), for any logged-in role. */
  @GetMapping("/availability/{user}/{doctorId}/{date}/{token}")
  public ResponseEntity<Map<String, Object>> getDoctorAvailability(
      @PathVariable String user,
      @PathVariable Long doctorId,
      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      @PathVariable String token) {
    ResponseEntity<Map<String, String>> check = service.validateToken(token, user);
    if (!check.getBody().isEmpty()) {
      return new ResponseEntity<>(new HashMap<>(check.getBody()), check.getStatusCode());
    }
    Map<String, Object> response = new HashMap<>();
    response.put("availability", doctorService.getDoctorAvailability(doctorId, date));
    return ResponseEntity.ok(response);
  }

  @GetMapping
  public ResponseEntity<Map<String, Object>> getDoctor() {
    Map<String, Object> response = new HashMap<>();
    response.put("doctors", doctorService.getDoctors());
    return ResponseEntity.ok(response);
  }

  /** Admin: add a doctor. */
  @PostMapping("/{token}")
  public ResponseEntity<Map<String, String>> saveDoctor(
      @Valid @RequestBody Doctor doctor,
      @PathVariable String token) {
    ResponseEntity<Map<String, String>> check = service.validateToken(token, "admin");
    if (!check.getBody().isEmpty()) {
      return check;
    }
    return switch (doctorService.saveDoctor(doctor)) {
      case 1 -> message(HttpStatus.CREATED, "Doctor added to db");
      case -1 -> message(HttpStatus.CONFLICT, "Doctor already exists");
      default -> message(HttpStatus.INTERNAL_SERVER_ERROR, "Some internal error occurred");
    };
  }

  /** index.js posts {email, password}; returns 200 {"token"} or 401 {"message"}. */
  @PostMapping("/login")
  public ResponseEntity<Map<String, String>> doctorLogin(@Valid @RequestBody Login login) {
    return doctorService.validateDoctor(login);
  }

  /** Admin: replace a doctor's details (the body must include the id and password). */
  @PutMapping("/{token}")
  public ResponseEntity<Map<String, String>> updateDoctor(
      @Valid @RequestBody Doctor doctor,
      @PathVariable String token) {
    ResponseEntity<Map<String, String>> check = service.validateToken(token, "admin");
    if (!check.getBody().isEmpty()) {
      return check;
    }
    return switch (doctorService.updateDoctor(doctor)) {
      case 1 -> message(HttpStatus.OK, "Doctor updated");
      case -1 -> message(HttpStatus.NOT_FOUND, "Doctor not found");
      default -> message(HttpStatus.INTERNAL_SERVER_ERROR, "Some internal error occurred");
    };
  }

  /** Admin: delete a doctor and their appointments. */
  @DeleteMapping("/{id}/{token}")
  public ResponseEntity<Map<String, String>> deleteDoctor(
      @PathVariable long id,
      @PathVariable String token) {
    ResponseEntity<Map<String, String>> check = service.validateToken(token, "admin");
    if (!check.getBody().isEmpty()) {
      return check;
    }
    return switch (doctorService.deleteDoctor(id)) {
      case 1 -> message(HttpStatus.OK, "Doctor deleted successfully");
      case -1 -> message(HttpStatus.NOT_FOUND, "Doctor not found with id");
      default -> message(HttpStatus.INTERNAL_SERVER_ERROR, "Some internal error occurred");
    };
  }

  /** "null" in any segment means no filter. time is "AM", "PM" or a slot like "09:00-10:00". */
  @GetMapping("/filter/{name}/{time}/{speciality}")
  public ResponseEntity<Map<String, Object>> filter(
      @PathVariable String name,
      @PathVariable String time,
      @PathVariable String speciality) {
    // Service.filterDoctor takes (name, specialty, time): a different order from the URL.
    return ResponseEntity.ok(service.filterDoctor(name, speciality, time));
  }

  private static ResponseEntity<Map<String, String>> message(HttpStatus status, String text) {
    Map<String, String> response = new HashMap<>();
    response.put("message", text);
    return new ResponseEntity<>(response, status);
  }
}
