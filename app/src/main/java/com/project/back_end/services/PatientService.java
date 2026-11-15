package com.project.back_end.services;

import com.project.back_end.DTO.AppointmentDTO;
import com.project.back_end.models.Appointment;
import com.project.back_end.models.Patient;
import com.project.back_end.repo.AppointmentRepository;
import com.project.back_end.repo.PatientRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
public class PatientService {

  private static final Logger log = LoggerFactory.getLogger(PatientService.class);

  /** Appointment.status values: 0 = upcoming, 1 = completed (a prescription was added). */
  private static final int STATUS_FUTURE = 0;
  private static final int STATUS_PAST = 1;

  private final PatientRepository patientRepository;
  private final AppointmentRepository appointmentRepository;
  private final TokenService tokenService;

  public PatientService(PatientRepository patientRepository,
      AppointmentRepository appointmentRepository,
      TokenService tokenService) {
    this.patientRepository = patientRepository;
    this.appointmentRepository = appointmentRepository;
    this.tokenService = tokenService;
  }

  /** Returns 1 on success and 0 on an error. */
  public int createPatient(Patient patient) {
    try {
      patientRepository.save(patient);
      return 1;
    } catch (Exception e) {
      log.error("Failed to create patient {}", patient.getEmail(), e);
      return 0;
    }
  }

  /**
   * Returns {"appointments": [AppointmentDTO]} for the patient. The controller has already
   * validated the token for the caller's role. A patient may only read their own appointments; a
   * doctor (patientRecordServices.js) may read any patient's.
   */
  @Transactional(readOnly = true)
  public ResponseEntity<Map<String, Object>> getPatientAppointment(Long id, String token) {
    Map<String, Object> response = new HashMap<>();
    try {
      Patient caller = patientRepository.findByEmail(tokenService.extractEmail(token));
      if (caller != null && !caller.getId().equals(id)) {
        response.put("message", "Unauthorized");
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
      }
      response.put("appointments", toDTOs(appointmentRepository.findByPatientId(id)));
      return new ResponseEntity<>(response, HttpStatus.OK);
    } catch (Exception e) {
      log.error("Failed to fetch appointments for patient {}", id, e);
      return serverError(response);
    }
  }

  /** condition is "past" or "future"; anything else is a 400. */
  @Transactional(readOnly = true)
  public ResponseEntity<Map<String, Object>> filterByCondition(String condition, Long id) {
    Map<String, Object> response = new HashMap<>();
    Integer status = statusFor(condition);
    if (status == null) {
      response.put("message", "Invalid condition");
      return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
    try {
      response.put("appointments", toDTOs(
          appointmentRepository.findByPatient_IdAndStatusOrderByAppointmentTimeAsc(id, status)));
      return new ResponseEntity<>(response, HttpStatus.OK);
    } catch (Exception e) {
      log.error("Failed to filter appointments for patient {} by {}", id, condition, e);
      return serverError(response);
    }
  }

  /** Partial, case-insensitive match on the doctor's name. */
  @Transactional(readOnly = true)
  public ResponseEntity<Map<String, Object>> filterByDoctor(String name, Long patientId) {
    Map<String, Object> response = new HashMap<>();
    try {
      response.put("appointments", toDTOs(
          appointmentRepository.filterByDoctorNameAndPatientId(name, patientId)));
      return new ResponseEntity<>(response, HttpStatus.OK);
    } catch (Exception e) {
      log.error("Failed to filter appointments for patient {} by doctor {}", patientId, name, e);
      return serverError(response);
    }
  }

  @Transactional(readOnly = true)
  public ResponseEntity<Map<String, Object>> filterByDoctorAndCondition(String condition,
      String name, long patientId) {
    Map<String, Object> response = new HashMap<>();
    Integer status = statusFor(condition);
    if (status == null) {
      response.put("message", "Invalid condition");
      return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
    try {
      response.put("appointments", toDTOs(
          appointmentRepository.filterByDoctorNameAndPatientIdAndStatus(name, patientId, status)));
      return new ResponseEntity<>(response, HttpStatus.OK);
    } catch (Exception e) {
      log.error("Failed to filter appointments for patient {} by doctor {} and {}",
          patientId, name, condition, e);
      return serverError(response);
    }
  }

  /** Returns {"patient": ...} for the token's owner (patientServices.js reads data.patient). */
  public ResponseEntity<Map<String, Object>> getPatientDetails(String token) {
    Map<String, Object> response = new HashMap<>();
    try {
      Patient patient = patientRepository.findByEmail(tokenService.extractEmail(token));
      if (patient == null) {
        response.put("message", "Patient not found");
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
      }
      response.put("patient", patient);
      return new ResponseEntity<>(response, HttpStatus.OK);
    } catch (Exception e) {
      log.error("Failed to fetch patient details", e);
      return serverError(response);
    }
  }

  private Integer statusFor(String condition) {
    if ("past".equalsIgnoreCase(condition)) {
      return STATUS_PAST;
    }
    if ("future".equalsIgnoreCase(condition)) {
      return STATUS_FUTURE;
    }
    return null;
  }

  private List<AppointmentDTO> toDTOs(List<Appointment> appointments) {
    return appointments.stream().map(this::toDTO).collect(Collectors.toList());
  }

  private AppointmentDTO toDTO(Appointment a) {
    Patient p = a.getPatient();
    return new AppointmentDTO(
        a.getId(),
        a.getDoctor().getId(),
        a.getDoctor().getName(),
        p.getId(),
        p.getName(),
        p.getEmail(),
        p.getPhone(),
        p.getAddress(),
        a.getAppointmentTime(),
        a.getStatus());
  }

  private ResponseEntity<Map<String, Object>> serverError(Map<String, Object> response) {
    response.put("message", "Internal server error");
    return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
