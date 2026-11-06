import { openModal } from "./components/modals.js";
import {
  getDoctors,
  filterDoctors,
  saveDoctor,
} from "./services/doctorServices.js";
import { createDoctorCard } from "./components/doctorCard.js";

// header.js renders #addDocBtn before this module runs, but leaves wiring it up to the page.
const addDocBtn = document.getElementById("addDocBtn");
if (addDocBtn) {
  addDocBtn.addEventListener("click", () => {
    openModal("addDoctor");
  });
}

document.addEventListener("DOMContentLoaded", () => {
  loadDoctorCards();
});

async function loadDoctorCards() {
  try {
    const doctors = await getDoctors();
    renderDoctorCards(doctors);
  } catch (error) {
    console.error("Error loading doctors:", error);
  }
}

document
  .getElementById("searchBar")
  .addEventListener("input", filterDoctorsOnChange);
document
  .getElementById("filterTime")
  .addEventListener("change", filterDoctorsOnChange);
document
  .getElementById("filterSpecialty")
  .addEventListener("change", filterDoctorsOnChange);

async function filterDoctorsOnChange() {
  try {
    const name = document.getElementById("searchBar").value.trim() || null;
    const time = document.getElementById("filterTime").value || null;
    const specialty = document.getElementById("filterSpecialty").value || null;

    const response = await filterDoctors(name, time, specialty);
    const doctors = response.doctors || [];

    if (doctors.length > 0) {
      renderDoctorCards(doctors);
    } else {
      document.getElementById("content").innerHTML =
        "<p>No doctors found with the given filters.</p>";
    }
  } catch (error) {
    console.error("Error filtering doctors:", error);
    alert("Something went wrong while filtering doctors.");
  }
}

function renderDoctorCards(doctors) {
  const contentDiv = document.getElementById("content");
  contentDiv.innerHTML = "";
  doctors.forEach((doctor) => {
    contentDiv.appendChild(createDoctorCard(doctor));
  });
}

// Called by the Save button in the Add Doctor modal (see modals.js), so it must be global.
window.adminAddDoctor = async function () {
  const token = localStorage.getItem("token");
  if (!token) {
    alert("Your session has expired. Please log in again.");
    return;
  }

  const availableTimes = Array.from(
    document.querySelectorAll('input[name="availability"]:checked'),
  ).map((checkbox) => checkbox.value);

  // Field names match the Doctor model.
  const doctor = {
    name: document.getElementById("doctorName").value.trim(),
    specialty: document.getElementById("specialization").value,
    email: document.getElementById("doctorEmail").value.trim(),
    password: document.getElementById("doctorPassword").value,
    phone: document.getElementById("doctorPhone").value.trim(),
    availableTimes,
  };

  const { success, message } = await saveDoctor(doctor, token);
  if (success) {
    alert(message);
    document.getElementById("modal").style.display = "none";
    window.location.reload();
  } else {
    alert(message);
  }
};
/*
  This script handles the admin dashboard functionality for managing doctors:
  - Loads all doctor cards
  - Filters doctors by name, time, or specialty
  - Adds a new doctor via modal form


  Attach a click listener to the "Add Doctor" button
  When clicked, it opens a modal form using openModal('addDoctor')


  When the DOM is fully loaded:
    - Call loadDoctorCards() to fetch and display all doctors


  Function: loadDoctorCards
  Purpose: Fetch all doctors and display them as cards

    Call getDoctors() from the service layer
    Clear the current content area
    For each doctor returned:
    - Create a doctor card using createDoctorCard()
    - Append it to the content div

    Handle any fetch errors by logging them


  Attach 'input' and 'change' event listeners to the search bar and filter dropdowns
  On any input change, call filterDoctorsOnChange()


  Function: filterDoctorsOnChange
  Purpose: Filter doctors based on name, available time, and specialty

    Read values from the search bar and filters
    Normalize empty values to null
    Call filterDoctors(name, time, specialty) from the service

    If doctors are found:
    - Render them using createDoctorCard()
    If no doctors match the filter:
    - Show a message: "No doctors found with the given filters."

    Catch and display any errors with an alert


  Function: renderDoctorCards
  Purpose: A helper function to render a list of doctors passed to it

    Clear the content area
    Loop through the doctors and append each card to the content area


  Function: adminAddDoctor
  Purpose: Collect form data and add a new doctor to the system

    Collect input values from the modal form
    - Includes name, email, phone, password, specialty, and available times

    Retrieve the authentication token from localStorage
    - If no token is found, show an alert and stop execution

    Build a doctor object with the form values

    Call saveDoctor(doctor, token) from the service

    If save is successful:
    - Show a success message
    - Close the modal and reload the page

    If saving fails, show an error message
*/
