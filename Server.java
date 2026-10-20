import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Server {
    private static final List<Appointment> appointments = new ArrayList<>();
    private static int nextId = 1;

    private static final String[] DOCTORS = {
        "Dr. Sharma", "Dr. Patel", "Dr. Mehta"
    };
    private static final String[] TIMES = {
        "10:00 AM", "11:00 AM", "02:00 PM", "04:00 PM"
    };

    // Validation patterns: allows letters, spaces, hyphens, periods, straight apostrophe, and curly apostrophe
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z\\s'’\\.\\-]+$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern UNICODE_PATTERN = Pattern.compile("\\\\u([0-9a-fA-F]{4})");

    static class Appointment {
        int id;
        String patientName;
        String email;
        String doctor;
        String date;
        String time;
        String status = "Booked";

        Appointment(int id, String patientName, String email,
                    String doctor, String date, String time) {
            this.id = id;
            this.patientName = patientName;
            this.email = email;
            this.doctor = doctor;
            this.date = date;
            this.time = time;
        }

        String toJson() {
            return "{\"id\":" + id
                + ",\"patientName\":" + quote(patientName)
                + ",\"email\":" + quote(email)
                + ",\"doctor\":" + quote(doctor)
                + ",\"date\":" + quote(date)
                + ",\"time\":" + quote(time)
                + ",\"status\":" + quote(status) + "}";
        }
    }

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", Server::handleRequest);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.println("Carewell — Hospital Appointment Manager is running.");
        System.out.println("Open http://localhost:8080 in your browser.");
        System.out.println("Press Ctrl+C to stop the server.");
    }

    private static void handleRequest(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        if ("OPTIONS".equalsIgnoreCase(method)) {
            send(exchange, 204, "");
            return;
        }

        try {
            if ("/".equals(path) && "GET".equalsIgnoreCase(method)) {
                Path htmlPath = Path.of("index.html");
                if (!Files.exists(htmlPath)) {
                    sendJson(exchange, 404, "{\"message\":\"index.html not found. Run Server from the project folder.\"}");
                    return;
                }
                byte[] html = Files.readAllBytes(htmlPath);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, html.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(html);
                }
                return;
            }

            if ("/api/appointments".equals(path)
                    && "GET".equalsIgnoreCase(method)) {
                sendJson(exchange, 200, appointmentsToJson());
                return;
            }

            if ("/api/appointments".equals(path)
                    && "POST".equalsIgnoreCase(method)) {
                String body = readBody(exchange);
                String rawName = getJsonString(body, "patientName");
                String rawEmail = getJsonString(body, "email");
                String rawDoctor = getJsonString(body, "doctor");
                String rawDate = getJsonString(body, "date");
                String rawTime = getJsonString(body, "time");

                // 1. Patient Name Validation
                String nameError = validatePatientName(rawName);
                if (nameError != null) {
                    sendMessage(exchange, 400, nameError);
                    return;
                }

                // 2. Email Validation
                String emailError = validateEmail(rawEmail);
                if (emailError != null) {
                    sendMessage(exchange, 400, emailError);
                    return;
                }

                // 3. Doctor Validation
                String doctorError = validateDoctor(rawDoctor);
                if (doctorError != null) {
                    sendMessage(exchange, 400, doctorError);
                    return;
                }

                // 4. Date Validation
                String dateError = validateDate(rawDate);
                if (dateError != null) {
                    sendMessage(exchange, 400, dateError);
                    return;
                }

                // 5. Time Validation
                String timeError = validateTime(rawTime);
                if (timeError != null) {
                    sendMessage(exchange, 400, timeError);
                    return;
                }

                String patientName = rawName.trim();
                String email = rawEmail.trim();
                String doctor = rawDoctor.trim();
                String date = rawDate.trim();
                String time = rawTime.trim();

                // Slot Availability (Prevent duplicate active bookings)
                if (isSlotTaken(doctor, date, time, -1)) {
                    sendMessage(exchange, 409, "This doctor already has an active appointment at that date and time. Please choose another slot.");
                    return;
                }

                Appointment appointment = new Appointment(
                    nextId++, patientName, email, doctor, date, time
                );
                appointments.add(appointment);
                sendMessage(exchange, 201, "Appointment booked successfully.");
                return;
            }

            Matcher matcher = Pattern.compile("^/api/appointments/(\\d+)$").matcher(path);
            if (matcher.matches()) {
                int id = Integer.parseInt(matcher.group(1));
                Appointment appointment = findAppointment(id);

                if (appointment == null) {
                    sendMessage(exchange, 404, "Appointment not found.");
                    return;
                }

                if ("DELETE".equalsIgnoreCase(method)) {
                    if ("Cancelled".equals(appointment.status)) {
                        sendMessage(exchange, 400, "This appointment is already cancelled.");
                        return;
                    }
                    appointment.status = "Cancelled";
                    sendMessage(exchange, 200, "Appointment cancelled successfully.");
                    return;
                }

                if ("PUT".equalsIgnoreCase(method)) {
                    if (!"Booked".equals(appointment.status)) {
                        sendMessage(exchange, 400, "Only active booked appointments can be rescheduled.");
                        return;
                    }

                    String body = readBody(exchange);
                    String rawDate = getJsonString(body, "date");
                    String rawTime = getJsonString(body, "time");

                    // Validate Reschedule Date
                    String dateError = validateDate(rawDate);
                    if (dateError != null) {
                        sendMessage(exchange, 400, dateError);
                        return;
                    }

                    // Validate Reschedule Time
                    String timeError = validateTime(rawTime);
                    if (timeError != null) {
                        sendMessage(exchange, 400, timeError);
                        return;
                    }

                    String date = rawDate.trim();
                    String time = rawTime.trim();

                    // Check slot conflict excluding current appointment
                    if (isSlotTaken(appointment.doctor, date, time, appointment.id)) {
                        sendMessage(exchange, 409, "This doctor already has an active appointment at that date and time. Please choose another slot.");
                        return;
                    }

                    appointment.date = date;
                    appointment.time = time;
                    sendMessage(exchange, 200, "Appointment rescheduled successfully.");
                    return;
                }
            }

            sendMessage(exchange, 404, "Page or API endpoint not found.");
        } catch (DateTimeParseException ex) {
            sendMessage(exchange, 400, "Please enter a valid calendar date in YYYY-MM-DD format.");
        } catch (Exception ex) {
            sendMessage(exchange, 500, "Something went wrong while processing the request.");
        }
    }

    // Validation Methods
    private static String validatePatientName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "Patient name is required and cannot be empty.";
        }
        String trimmed = name.trim();
        if (trimmed.length() < 2 || trimmed.length() > 80) {
            return "Patient name must be between 2 and 80 characters long.";
        }
        if (!NAME_PATTERN.matcher(trimmed).matches()) {
            return "Patient name may only contain letters, spaces, hyphens, periods, and apostrophes.";
        }
        return null;
    }

    private static String validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return "Email address is required.";
        }
        String trimmed = email.trim();
        if (trimmed.length() > 100) {
            return "Email address must not exceed 100 characters.";
        }
        if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
            return "Please enter a valid email address with a domain suffix (e.g., patient@gmail.com).";
        }
        return null;
    }

    private static String validateDoctor(String doctor) {
        if (doctor == null || doctor.trim().isEmpty()) {
            return "Please select a registered doctor.";
        }
        String trimmed = doctor.trim();
        if (!contains(DOCTORS, trimmed)) {
            return "Please select a registered doctor (Dr. Sharma, Dr. Patel, or Dr. Mehta).";
        }
        return null;
    }

    private static String validateDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return "Appointment date is required.";
        }
        String trimmed = dateStr.trim();
        if (!DATE_PATTERN.matcher(trimmed).matches()) {
            return "Please enter a valid date in YYYY-MM-DD format.";
        }
        try {
            LocalDate parsed = LocalDate.parse(trimmed);
            if (!parsed.toString().equals(trimmed)) {
                return "The specified calendar date is invalid.";
            }
            if (parsed.isBefore(LocalDate.now())) {
                return "Appointment date cannot be in the past. Please select today or a future date.";
            }
        } catch (DateTimeParseException ex) {
            return "Please enter a valid calendar date in YYYY-MM-DD format.";
        }
        return null;
    }

    private static String validateTime(String time) {
        if (time == null || time.trim().isEmpty()) {
            return "Appointment time is required.";
        }
        String trimmed = time.trim();
        if (!contains(TIMES, trimmed)) {
            return "Please select a valid appointment time (10:00 AM, 11:00 AM, 02:00 PM, or 04:00 PM).";
        }
        return null;
    }

    // Availability check (Cancelled appointments do not block time slots)
    private static boolean isSlotTaken(String doctor, String date, String time, int ignoreId) {
        for (Appointment a : appointments) {
            if (a.id != ignoreId && "Booked".equals(a.status)
                    && a.doctor.equals(doctor)
                    && a.date.equals(date)
                    && a.time.equals(time)) {
                return true;
            }
        }
        return false;
    }

    private static Appointment findAppointment(int id) {
        for (Appointment a : appointments) {
            if (a.id == id) return a;
        }
        return null;
    }

    private static String appointmentsToJson() {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < appointments.size(); i++) {
            if (i > 0) json.append(",");
            json.append(appointments.get(i).toJson());
        }
        return json.append("]").toString();
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // JSON string extractor supporting unicode sequences and standard escapes
    private static String getJsonString(String json, String key) {
        Pattern pattern = Pattern.compile(
            "\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\""
        );
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) return "";
        String val = matcher.group(1)
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t");
        return decodeUnicode(val);
    }

    private static String decodeUnicode(String text) {
        Matcher matcher = UNICODE_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            int codePoint = Integer.parseInt(matcher.group(1), 16);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf((char) codePoint)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String quote(String value) {
        if (value == null) return "\"\"";
        return "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t") + "\"";
    }

    private static boolean contains(String[] values, String target) {
        if (target == null) return false;
        for (String value : values) {
            if (value.equals(target)) return true;
        }
        return false;
    }

    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static void sendMessage(HttpExchange exchange, int status, String message)
            throws IOException {
        sendJson(exchange, status, "{\"message\":" + quote(message) + "}");
    }

    private static void sendJson(HttpExchange exchange, int status, String json)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        send(exchange, status, json);
    }

    private static void send(HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (status == 204) {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
