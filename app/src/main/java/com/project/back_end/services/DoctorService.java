package com.project.back_end.services;

import com.project.back_end.DTO.Login;
import com.project.back_end.models.Appointment;
import com.project.back_end.models.Doctor;
import com.project.back_end.repo.AppointmentRepository;
import com.project.back_end.repo.DoctorRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;

@org.springframework.stereotype.Service
public class DoctorService {

  private static final Logger log = LoggerFactory.getLogger(DoctorService.class);

  private final DoctorRepository doctorRepository;
  private final AppointmentRepository appointmentRepository;
  private final TokenService tokenService;

  public DoctorService(DoctorRepository doctorRepository,
      AppointmentRepository appointmentRepository,
      TokenService tokenService) {
    this.doctorRepository = doctorRepository;
    this.appointmentRepository = appointmentRepository;
    this.tokenService = tokenService;
  }

  /**
   * Returns the doctor's slots for the date (e.g. "09:00-10:00") minus the ones already booked.
   * An unknown doctor gets an empty list.
   */
  @Transactional(readOnly = true)
  public List<String> getDoctorAvailability(Long doctorId, LocalDate date) {
    Optional<Doctor> doctor = doctorRepository.findById(doctorId);
    if (doctor.isEmpty() || doctor.get().getAvailableTimes() == null) {
      return List.of();
    }

    Set<LocalTime> booked = appointmentRepository
        .findByDoctorIdAndAppointmentTimeBetween(
            doctorId, date.atStartOfDay(), date.plusDays(1).atStartOfDay())
        .stream()
        .map(Appointment::getAppointmentTime)
        .map(t -> t.toLocalTime().withSecond(0).withNano(0))
        .collect(Collectors.toSet());

    return doctor.get().getAvailableTimes().stream()
        .filter(slot -> !booked.contains(slotStart(slot)))
        .collect(Collectors.toList());
  }

  /** Returns 1 on success, -1 if the email is already taken and 0 on an error. */
  public int saveDoctor(Doctor doctor) {
    try {
      if (doctorRepository.findByEmail(doctor.getEmail()) != null) {
        return -1;
      }
      doctorRepository.save(doctor);
      return 1;
    } catch (Exception e) {
      log.error("Failed to save doctor {}", doctor.getEmail(), e);
      return 0;
    }
  }

  /** Returns 1 on success, -1 if the doctor doesn't exist and 0 on an error. */
  public int updateDoctor(Doctor doctor) {
    try {
      if (doctor.getId() == null || !doctorRepository.existsById(doctor.getId())) {
        return -1;
      }
      doctorRepository.save(doctor);
      return 1;
    } catch (Exception e) {
      log.error("Failed to update doctor {}", doctor.getId(), e);
      return 0;
    }
  }

  @Transactional(readOnly = true)
  public List<Doctor> getDoctors() {
    return loadTimes(doctorRepository.findAll());
  }

  /**
   * Deletes the doctor and their appointments. Returns 1 on success, -1 if the doctor doesn't exist
   * and 0 on an error.
   */
  @Transactional
  public int deleteDoctor(long id) {
    try {
      if (!doctorRepository.existsById(id)) {
        return -1;
      }
      // Appointments reference the doctor, so they have to go first.
      appointmentRepository.deleteAllByDoctorId(id);
      doctorRepository.deleteById(id);
      return 1;
    } catch (Exception e) {
      log.error("Failed to delete doctor {}", id, e);
      return 0;
    }
  }

  /** Checks a doctor's email and password. Returns 200 {"token"} or 401 {"message"}. */
  public ResponseEntity<Map<String, String>> validateDoctor(Login login) {
    Map<String, String> response = new HashMap<>();
    try {
      Doctor doctor = doctorRepository.findByEmail(login.getEmail());
      if (doctor == null || !doctor.getPassword().equals(login.getPassword())) {
        response.put("message", "Invalid email or password");
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
      }
      response.put("token", tokenService.generateToken(doctor.getEmail()));
      return new ResponseEntity<>(response, HttpStatus.OK);
    } catch (Exception e) {
      log.error("Doctor login failed for {}", login.getEmail(), e);
      response.put("message", "Internal server error");
      return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  @Transactional(readOnly = true)
  public Map<String, Object> findDoctorByName(String name) {
    return doctorsResponse(doctorRepository.findByNameLike(name));
  }

  @Transactional(readOnly = true)
  public Map<String, Object> filterDoctorsByNameSpecilityandTime(String name, String specialty,
      String amOrPm) {
    List<Doctor> doctors =
        doctorRepository.findByNameContainingIgnoreCaseAndSpecialtyIgnoreCase(name, specialty);
    return doctorsResponse(filterDoctorByTime(doctors, amOrPm));
  }

  @Transactional(readOnly = true)
  public Map<String, Object> filterDoctorByNameAndTime(String name, String amOrPm) {
    return doctorsResponse(filterDoctorByTime(doctorRepository.findByNameLike(name), amOrPm));
  }

  @Transactional(readOnly = true)
  public Map<String, Object> filterDoctorByNameAndSpecility(String name, String specialty) {
    return doctorsResponse(
        doctorRepository.findByNameContainingIgnoreCaseAndSpecialtyIgnoreCase(name, specialty));
  }

  @Transactional(readOnly = true)
  public Map<String, Object> filterDoctorByTimeAndSpecility(String specialty, String amOrPm) {
    return doctorsResponse(
        filterDoctorByTime(doctorRepository.findBySpecialtyIgnoreCase(specialty), amOrPm));
  }

  @Transactional(readOnly = true)
  public Map<String, Object> filterDoctorBySpecility(String specialty) {
    return doctorsResponse(doctorRepository.findBySpecialtyIgnoreCase(specialty));
  }

  @Transactional(readOnly = true)
  public Map<String, Object> filterDoctorsByTime(String amOrPm) {
    return doctorsResponse(filterDoctorByTime(doctorRepository.findAll(), amOrPm));
  }

  /**
   * Keeps doctors with at least one matching slot. "AM" means a slot starting before 12:00 and
   * "PM" one starting at 12:00 or later. A full slot such as "09:00-10:00" (as in the PDF's curl
   * example) keeps doctors who offer exactly that slot.
   */
  private List<Doctor> filterDoctorByTime(List<Doctor> doctors, String amOrPm) {
    return doctors.stream()
        .filter(d -> d.getAvailableTimes() != null
            && d.getAvailableTimes().stream().anyMatch(slot -> slotMatches(slot, amOrPm)))
        .collect(Collectors.toList());
  }

  private boolean slotMatches(String slot, String time) {
    if ("AM".equalsIgnoreCase(time)) {
      return slotStart(slot).getHour() < 12;
    }
    if ("PM".equalsIgnoreCase(time)) {
      return slotStart(slot).getHour() >= 12;
    }
    return slot.trim().equals(time.trim());
  }

  /** "09:00-10:00" -> 09:00. */
  private LocalTime slotStart(String slot) {
    return LocalTime.parse(slot.split("-")[0].trim());
  }

  private Map<String, Object> doctorsResponse(List<Doctor> doctors) {
    Map<String, Object> response = new HashMap<>();
    response.put("doctors", loadTimes(doctors));
    return response;
  }

  /**
   * availableTimes is a lazy collection and open-in-view is off, so it has to be loaded while the
   * transaction is still open or serializing the doctor to JSON fails.
   */
  private List<Doctor> loadTimes(List<Doctor> doctors) {
    doctors.forEach(d -> {
      if (d.getAvailableTimes() != null) {
        d.getAvailableTimes().size();
      }
    });
    return doctors;
  }
}
