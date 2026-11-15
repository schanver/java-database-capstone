package com.project.back_end.repo;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.project.back_end.models.Prescription;

@Repository
public interface PrescriptionRepository extends MongoRepository<Prescription, String> {

  // 2. Custom Query Method:

  // - **findByAppointmentId**:
  // - This method retrieves a list of prescriptions associated with a specific
  // appointment.
  // - Return type: List<Prescription>
  // - Parameters: Long appointmentId
  // - MongoRepository automatically derives the query from the method name, in
  // this case, it will find prescriptions by the appointment ID.

  List<Prescription> findByAppointmentId(Long appointmentId);

}
