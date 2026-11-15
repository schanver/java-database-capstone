package com.project.back_end.services;

import com.project.back_end.models.Prescription;
import com.project.back_end.repo.AppointmentRepository;
import com.project.back_end.repo.PrescriptionRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@org.springframework.stereotype.Service
public class PrescriptionService {

  private static final Logger log = LoggerFactory.getLogger(PrescriptionService.class);

  private final PrescriptionRepository prescriptionRepository;
  private final AppointmentRepository appointmentRepository;

  public PrescriptionService(PrescriptionRepository prescriptionRepository,
      AppointmentRepository appointmentRepository) {
    this.prescriptionRepository = prescriptionRepository;
    this.appointmentRepository = appointmentRepository;
  }

  /**
   * Saves a prescription unless one already exists for the same appointment.
   * Returns 201 on success, 400 for an unknown appointment or a duplicate, and 500 on a
   * database error. Prescriptions live in MongoDB and appointments in MySQL, so nothing but
   * this check stops a prescription pointing at an appointment that doesn't exist.
   */
  public ResponseEntity<Map<String, String>> savePrescription(Prescription prescription) {
    Map<String, String> response = new HashMap<>();
    try {
      if (!appointmentRepository.existsById(prescription.getAppointmentId())) {
        response.put("message", "Appointment not found");
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
      }
      List<Prescription> existing = prescriptionRepository.findByAppointmentId(prescription.getAppointmentId());
      if (!existing.isEmpty()) {
        response.put("message", "Prescription already exists for this appointment");
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
      }
      prescriptionRepository.save(prescription);
      response.put("message", "Prescription saved");
      return new ResponseEntity<>(response, HttpStatus.CREATED);
    } catch (Exception e) {
      log.error("Failed to save prescription for appointment {}", prescription.getAppointmentId(), e);
      response.put("message", "Internal server error");
      return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Returns the prescriptions for an appointment under "prescription" (a list,
   * possibly empty,
   * which addPrescription.js checks with .length). Returns 500 on a database
   * error.
   */
  public ResponseEntity<Map<String, Object>> getPrescription(Long appointmentId) {
    Map<String, Object> response = new HashMap<>();
    try {
      response.put("prescription", prescriptionRepository.findByAppointmentId(appointmentId));
      return new ResponseEntity<>(response, HttpStatus.OK);
    } catch (Exception e) {
      log.error("Failed to fetch prescription for appointment {}", appointmentId, e);
      response.put("message", "Internal server error");
      return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }
}
