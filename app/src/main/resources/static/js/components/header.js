// header.js

function renderHeader() {
  const headerDiv = document.getElementById("header");
  if (!headerDiv) return;

  const logoSection = `
    <div class="logo-section">
      <img src="/assets/images/logo/logo.png" alt="Hospital CRM Logo" class="logo-img">
      <span class="logo-title">Hospital CMS</span>
    </div>`;

  // Root page: clear any session and show only the logo
  if (window.location.pathname.endsWith("/")) {
    localStorage.removeItem("userRole");
    localStorage.removeItem("token");
    headerDiv.innerHTML = `<header class="header">${logoSection}</header>`;
    return;
  }

  const role = localStorage.getItem("userRole");
  const token = localStorage.getItem("token");

  // Roles that require a login but have no token: session is invalid
  if ((role === "loggedPatient" || role === "admin" || role === "doctor") && !token) {
    localStorage.removeItem("userRole");
    alert("Session expired or invalid login. Please log in again.");
    window.location.href = "/";
    return;
  }

  let headerContent = `<header class="header">${logoSection}<nav>`;

  if (role === "admin") {
    headerContent += `
      <button id="addDocBtn" class="adminBtn" onclick="openModal('addDoctor')">Add Doctor</button>
      <a href="#" onclick="logout()">Logout</a>`;
  } else if (role === "doctor") {
    headerContent += `
      <button class="adminBtn" onclick="selectRole('doctor')">Home</button>
      <a href="#" onclick="logout()">Logout</a>`;
  } else if (role === "patient") {
    headerContent += `
      <button id="patientLogin" class="adminBtn">Login</button>
      <button id="patientSignup" class="adminBtn">Sign Up</button>`;
  } else if (role === "loggedPatient") {
    headerContent += `
      <button id="home" class="adminBtn" onclick="window.location.href='/pages/loggedPatientDashboard.html'">Home</button>
      <button id="patientAppointments" class="adminBtn" onclick="window.location.href='/pages/patientAppointments.html'">Appointments</button>
      <a href="#" onclick="logoutPatient()">Logout</a>`;
  }

  headerContent += `</nav></header>`;

  headerDiv.innerHTML = headerContent;
  attachHeaderButtonListeners();
}

// Buttons are created dynamically, so listeners are attached after insertion.
// Pages that import openModal as a module (e.g. patientDashboard.js) attach their
// own listeners; here we only wire buttons when openModal is globally available.
function attachHeaderButtonListeners() {
  if (typeof window.openModal !== "function") return;

  const modalButtons = {
    patientLogin: "patientLogin",
    patientSignup: "patientSignup",
    adminLogin: "adminLogin",
    doctorLogin: "doctorLogin",
  };

  for (const [id, modalType] of Object.entries(modalButtons)) {
    const btn = document.getElementById(id);
    if (btn) {
      btn.addEventListener("click", () => window.openModal(modalType));
    }
  }
}

function logout() {
  localStorage.removeItem("token");
  localStorage.removeItem("userRole");
  window.location.href = "/";
}

function logoutPatient() {
  localStorage.removeItem("token");
  localStorage.setItem("userRole", "patient");
  window.location.href = "/pages/patientDashboard.html";
}

renderHeader();
