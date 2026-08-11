# Schema Design — Smart Clinic Management System

The clinic's data splits cleanly along one line: anything that is **operational, relational, and
constrained** goes in MySQL; anything that is **narrative, optional, or likely to change shape** goes in
MongoDB. Patients, doctors, appointments, and admins all have fixed fields, are queried across joins,
and need real integrity guarantees, so they are relational. Prescriptions are mostly free text plus
whatever metadata a pharmacy or regulator wants next year, so they are documents.

---

## MySQL Database Design

### Table: `patients`

- `id`: BIGINT, Primary Key, Auto Increment
- `name`: VARCHAR(100), Not Null — validated at 3–100 characters
- `email`: VARCHAR(255), Not Null, Unique — doubles as the login identity
- `password`: VARCHAR(255), Not Null — minimum 6 characters, never returned by the API
- `phone`: VARCHAR(10), Not Null, Unique — exactly 10 digits
- `address`: VARCHAR(255), Not Null

**Notes.** Email and phone are both unique because registration rejects a patient matching either one;
enforcing this only in application code would leave the door open to a race between two simultaneous
signups. `password` is sized at 255 to leave room for a bcrypt hash — the current implementation stores
it in plain text, which is a known shortcoming rather than a design decision.

### Table: `doctors`

- `id`: BIGINT, Primary Key, Auto Increment
- `name`: VARCHAR(100), Not Null — validated at 3–100 characters
- `specialty`: VARCHAR(50), Not Null — validated at 3–50 characters
- `email`: VARCHAR(255), Not Null, Unique — login identity
- `password`: VARCHAR(255), Not Null — minimum 6 characters
- `phone`: VARCHAR(10), Not Null — exactly 10 digits

### Table: `doctor_available_times`

A doctor has many time slots, so they cannot live in a column on `doctors`. Mapped with
`@ElementCollection`, which produces a side table of simple values rather than a full entity:

- `doctor_id`: BIGINT, Foreign Key → `doctors(id)`, Not Null
- `available_times`: VARCHAR(20), Not Null — a slot string such as `"09:00-10:00"`

**Notes.** Storing slots as strings is the course's approach and is worth naming as a compromise: it
cannot be range-queried in SQL, cannot express recurrence, and puts parsing in application code. A
stricter design would store `day_of_week`, `start_time` (TIME), and `end_time` (TIME) as real columns.

### Table: `appointments`

- `id`: BIGINT, Primary Key, Auto Increment
- `doctor_id`: BIGINT, Foreign Key → `doctors(id)`, Not Null
- `patient_id`: BIGINT, Foreign Key → `patients(id)`, Not Null
- `appointment_time`: DATETIME, Not Null — start time; must be in the future at creation
- `status`: INT, Not Null — `0` = Scheduled, `1` = Completed

**Notes.** The end time is **not stored**. Every appointment is one hour, so the end is derived as
`appointment_time + 1 hour`. This keeps the two values from ever disagreeing, at the cost of hard-coding
the duration — if variable-length appointments are ever needed, a `duration_minutes` column is the
migration.

Two design questions the lab poses, answered explicitly:

- *If a patient is deleted, should their appointments be deleted?* No. Appointment history is a medical
  record and deleting it destroys the clinic's own audit trail. The foreign key should `RESTRICT`, and
  departing patients should be marked inactive rather than removed.
- *Should a doctor be allowed overlapping appointments?* No. Nothing in the table structure prevents it
  — a `UNIQUE (doctor_id, appointment_time)` constraint would, and is the right addition. As currently
  built the rule is enforced only in the service layer, which is a single missed code path away from
  double-booking.

### Table: `admin`

- `id`: BIGINT, Primary Key, Auto Increment
- `username`: VARCHAR(255), Not Null, Unique
- `password`: VARCHAR(255), Not Null — minimum 6 characters

**Notes.** Admins log in by username rather than email, unlike the other two roles. This inconsistency
is inherited from the specification; a single `users` table with a `role` column would model all three
roles more coherently and remove the three parallel login paths.

### Table: `clinic_locations` *(extension)*

Included as a forward-looking addition — a clinic with more than one site needs appointments to know
where they happen.

- `id`: BIGINT, Primary Key, Auto Increment
- `name`: VARCHAR(100), Not Null
- `address`: VARCHAR(255), Not Null
- `phone`: VARCHAR(10), Not Null

`appointments` would gain a nullable `location_id` foreign key referencing this table.

### Table: `payments` *(extension)*

- `id`: BIGINT, Primary Key, Auto Increment
- `appointment_id`: BIGINT, Foreign Key → `appointments(id)`, Not Null, Unique
- `amount`: DECIMAL(10,2), Not Null — decimal, never float, because money must not carry binary rounding error
- `currency`: CHAR(3), Not Null — ISO 4217
- `status`: VARCHAR(20), Not Null — `PENDING`, `PAID`, `REFUNDED`
- `paid_at`: DATETIME, Nullable — null until payment settles

---

## MongoDB Collection Design

### Collection: `prescriptions`

A prescription is a good fit for a document store: the required core is small, but the surrounding
detail (pharmacy, refills, attachments, structured dosage) is optional, nested, and changes over time.
Adding any of it to MySQL would mean a migration and a column that is null for most rows.

```json
{
  "_id": "ObjectId('64f8a2b1c3d4e5f6a7b8c9d0')",
  "patientName": "Ayşe Yılmaz",
  "appointmentId": 51,
  "medication": "Amoxicillin",
  "dosage": "500mg",
  "doctorNotes": "One capsule every 8 hours with food. Complete the full course.",
  "refillCount": 1,
  "issuedAt": "2026-08-11T10:15:00Z",
  "pharmacy": {
    "name": "Kadıköy Eczanesi",
    "address": "Bahariye Cd. 42, İstanbul",
    "phone": "2161234567"
  },
  "tags": ["antibiotic", "course-7-days"],
  "attachments": [
    {
      "fileName": "lab-results.pdf",
      "contentType": "application/pdf",
      "uploadedAt": "2026-08-11T10:20:00Z"
    }
  ]
}
```

**Why a reference and not an embedded object.** The document stores `appointmentId` as a bare number and
`patientName` as a string, rather than embedding the whole patient. Embedding would duplicate patient
data across every prescription and go stale the moment someone changes their phone number. The
denormalised `patientName` is a deliberate exception: it is a snapshot of the name *at the time of
issue*, which is what a medical record should show even if the patient later changes it.

**The cost of the split.** There is no foreign key from `appointmentId` to `appointments(id)` — the two
live in different databases and MongoDB cannot enforce the reference. Nothing prevents a prescription
pointing at an appointment that never existed or was deleted. That check has to happen in the service
layer before a prescription is saved, and orphan cleanup has to be an explicit job. This is the honest
price of the dual-store design.

**Schema evolution.** Because documents are schemaless, older prescriptions simply lack `tags`,
`pharmacy`, or `attachments`, and reading code must treat every optional field as possibly absent
rather than assuming a shape. Adding a field requires no migration; *removing or renaming* one still
requires a backfill, so the flexibility is real but not unlimited.

### Collection: `messages` *(extension)*

Patient–doctor messaging is another natural document fit — a thread is a nested, append-only structure
with no fixed size.

```json
{
  "_id": "ObjectId('64f8a2b1c3d4e5f6a7b8c9d1')",
  "appointmentId": 51,
  "participants": { "patientId": 12, "doctorId": 7 },
  "messages": [
    {
      "sender": "patient",
      "body": "Should I keep taking the medication if the rash clears up?",
      "sentAt": "2026-08-12T09:02:00Z",
      "readAt": "2026-08-12T09:40:00Z"
    },
    {
      "sender": "doctor",
      "body": "Yes, please finish the full course.",
      "sentAt": "2026-08-12T09:41:00Z",
      "readAt": null
    }
  ]
}
```

**Caveat on unbounded arrays.** Embedding messages in the thread document is convenient because a whole
conversation is one read, but MongoDB documents cap at 16 MB and an ever-growing array eventually
forces the document to be rewritten on every append. For a busy thread the better shape is one document
per message with a shared `threadId`.
