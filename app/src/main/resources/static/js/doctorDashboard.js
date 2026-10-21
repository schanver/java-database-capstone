import { getAllAppointments } from "./services/appointmentRecordService.js";
import { createPatientRow } from "./components/patientRows.js";

const tableBody = document.getElementById("patientTableBody");
const datePicker = document.getElementById("datePicker");
let selectedDate = new Date().toLocaleDateString("en-CA");
const token = localStorage.getItem("token");
let patientName = "null";

datePicker.value = selectedDate;

document.getElementById("searchBar").addEventListener("input", (event) => {
  const value = event.target.value.trim();
  patientName = value !== "" ? value : "null";
  loadAppointments();
});

document.getElementById("todayButton").addEventListener("click", () => {
  selectedDate = new Date().toLocaleDateString("en-CA");
  datePicker.value = selectedDate;
  loadAppointments();
});

datePicker.addEventListener("change", (event) => {
  selectedDate = event.target.value;
  loadAppointments();
});

function toPatient(appointment) {
  if (appointment.patient) {
    const { id, name, phone, email } = appointment.patient;
    return { id, name, phone, email };
  }
  return {
    id: appointment.patientId,
    name: appointment.patientName,
    phone: appointment.patientPhone,
    email: appointment.patientEmail,
  };
}

function showMessageRow(message) {
  tableBody.innerHTML = `<tr><td colspan="5" class="noPatientRecord">${message}</td></tr>`;
}

async function loadAppointments() {
  try {
    const data = await getAllAppointments(
      selectedDate,
      encodeURIComponent(patientName),
      token,
    );
    const appointments = data.appointments || [];
    tableBody.innerHTML = "";

    if (appointments.length === 0) {
      showMessageRow("No Appointments found for today.");
      return;
    }

    appointments.forEach((appointment) => {
      const doctorId = appointment.doctor
        ? appointment.doctor.id
        : appointment.doctorId;
      const row = createPatientRow(
        toPatient(appointment),
        appointment.id,
        doctorId,
      );
      tableBody.appendChild(row);
    });
  } catch (error) {
    console.error("Error loading appointments:", error);
    showMessageRow("Error loading appointments. Try again later.");
  }
}

document.addEventListener("DOMContentLoaded", () => {
  loadAppointments();
});
