# Carewell — Hospital Appointment Manager

A web-based Hospital Appointment Management application built using HTML, Vanilla CSS, JavaScript, and a Java HTTP backend server.

---

## 🌟 Overview & Key Features

- **Editorial Clinic UI/UX**: Designed with a warm ivory and forest green palette, refined serif typography, and a modern clinic reception aesthetic.
- **Reservation Workflow**: Step-by-step reservation process with real-time field validation for patient details, doctor selection, and scheduling.
- **Consulting Physicians**: Interactive availability directory for:
  - **Dr. Sharma** — Cardiologist
  - **Dr. Patel** — Dentist
  - **Dr. Mehta** — Dermatologist
- **Live Slot Strikethrough**: Reflects booked time slots in real time for any selected date.
- **Chronological History Timeline**: View consultations in a vertical timeline journal with status indicators and search/filter controls.
- **Appointment Actions**: Effortlessly cancel or reschedule consultations with instant conflict checks.
- **Robust End-to-End Validation**: Strict verification of patient names (supports hyphens and apostrophes), email formats, future dates, valid doctors, and prevention of double bookings.

---

## 🛠️ Technology Stack

- **Frontend**: HTML5, Semantic CSS3 (CSS Variables, Flexbox, Grid), Vanilla JavaScript (ES6+).
- **Backend**: Java (`com.sun.net.httpserver.HttpServer`).
- **Storage**: In-memory thread-safe `ArrayList<Appointment>` (no third-party databases or heavy frameworks required).

---

## 🚀 How to Run Locally

### Prerequisites
- JDK 11 or newer (JDK 17, 21, or 25 recommended)

### Steps
1. Clone the repository and navigate to the project directory:
   ```bash
   git clone https://github.com/dev-divyanshushah/IP-Final-Project.git
   cd IP-Final-Project
   ```

2. Compile the Java server:
   ```bash
   javac Server.java
   ```

3. Start the server:
   ```bash
   java Server
   ```

4. Open your browser and navigate to:
   ```
   http://localhost:8080
   ```

---

## 📡 API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/` | Serves the web interface (`index.html`) |
| `GET` | `/api/appointments` | Fetches all recorded appointments as JSON |
| `POST` | `/api/appointments` | Books a new appointment with validation and conflict prevention |
| `PUT` | `/api/appointments/{id}` | Reschedules an existing booked appointment |
| `DELETE` | `/api/appointments/{id}` | Cancels an appointment (frees up the time slot) |

---

## 📝 Notes
- Appointment records are maintained in-memory and reset when the Java server is terminated.
- Designed as a clean educational industry practice project for healthcare management systems.
