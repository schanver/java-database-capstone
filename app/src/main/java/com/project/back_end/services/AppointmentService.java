package com.project.back_end.services;

import com.project.back_end.models.Appointment;
import com.project.back_end.models.Doctor;
import com.project.back_end.models.Patient;
import com.project.back_end.repo.AppointmentRepository;
import com.project.back_end.repo.DoctorRepository;
import com.project.back_end.repo.PatientRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;

// Fully qualified: this package also contains the shared Service class injected below.
@org.springframework.stereotype.Service
public class AppointmentService {

  private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);

  private final AppointmentRepository appointmentRepository;
  private final Service service;
  private final TokenService tokenService;
  private final PatientRepository patientRepository;
  private final DoctorRepository doctorRepository;

  public AppointmentService(AppointmentRepository appointmentRepository,
      Service service,
      TokenService tokenService,
      PatientRepository patientRepository,
      DoctorRepository doctorRepository) {
    this.appointmentRepository = appointmentRepository;
    this.service = service;
    this.tokenService = tokenService;
    this.patientRepository = patientRepository;
    this.doctorRepository = doctorRepository;
  }

  /**
   * Books the appointment for the token's patient. The controller has already run
   * Service.validateAppointment. Returns 1 on success and 0 on an error.
   *
   * <p>Not @Transactional on purpose: save() runs in its own transaction, so a failed save is
   * caught here and returned as 0 instead of surfacing later as an UnexpectedRollbackException.
   */
  public int bookAppointment(Appointment appointment, String token) {
    try {
      Patient patient = patientFromToken(token);
      if (patient == null) {
        return 0;
      }
      // The body only carries {doctor: {id}, patient: {id}}. Use the real doctor, and always
      // book for the logged-in patient rather than whoever the body names.
      appointment.setDoctor(doctorRepository.getReferenceById(appointment.getDoctor().getId()));
      appointment.setPatient(patient);
      appointmentRepository.save(appointment);
      return 1;
    } catch (Exception e) {
      log.error("Failed to book appointment", e);
      return 0;
    }
  }

  /**
   * Moves an existing appointment to a new time (and optionally another doctor). Only the patient
   * who owns it may change it, and the new slot must be free.
   */
  @Transactional
  public ResponseEntity<Map<String, String>> updateAppointment(Appointment appointment,
      String token) {
    Map<String, String> response = new HashMap<>();
    if (appointment.getId() == null) {
      return message(response, HttpStatus.BAD_REQUEST, "Appointment id is required");
    }
    Optional<Appointment> found = appointmentRepository.findById(appointment.getId());
    if (found.isEmpty()) {
      return message(response, HttpStatus.BAD_REQUEST, "Appointment not found");
    }
    Appointment existing = found.get();

    Patient caller = patientFromToken(token);
    if (caller == null || !existing.getPatient().getId().equals(caller.getId())) {
      return message(response, HttpStatus.UNAUTHORIZED,
          "You can only update your own appointments");
    }

    int valid = service.validateAppointment(appointment);
    if (valid == -1) {
      return message(response, HttpStatus.BAD_REQUEST, "Invalid doctor id");
    }
    if (valid == 0) {
      return message(response, HttpStatus.CONFLICT,
          "Appointment already booked for given time or doctor not available");
    }

    try {
      existing.setDoctor(doctorRepository.getReferenceById(appointment.getDoctor().getId()));
      existing.setAppointmentTime(appointment.getAppointmentTime());
      appointmentRepository.save(existing);
      return message(response, HttpStatus.OK, "Appointment updated successfully");
    } catch (Exception e) {
      log.error("Failed to update appointment {}", appointment.getId(), e);
      return message(response, HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }
  }

  /** Deletes the appointment if it belongs to the token's patient. */
  @Transactional
  public ResponseEntity<Map<String, String>> cancelAppointment(long id, String token) {
    Map<String, String> response = new HashMap<>();
    Optional<Appointment> found = appointmentRepository.findById(id);
    if (found.isEmpty()) {
      return message(response, HttpStatus.BAD_REQUEST, "Appointment not found");
    }

    Patient caller = patientFromToken(token);
    if (caller == null || !found.get().getPatient().getId().equals(caller.getId())) {
      return message(response, HttpStatus.FORBIDDEN,
          "You can only cancel your own appointments");
    }

    try {
      appointmentRepository.delete(found.get());
      return message(response, HttpStatus.OK, "Appointment cancelled successfully");
    } catch (Exception e) {
      log.error("Failed to cancel appointment {}", id, e);
      return message(response, HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }
  }

  /**
   * Returns {"appointments": [...]} for the token's doctor on the given day, optionally narrowed
   * to patients whose name contains pname. The doctor dashboard sends "null" for no filter.
   */
  @Transactional(readOnly = true)
  public Map<String, Object> getAppointment(String pname, LocalDate date, String token) {
    Map<String, Object> response = new HashMap<>();
    Doctor doctor = doctorRepository.findByEmail(tokenService.extractEmail(token));
    if (doctor == null) {
      response.put("appointments", List.of());
      return response;
    }

    LocalDateTime start = date.atStartOfDay();
    LocalDateTime end = date.plusDays(1).atStartOfDay();
    List<Appointment> appointments;
    if (pname == null || pname.isBlank() || pname.equalsIgnoreCase("null")) {
      appointments = appointmentRepository
          .findByDoctorIdAndAppointmentTimeBetween(doctor.getId(), start, end);
    } else {
      appointments = appointmentRepository
          .findByDoctorIdAndPatient_NameContainingIgnoreCaseAndAppointmentTimeBetween(
              doctor.getId(), pname.trim(), start, end);
    }

    // The entities are serialized after this transaction ends (open-in-view is off), so the
    // doctor's lazy availableTimes must be loaded now.
    appointments.forEach(a -> {
      if (a.getDoctor().getAvailableTimes() != null) {
        a.getDoctor().getAvailableTimes().size();
      }
    });
    response.put("appointments", appointments);
    return response;
  }

  /** Sets the status, e.g. 1 once a prescription has been added. */
  @Transactional
  public void changeStatus(int status, long id) {
    appointmentRepository.updateStatus(status, id);
  }

  private Patient patientFromToken(String token) {
    try {
      return patientRepository.findByEmail(tokenService.extractEmail(token));
    } catch (Exception e) {
      return null;
    }
  }

  private static ResponseEntity<Map<String, String>> message(Map<String, String> response,
      HttpStatus status, String text) {
    response.put("message", text);
    return new ResponseEntity<>(response, status);
  }
}
