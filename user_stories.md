# User Stories — Smart Clinic Management System

Stories are grouped by the three roles the system serves: **Admin**, **Patient**, and **Doctor**.
Each follows the format *As a [role], I want [goal], so that [benefit]*, with acceptance criteria,
priority, and an effort estimate in story points.

---

## Admin User Stories

### Admin Story 1 — Log in to the portal

**Title:**
_As an admin, I want to log into the portal with my username and password, so that I can manage the
platform securely._

**Acceptance Criteria:**
1. The login form accepts a username and a password and submits them to the admin login endpoint.
2. On valid credentials the system returns a signed JWT, which the frontend stores and uses for
   subsequent requests.
3. On invalid credentials the system returns `401` with a clear message and the user stays on the
   login screen.

**Priority:** High
**Story Points:** 3
**Notes:**
- The token expires after seven days, after which the admin must log in again.
- The password must never appear in any API response.

### Admin Story 2 — Log out of the portal

**Title:**
_As an admin, I want to log out of the portal, so that my session cannot be reused by anyone else on a
shared machine._

**Acceptance Criteria:**
1. A logout control is visible on every admin dashboard page.
2. Selecting it clears the stored token and role from the browser.
3. Any attempt to revisit a dashboard URL afterwards redirects to the role-selection page.

**Priority:** High
**Story Points:** 2
**Notes:**
- Because tokens are stateless, logout is client-side only; the token remains technically valid until
  it expires. Server-side revocation would need a token blacklist.

### Admin Story 3 — Add a doctor to the system

**Title:**
_As an admin, I want to add a new doctor profile, so that patients can find and book appointments with
them._

**Acceptance Criteria:**
1. The form captures name, specialty, email, password, phone number, and available time slots.
2. Validation rejects a malformed email, a phone number that is not exactly ten digits, and a password
   shorter than six characters, showing the specific reason.
3. A doctor saved successfully appears immediately in the doctor list without a page reload.

**Priority:** High
**Story Points:** 5
**Notes:**
- Two doctors must not be able to share an email address, since email is the login identity.

### Admin Story 4 — Delete a doctor profile

**Title:**
_As an admin, I want to delete a doctor's profile, so that staff who have left the clinic can no longer
be booked._

**Acceptance Criteria:**
1. Deletion requires a confirmation step before anything is removed.
2. The request is rejected with `401` unless it carries a valid admin token.
3. The doctor disappears from the bookable list immediately after deletion.

**Priority:** Medium
**Story Points:** 3
**Notes:**
- Open question resolved during design: existing appointments are **not** silently deleted, because
  they are part of the clinic's medical record. Deletion should be blocked, or the doctor deactivated
  instead, when future appointments exist.

### Admin Story 5 — View appointment volume per month

**Title:**
_As an admin, I want to run a report of appointments per month, so that I can see how busy the clinic
is and plan staffing._

**Acceptance Criteria:**
1. A stored procedure returns the number of appointments grouped by month.
2. The result shows the month and the corresponding total.
3. Months with no appointments are reported as zero rather than omitted.

**Priority:** Low
**Story Points:** 3
**Notes:**
- Implemented as a MySQL stored procedure rather than application code, to keep the aggregation next to
  the data.

### Admin Story 6 — View appointment volume per doctor

**Title:**
_As an admin, I want to see how many appointments each doctor has handled, so that I can spot uneven
workload distribution._

**Acceptance Criteria:**
1. A stored procedure returns appointment counts grouped by doctor.
2. Each row identifies the doctor by name, not only by id.
3. Only completed appointments are counted, so cancelled slots do not inflate the numbers.

**Priority:** Low
**Story Points:** 3

---

## Patient User Stories

### Patient Story 1 — Browse doctors without signing up

**Title:**
_As a patient, I want to view the list of doctors without logging in, so that I can decide whether this
clinic has the specialist I need before creating an account._

**Acceptance Criteria:**
1. The doctor list is reachable from the landing page with no token required.
2. Each doctor card shows name, specialty, and available time slots.
3. Attempting to book from this view prompts the visitor to log in or sign up first.

**Priority:** High
**Story Points:** 3
**Notes:**
- Doctor email addresses and phone numbers must not be exposed on the public view.

### Patient Story 2 — Register an account

**Title:**
_As a patient, I want to sign up with my email and password, so that I can book appointments._

**Acceptance Criteria:**
1. Registration captures name, email, password, phone number, and address.
2. Signing up with an email or phone number already on file is rejected with an explanatory message.
3. A successful registration returns a token so the patient is logged in immediately.

**Priority:** High
**Story Points:** 5

### Patient Story 3 — Log in to manage bookings

**Title:**
_As a patient, I want to log into the portal, so that I can manage my bookings in one place._

**Acceptance Criteria:**
1. Login accepts email and password and returns a token on success.
2. Wrong credentials produce `401` and a generic failure message that does not reveal whether the email
   exists.
3. The token is stored and attached to every subsequent request.

**Priority:** High
**Story Points:** 3

### Patient Story 4 — Filter doctors to find a match

**Title:**
_As a patient, I want to filter doctors by name, specialty, and time of day, so that I can quickly find
one who fits my schedule._

**Acceptance Criteria:**
1. Any combination of the three filters can be applied together.
2. Applying no filter returns the full list of doctors.
3. A search matching nothing shows an explicit empty-state message rather than a blank page.

**Priority:** Medium
**Story Points:** 5
**Notes:**
- Name matching should be case-insensitive and match on partial input.

### Patient Story 5 — Book an appointment

**Title:**
_As a patient, I want to book an hour-long appointment with a chosen doctor, so that I can receive a
consultation._

**Acceptance Criteria:**
1. Only slots the doctor has actually published as available can be selected.
2. Booking a slot that has already been taken is rejected, and the list of free slots refreshes.
3. A confirmed appointment is created with status `0` (Scheduled) and appears on the patient's
   dashboard.

**Priority:** High
**Story Points:** 8
**Notes:**
- Appointments are fixed at one hour, so the end time is derived rather than stored.
- The requested time must be in the future.

### Patient Story 6 — Review upcoming appointments

**Title:**
_As a patient, I want to see my upcoming appointments, so that I can prepare for them or rearrange my
day._

**Acceptance Criteria:**
1. The dashboard lists future appointments with doctor name, date, and time.
2. Appointments are sorted soonest first.
3. A patient can only ever see their own appointments, enforced from the token rather than a
   client-supplied id.

**Priority:** High
**Story Points:** 3

### Patient Story 7 — Review past appointments and prescriptions

**Title:**
_As a patient, I want to look back at completed appointments and the prescriptions issued, so that I
have a record of my treatment._

**Acceptance Criteria:**
1. Completed appointments (status `1`) are shown in a separate section from upcoming ones.
2. Where a prescription exists for an appointment, its medication, dosage, and doctor's notes are shown.
3. Appointments with no prescription are displayed without an error.

**Priority:** Medium
**Story Points:** 5

---

## Doctor User Stories

### Doctor Story 1 — Log in to the dashboard

**Title:**
_As a doctor, I want to log into the portal, so that I can manage my appointments._

**Acceptance Criteria:**
1. Login accepts email and password and returns a token scoped to the doctor role.
2. A patient's or admin's token is rejected on doctor-only routes even though it is otherwise valid.
3. Successful login lands on the doctor dashboard.

**Priority:** High
**Story Points:** 3
**Notes:**
- Role is verified by looking the token's email up in the doctor repository specifically, so a valid
  token for one role cannot be replayed against another.

### Doctor Story 2 — See the daily appointment calendar

**Title:**
_As a doctor, I want to view my appointments for a given day, so that I know who I am seeing and when._

**Acceptance Criteria:**
1. The dashboard defaults to today and allows picking any other date.
2. Each entry shows the patient's name, the time, and the current status.
3. A day with no appointments shows an explicit empty state.

**Priority:** High
**Story Points:** 5

### Doctor Story 3 — Filter the day's list by patient name

**Title:**
_As a doctor, I want to search my appointment list by patient name, so that I can find a specific
booking without scrolling._

**Acceptance Criteria:**
1. Search is case-insensitive and matches partial names.
2. The search applies within the selected date only.
3. Clearing the search restores the full list for that day.

**Priority:** Low
**Story Points:** 3

### Doctor Story 4 — Manage availability

**Title:**
_As a doctor, I want to set and update my available time slots, so that patients can only book hours I
actually work._

**Acceptance Criteria:**
1. Time slots can be added and removed from the doctor's profile.
2. Slots already booked cannot be removed without addressing the existing appointment.
3. Updated availability is reflected in patient-facing search results immediately.

**Priority:** High
**Story Points:** 5

### Doctor Story 5 — Write a prescription for an appointment

**Title:**
_As a doctor, I want to record a prescription against a completed appointment, so that the patient has
a clear record of their medication._

**Acceptance Criteria:**
1. The prescription form captures patient name, medication, dosage, and doctor's notes.
2. The saved prescription is linked to the originating appointment id.
3. The prescription becomes visible to that patient, and to no other patient.

**Priority:** High
**Story Points:** 5
**Notes:**
- Stored in MongoDB rather than MySQL so that optional fields (pharmacy details, refill counts,
  attachments) can be added later without a schema migration.

### Doctor Story 6 — Mark an appointment as completed

**Title:**
_As a doctor, I want to mark an appointment as completed once the consultation is over, so that my
upcoming list reflects only work still to do._

**Acceptance Criteria:**
1. Changing status from `0` to `1` moves the appointment out of the upcoming list.
2. Only the doctor assigned to that appointment can change its status.
3. The change is visible on the patient's history view.

**Priority:** Medium
**Story Points:** 3
