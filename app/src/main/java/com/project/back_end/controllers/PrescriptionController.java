package com.project.back_end.controllers;

import com.project.back_end.models.Prescription;
import com.project.back_end.services.AppointmentService;
import com.project.back_end.services.PrescriptionService;
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
@RequestMapping("${api.path}prescription")
public class PrescriptionController {

  /** Appointment.status once a prescription has been written. */
  private static final int STATUS_COMPLETED = 1;

  private final PrescriptionService prescriptionService;
  private final Service service;
  private final AppointmentService appointmentService;

  public PrescriptionController(PrescriptionService prescriptionService,
      Service service,
      AppointmentService appointmentService) {
    this.prescriptionService = prescriptionService;
    this.service = service;
    this.appointmentService = appointmentService;
  }

  /** Doctor: save a prescription and mark its appointment completed. */
  @PostMapping("/{token}")
  public ResponseEntity<Map<String, String>> savePrescription(
      @Valid @RequestBody Prescription prescription,
      @PathVariable String token) {
    ResponseEntity<Map<String, String>> check = service.validateToken(token, "doctor");
    if (!check.getBody().isEmpty()) {
      return check;
    }
    ResponseEntity<Map<String, String>> result = prescriptionService.savePrescription(prescription);
    // Only mark the appointment completed once the prescription is actually stored, so a
    // duplicate or failed save leaves the status alone.
    if (result.getStatusCode() == HttpStatus.CREATED) {
      appointmentService.changeStatus(STATUS_COMPLETED, prescription.getAppointmentId());
    }
    return result;
  }

  /** Doctor: the prescriptions for an appointment under "prescription". */
  @GetMapping("/{appointmentId}/{token}")
  public ResponseEntity<Map<String, Object>> getPrescription(
      @PathVariable Long appointmentId,
      @PathVariable String token) {
    ResponseEntity<Map<String, String>> check = service.validateToken(token, "doctor");
    if (!check.getBody().isEmpty()) {
      return new ResponseEntity<>(new HashMap<>(check.getBody()), check.getStatusCode());
    }
    return prescriptionService.getPrescription(appointmentId);
  }
}
