package com.project.back_end.controllers;

import com.project.back_end.DTO.Login;
import com.project.back_end.models.Patient;
import com.project.back_end.services.PatientService;
import com.project.back_end.services.Service;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/patient")
public class PatientController {

  private final PatientService patientService;
  private final Service service;

  public PatientController(PatientService patientService, Service service) {
    this.patientService = patientService;
    this.service = service;
  }

  /** The logged-in patient's details under "patient". */
  @GetMapping("/{token}")
  public ResponseEntity<Map<String, Object>> getPatient(@PathVariable String token) {
    ResponseEntity<Map<String, Object>> invalid = checkToken(token, "patient");
    if (invalid != null) {
      return invalid;
    }
    return patientService.getPatientDetails(token);
  }

  /** Signup. 201 on success, 409 if the email or phone is taken. */
  @PostMapping
  public ResponseEntity<Map<String, String>> createPatient(@Valid @RequestBody Patient patient) {
    Map<String, String> response = new HashMap<>();
    if (!service.validatePatient(patient)) {
      response.put("message", "Patient with email id or phone no already exist");
      return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }
    if (patientService.createPatient(patient) == 1) {
      response.put("message", "Signup successful");
      return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    response.put("message", "Internal server error");
    return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @PostMapping("/login")
  public ResponseEntity<Map<String, String>> login(@RequestBody Login login) {
    return service.validatePatientLogin(login);
  }

  /**
   * A patient's appointments. user is "patient" (patientAppointment.js) or "doctor"
   * (patientRecordServices.js); PatientService stops a patient reading someone else's.
   */
  @GetMapping("/{id}/{user}/{token}")
  public ResponseEntity<Map<String, Object>> getPatientAppointment(
      @PathVariable Long id,
      @PathVariable String user,
      @PathVariable String token) {
    ResponseEntity<Map<String, Object>> invalid = checkToken(token, user);
    if (invalid != null) {
      return invalid;
    }
    return patientService.getPatientAppointment(id, token);
  }

  /** condition is "past", "future" or "null"; name is a doctor name or "null". */
  @GetMapping("/filter/{condition}/{name}/{token}")
  public ResponseEntity<Map<String, Object>> filterPatientAppointment(
      @PathVariable String condition,
      @PathVariable String name,
      @PathVariable String token) {
    ResponseEntity<Map<String, Object>> invalid = checkToken(token, "patient");
    if (invalid != null) {
      return invalid;
    }
    return service.filterPatient(condition, name, token);
  }

  /** Returns the error response if the token isn't valid for the role, otherwise null. */
  private ResponseEntity<Map<String, Object>> checkToken(String token, String role) {
    ResponseEntity<Map<String, String>> check = service.validateToken(token, role);
    if (check.getBody().isEmpty()) {
      return null;
    }
    return new ResponseEntity<>(new HashMap<>(check.getBody()), check.getStatusCode());
  }
}
