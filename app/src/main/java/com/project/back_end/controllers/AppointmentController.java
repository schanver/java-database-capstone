package com.project.back_end.controllers;

import com.project.back_end.models.Appointment;
import com.project.back_end.services.AppointmentService;
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
@RequestMapping("/appointments")
public class AppointmentController {

  private final AppointmentService appointmentService;
  private final Service service;

  public AppointmentController(AppointmentService appointmentService, Service service) {
    this.appointmentService = appointmentService;
    this.service = service;
  }

  /** Doctor: their appointments on a date (YYYY-MM-DD); patientName "null" means everyone. */
  @GetMapping("/{date}/{patientName}/{token}")
  public ResponseEntity<Map<String, Object>> getAppointments(
      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      @PathVariable String patientName,
      @PathVariable String token) {
    ResponseEntity<Map<String, String>> check = service.validateToken(token, "doctor");
    if (!check.getBody().isEmpty()) {
      return new ResponseEntity<>(new HashMap<>(check.getBody()), check.getStatusCode());
    }
    return ResponseEntity.ok(appointmentService.getAppointment(patientName, date, token));
  }

  /** Patient: book an appointment. 201 on success, 400 bad doctor, 409 slot unavailable. */
  @PostMapping("/{token}")
  public ResponseEntity<Map<String, String>> bookAppointment(
      @Valid @RequestBody Appointment appointment,
      @PathVariable String token) {
    ResponseEntity<Map<String, String>> check = service.validateToken(token, "patient");
    if (!check.getBody().isEmpty()) {
      return check;
    }

    Map<String, String> response = new HashMap<>();
    int valid = service.validateAppointment(appointment);
    if (valid == -1) {
      response.put("message", "Invalid doctor id");
      return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
    if (valid == 0) {
      response.put("message", "Appointment already booked for given time or doctor not available");
      return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    if (appointmentService.bookAppointment(appointment, token) == 1) {
      response.put("message", "Appointment booked successfully");
      return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    response.put("message", "Internal server error");
    return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  /** Patient: move one of their appointments to another time or doctor. */
  @PutMapping("/{token}")
  public ResponseEntity<Map<String, String>> updateAppointment(
      @Valid @RequestBody Appointment appointment,
      @PathVariable String token) {
    ResponseEntity<Map<String, String>> check = service.validateToken(token, "patient");
    if (!check.getBody().isEmpty()) {
      return check;
    }
    return appointmentService.updateAppointment(appointment, token);
  }

  /** Patient: cancel one of their appointments. */
  @DeleteMapping("/{id}/{token}")
  public ResponseEntity<Map<String, String>> cancelAppointment(
      @PathVariable long id,
      @PathVariable String token) {
    ResponseEntity<Map<String, String>> check = service.validateToken(token, "patient");
    if (!check.getBody().isEmpty()) {
      return check;
    }
    return appointmentService.cancelAppointment(id, token);
  }
}
