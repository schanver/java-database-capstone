// doctorCard.js
import { deleteDoctor } from "../services/doctorServices.js";
import { getPatientData } from "../services/patientServices.js";

export function createDoctorCard(doctor) {
  const card = document.createElement("div");
  card.classList.add("doctor-card");

  const role = localStorage.getItem("userRole");

  const infoDiv = document.createElement("div");
  infoDiv.classList.add("doctor-info");

  const name = document.createElement("h3");
  name.textContent = doctor.name;

  const specialization = document.createElement("p");
  specialization.textContent = `Specialty: ${doctor.specialty}`;

  const email = document.createElement("p");
  email.textContent = `Email: ${doctor.email}`;

  const availability = document.createElement("p");
  availability.textContent = `Available: ${(doctor.availableTimes || []).join(", ")}`;

  infoDiv.append(name, specialization, email, availability);

  const actionsDiv = document.createElement("div");
  actionsDiv.classList.add("card-actions");

  if (role === "admin") {
    const removeBtn = document.createElement("button");
    removeBtn.textContent = "Delete";
    removeBtn.addEventListener("click", async () => {
      if (!confirm(`Delete Dr. ${doctor.name}?`)) return;
      const token = localStorage.getItem("token");
      const { success, message } = await deleteDoctor(doctor.id, token);
      alert(message);
      if (success) card.remove();
    });
    actionsDiv.appendChild(removeBtn);
  } else if (role === "patient") {
    const bookNow = document.createElement("button");
    bookNow.textContent = "Book Now";
    bookNow.addEventListener("click", () => {
      alert("Please log in to book an appointment.");
    });
    actionsDiv.appendChild(bookNow);
  } else if (role === "loggedPatient") {
    const bookNow = document.createElement("button");
    bookNow.textContent = "Book Now";
    bookNow.addEventListener("click", async (e) => {
      const token = localStorage.getItem("token");
      if (!token) {
        window.location.href = "/pages/patientDashboard.html";
        return;
      }
      const patient = await getPatientData(token);
      if (!patient) {
        alert("Could not load your details. Please log in again.");
        return;
      }
      const { showBookingOverlay } = await import("../loggedPatient.js");
      showBookingOverlay(e, doctor, patient);
    });
    actionsDiv.appendChild(bookNow);
  }

  card.append(infoDiv, actionsDiv);
  return card;
}
