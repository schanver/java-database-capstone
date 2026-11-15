package com.project.back_end.services;

import com.project.back_end.DTO.Login;
import com.project.back_end.models.Admin;
import com.project.back_end.models.Appointment;
import com.project.back_end.models.Patient;
import com.project.back_end.repo.AdminRepository;
import com.project.back_end.repo.DoctorRepository;
import com.project.back_end.repo.PatientRepository;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Cross-cutting logic shared by the controllers: token checks, logins, doctor and appointment
 * filtering, and booking validation. It composes the per-entity services.
 *
 * <p>Don't inject AppointmentService here: AppointmentService already depends on this class, and
 * Spring Boot refuses circular dependencies.
 */
@org.springframework.stereotype.Service
public class Service {

    private static final Logger log = LoggerFactory.getLogger(Service.class);

    private final TokenService tokenService;
    private final AdminRepository adminRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final DoctorService doctorService;
    private final PatientService patientService;

    public Service(TokenService tokenService,
            AdminRepository adminRepository,
            DoctorRepository doctorRepository,
            PatientRepository patientRepository,
            DoctorService doctorService,
            PatientService patientService) {
        this.tokenService = tokenService;
        this.adminRepository = adminRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.doctorService = doctorService;
        this.patientService = patientService;
    }

    /**
     * Returns an empty body with 200 OK when the token is valid for the given role,
     * otherwise a 401 with {"message": ...}.
     */
    public ResponseEntity<Map<String, String>> validateToken(String token, String user) {
        Map<String, String> response = new HashMap<>();
        if (!tokenService.validateToken(token, user)) {
            response.put("message", "Invalid or expired token");
            return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
        }
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /** Admin login by username. Returns 200 {"token"}, 401 {"message"} or 500. */
    public ResponseEntity<Map<String, String>> validateAdmin(Admin receivedAdmin) {
        Map<String, String> response = new HashMap<>();
        try {
            Admin admin = adminRepository.findByUsername(receivedAdmin.getUsername());
            if (admin == null || !admin.getPassword().equals(receivedAdmin.getPassword())) {
                response.put("message", "Invalid username or password");
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }
            response.put("token", tokenService.generateToken(admin.getUsername()));
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            log.error("Admin login failed for {}", receivedAdmin.getUsername(), e);
            response.put("message", "Internal server error");
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Filters doctors by any combination of name, specialty and time ("AM", "PM" or a slot).
     * The frontend sends the string "null" for an empty filter. Note the argument order differs
     * from the URL /doctor/filter/{name}/{time}/{speciality}.
     */
    public Map<String, Object> filterDoctor(String name, String specialty, String time) {
        name = blankToNull(name);
        specialty = blankToNull(specialty);
        time = blankToNull(time);

        if (name != null && specialty != null && time != null) {
            return doctorService.filterDoctorsByNameSpecilityandTime(name, specialty, time);
        }
        if (name != null && specialty != null) {
            return doctorService.filterDoctorByNameAndSpecility(name, specialty);
        }
        if (name != null && time != null) {
            return doctorService.filterDoctorByNameAndTime(name, time);
        }
        if (specialty != null && time != null) {
            return doctorService.filterDoctorByTimeAndSpecility(specialty, time);
        }
        if (name != null) {
            return doctorService.findDoctorByName(name);
        }
        if (specialty != null) {
            return doctorService.filterDoctorBySpecility(specialty);
        }
        if (time != null) {
            return doctorService.filterDoctorsByTime(time);
        }
        Map<String, Object> response = new HashMap<>();
        response.put("doctors", doctorService.getDoctors());
        return response;
    }

    /**
     * Checks that the appointment's start time is one of the doctor's free slots that day.
     * Returns 1 if it is, 0 if not, and -1 if the doctor doesn't exist.
     */
    public int validateAppointment(Appointment appointment) {
        if (appointment.getDoctor() == null || appointment.getDoctor().getId() == null
                || appointment.getAppointmentTime() == null) {
            return -1;
        }
        Long doctorId = appointment.getDoctor().getId();
        if (!doctorRepository.existsById(doctorId)) {
            return -1;
        }

        List<String> freeSlots = doctorService.getDoctorAvailability(
                doctorId, appointment.getAppointmentTime().toLocalDate());
        LocalTime requested = appointment.getAppointmentTime().toLocalTime().withSecond(0).withNano(0);
        for (String slot : freeSlots) {
            if (LocalTime.parse(slot.split("-")[0].trim()).equals(requested)) {
                return 1;
            }
        }
        return 0;
    }

    /** True when no patient has this email or phone yet, so they may sign up. */
    public boolean validatePatient(Patient patient) {
        return patientRepository.findByEmailOrPhone(patient.getEmail(), patient.getPhone()) == null;
    }

    /** Patient login by email. Returns 200 {"token"}, 401 {"message"} or 500. */
    public ResponseEntity<Map<String, String>> validatePatientLogin(Login login) {
        Map<String, String> response = new HashMap<>();
        try {
            Patient patient = patientRepository.findByEmail(login.getEmail());
            if (patient == null || !patient.getPassword().equals(login.getPassword())) {
                response.put("message", "Invalid email or password");
                return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
            }
            response.put("token", tokenService.generateToken(patient.getEmail()));
            return new ResponseEntity<>(response, HttpStatus.OK);
        } catch (Exception e) {
            log.error("Patient login failed for {}", login.getEmail(), e);
            response.put("message", "Internal server error");
            return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Filters the token owner's appointments by condition ("past"/"future") and/or doctor name.
     * "null" means no filter; with neither filter it returns all of the patient's appointments.
     */
    public ResponseEntity<Map<String, Object>> filterPatient(String condition, String name, String token) {
        Patient patient = patientRepository.findByEmail(tokenService.extractEmail(token));
        if (patient == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Patient not found");
            return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
        }

        condition = blankToNull(condition);
        name = blankToNull(name);
        Long patientId = patient.getId();

        if (condition != null && name != null) {
            return patientService.filterByDoctorAndCondition(condition, name, patientId);
        }
        if (condition != null) {
            return patientService.filterByCondition(condition, patientId);
        }
        if (name != null) {
            return patientService.filterByDoctor(name, patientId);
        }
        return patientService.getPatientAppointment(patientId, token);
    }

    /** The frontend encodes an empty filter as the string "null". */
    private static String blankToNull(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("null")) {
            return null;
        }
        return value.trim();
    }
}
