package com.project.back_end.repo;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.project.back_end.models.Appointment;

import jakarta.transaction.Transactional;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

  @Query("SELECT a FROM Appointment a " +
      "LEFT JOIN FETCH a.doctor d " +
      "LEFT JOIN FETCH d.availableTimes " +
      "WHERE d.id = :doctorId " +
      "AND a.appointmentTime BETWEEN :start AND :end")
  List<Appointment> findByDoctorIdAndAppointmentTimeBetween(
      @Param("doctorId") Long doctorId,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);

  @Query("SELECT a FROM Appointment a " +
      "LEFT JOIN FETCH a.doctor d " +
      "LEFT JOIN FETCH a.patient p " +
      "WHERE d.id = :doctorId " +
      "AND LOWER(p.name) LIKE LOWER(CONCAT('%', :patientName, '%')) " +
      "AND a.appointmentTime BETWEEN :start AND :end")
  List<Appointment> findByDoctorIdAndPatient_NameContainingIgnoreCaseAndAppointmentTimeBetween(
      @Param("doctorId") Long doctorId,
      @Param("patientName") String patientName,
      @Param("start") LocalDateTime start,
      @Param("end") LocalDateTime end);

  @Modifying
  @Transactional
  @Query("DELETE FROM Appointment a WHERE a.doctor.id = :doctorId")
  void deleteAllByDoctorId(@Param("doctorId") Long doctorId);

  // - **findByPatientId**:
  // - This method retrieves all appointments for a specific patient.
  // - Return type: List<Appointment>
  // - Parameters: Long patientId
  @Query("SELECT a FROM Appointment a WHERE a.patient.id = :patientId")
  List<Appointment> findByPatientId(@Param("patientId") Long patientId);

  // - **findByPatient_IdAndStatusOrderByAppointmentTimeAsc**:
  // - This method retrieves all appointments for a specific patient with a given
  // status, ordered by the appointment time.
  // - Return type: List<Appointment>
  // - Parameters: Long patientId, int status
  @Query("SELECT a FROM Appointment a WHERE a.patient.id = :patientId AND a.status = :status ORDER BY a.appointmentTime ASC")
  List<Appointment> findByPatient_IdAndStatusOrderByAppointmentTimeAsc(
      @Param("patientId") Long patientId,
      @Param("status") int status);

  // - **filterByDoctorNameAndPatientId**:
  // - This method retrieves appointments based on a doctor’s name (using a LIKE
  // query) and the patient’s ID.
  // - Return type: List<Appointment>
  // - Parameters: String doctorName, Long patientId
  @Query("SELECT a FROM Appointment a WHERE LOWER(a.doctor.name) LIKE LOWER(CONCAT('%', :doctorName, '%')) AND a.patient.id = :patientId")
  List<Appointment> filterByDoctorNameAndPatientId(
      @Param("doctorName") String doctorName,
      @Param("patientId") Long patientId);

  // - **filterByDoctorNameAndPatientIdAndStatus**:
  // - This method retrieves appointments based on a doctor’s name (using a LIKE
  // query), patient’s ID, and a specific appointment status.
  //
  // - Return type: List<Appointment>
  // - Parameters: String doctorName, Long patientId, int status
  @Query("SELECT a FROM Appointment a WHERE LOWER(a.doctor.name) LIKE LOWER(CONCAT('%', :doctorName, '%')) AND a.patient.id = :patientId AND a.status = :status")
  List<Appointment> filterByDoctorNameAndPatientIdAndStatus(
      @Param("doctorName") String doctorName,
      @Param("patientId") Long patientId,
      @Param("status") int status);

  // - **updateStatus**:
  // - This method updates the status of a specific appointment based on its ID.
  // - Return type: void
  // - Parameters: int status, long id
  @Modifying
  @Transactional
  @Query("UPDATE Appointment a SET a.status = :status WHERE a.id = :id")
  void updateStatus(
      @Param("status") int status,
      @Param("id") long id);

}
