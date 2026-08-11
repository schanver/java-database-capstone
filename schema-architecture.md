# Smart Clinic Management System — Architecture Design

## Section 1: Architecture Summary

The Smart Clinic Management System is a single Spring Boot application that deliberately serves two
different kinds of client at once. The admin and doctor dashboards are server-rendered with Thymeleaf,
because those are internal, browser-only screens where there is no benefit to shipping state to the
client — the server already knows everything needed to draw the page. Every other module (appointments,
patient dashboard, patient records, prescriptions) is exposed as a REST API returning JSON, so that the
vanilla-JS frontend, and any future mobile or third-party client, can talk to the same backend over
plain HTTP. This is why `mvc/DashboardController` returns view names such as `admin/adminDashboard`
while the classes under `controllers/` are `@RestController`s that serialize objects.

The application persists to two databases, chosen per data shape rather than per module. MySQL, through
Spring Data JPA, holds the core operational entities — `Admin`, `Doctor`, `Patient`, and `Appointment`.
These are highly relational, benefit from foreign keys and uniqueness constraints, and are queried
across joins (for example, "all appointments for this doctor on this date"). MongoDB holds
`Prescription`, a `@Document` whose contents are free-form and expected to evolve: dosage notation,
doctor's notes, and eventually pharmacy metadata or attachments do not fit a fixed column set, and
adding a column to a relational table for every new optional field would be the wrong tool.

All controllers, MVC and REST alike, delegate to a shared service layer before any repository is
touched. A common `Service` class centralises cross-cutting concerns — token validation, login checks
for each role, and validation of whether a requested appointment slot is actually free — and composes
the per-entity services (`DoctorService`, `PatientService`, `AppointmentService`,
`PrescriptionService`) that hold entity-specific logic. Keeping this in one place means a controller
never decides for itself what counts as a valid token or a bookable slot.

Authentication is hand-rolled JWT rather than Spring Security. `TokenService` signs a token whose
subject is the user's email with a seven-day expiry, and validates a token *against a role* by
extracting the email and confirming it exists in the matching repository. Notably the token travels as
a **path variable** on nearly every endpoint, including the dashboard routes (`/adminDashboard/{token}`),
rather than in an `Authorization` header. Model binding closes the loop: rows fetched from MySQL are
mapped into `@Entity` classes, documents from MongoDB into `@Document` classes, and those objects are
then either handed to a Thymeleaf template as model attributes or serialized to JSON for the REST
response.

---

## Section 2: Numbered Flow of Data and Control

1. **A user reaches the application through one of two front doors.** Either they load a server-rendered
   Thymeleaf dashboard (`AdminDashboard`, `DoctorDashboard`), or they load a static page from
   `static/pages/` whose JavaScript then calls the REST API. Role selection happens first, on
   `index.html`, and the chosen role is stored in `localStorage` alongside the JWT.

2. **The request is routed to a controller based on URL path and HTTP method.** Requests for a rendered
   page land on `mvc/DashboardController`, a plain `@Controller` that returns a view *name*. Requests
   from JavaScript land on one of the `@RestController`s under `controllers/` — `AdminController`,
   `DoctorController`, `PatientController`, `AppointmentController`, `PrescriptionController` — all
   mounted beneath the configurable `${api.path}` prefix. Bean-validation failures on the request body
   never reach the controller body at all: `ValidationFailed`, a `@RestControllerAdvice`, intercepts
   them and returns `400` with a `{"message": ...}` payload.

3. **The controller delegates to the service layer rather than deciding anything itself.** For a
   token-protected route this means calling the shared `Service` to confirm the token is valid *for the
   claimed role* before any work happens; the MVC controller redirects to `/` when that check fails,
   while the REST controllers return `401`. Business rules live here too — checking that a doctor exists
   and that the requested hour is genuinely free before an appointment is allowed to be booked.

4. **The service layer calls the repository layer to reach data.** Five interfaces live side by side in
   `repo/`, but they are not the same kind of thing: `AdminRepository`, `DoctorRepository`,
   `PatientRepository`, and `AppointmentRepository` extend `JpaRepository` and are backed by MySQL,
   while `PrescriptionRepository` extends `MongoRepository` and is backed by MongoDB. Spring Data
   generates the implementations from the method signatures, so derived queries such as
   `findByDoctorIdAndAppointmentTimeBetween` need no hand-written SQL.

5. **Each repository talks to its own database engine.** MySQL holds the normalized `cms` schema, where
   Hibernate manages the tables directly (`ddl-auto=update`, so the Java models are the source of truth
   for the schema, not a migration script). MongoDB holds the `prescriptions` database, where documents
   are schemaless and can gain fields without a migration.

6. **Returned data is bound into Java model objects.** MySQL rows become JPA entities annotated
   `@Entity`, with `@ManyToOne` associations resolved into real object references — an `Appointment`
   carries an actual `Doctor` and `Patient`, not bare ids. Mongo documents become `@Document` classes
   with a `String` id. The one seam here is deliberate: `Prescription.appointmentId` is a plain `Long`
   with no mapping, because the appointment it names lives in a different database entirely.

7. **The bound models are used to produce a response.** In the MVC flow they are added as model
   attributes and rendered by Thymeleaf into complete HTML pages. In the REST flow they are serialized
   to JSON by Jackson and returned in the HTTP response body — sometimes as the entity directly,
   sometimes reshaped first into a DTO such as `AppointmentDTO` to flatten nested objects and drop
   fields the client has no business seeing. Either way the request–response cycle ends here.

---

## Design Notes and Trade-offs

A few consequences of this design are worth stating plainly, because they constrain later work:

- **There is no referential integrity between the two databases.** Nothing stops a `Prescription` from
  naming an `appointmentId` that does not exist, or from being orphaned when an appointment is deleted.
  This is the real price of the dual-store split and has to be enforced in the service layer or not at
  all.

- **The token in the URL path is a security weakness**, not a feature. Path segments are written to
  server access logs, kept in browser history, and leaked in `Referer` headers. A header-based scheme
  would avoid all three. The course specifies path variables, so that is what is implemented here.

- **Serializing JPA entities straight to JSON leaks schema decisions to the client** and risks exposing
  fields that were never meant to travel. Password fields are protected explicitly with
  `@JsonProperty(access = WRITE_ONLY)`, which works but is opt-out rather than opt-in — a DTO per
  resource would be the safer default.

- **`ddl-auto=update` is convenient for coursework and wrong for production.** It never drops or
  narrows a column, so the live schema silently drifts from the model over time. A real deployment
  would use versioned migrations (Flyway or Liquibase).
