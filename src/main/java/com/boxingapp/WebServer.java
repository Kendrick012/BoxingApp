package com.boxingapp;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Runs the BoxingPedia website on http://localhost:8080
 *
 * It does two jobs:
 *   1. Serves the website files (HTML, CSS, JS, pictures) from src/main/frontend/static
 *   2. Answers API requests with data from database/boxing.db:
 *        /api/divisions                       all weight classes, lightest first
 *        /api/boxers?division=Strawweight     boxers in a division, champion first
 *        /api/boxer?id=1                      one boxer
 *
 * When it starts, it runs DatabaseLoader first, so changes to your CSV files show up
 * every time you restart the server.
 *
 * Run it with the project's root folder (BoxingApp) as the working directory.
 */
public class WebServer {

    private static final int PORT = 8080;
    private static final Path WEBSITE_FOLDER = Path.of("src", "main", "frontend", "static");
    private static final Path DB_FILE = Path.of("database", "boxing.db");
    private static final String DB_URL = "jdbc:sqlite:database/boxing.db";

    // Every boxer query returns these columns (division name included, not just its id)
    private static final String BOXER_COLUMNS =
            "b.id, b.name, b.nickname, d.name AS division, b.rank, b.titles, b.nationality, "
            + "b.stance, b.age, b.height_cm, b.reach_cm, b.wins, b.losses, b.draws, b.kos, "
            + "b.ko_percentage, b.image_url "
            + "FROM boxers b LEFT JOIN divisions d ON d.id = b.division_id";

    public static void main(String[] args) throws IOException {
        // Rebuild the database from the CSV files so the site always shows your latest edits
        DatabaseLoader.main(args);

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/api/divisions", exchange -> handle(exchange, WebServer::divisions));
        server.createContext("/api/boxers", exchange -> handle(exchange, WebServer::boxersInDivision));
        server.createContext("/api/boxer", exchange -> handle(exchange, WebServer::oneBoxer));
        server.createContext("/", WebServer::serveFile);
        server.start();

        System.out.println("BoxingPedia is running! Open http://localhost:" + PORT + " in your browser.");
        System.out.println("Press the red Stop button in VS Code to shut it down.");
    }

    // ---------- API endpoints ----------

    /** /api/divisions */
    private static String divisions(Map<String, String> params) throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT id, name, weight_limit_lbs FROM divisions ORDER BY id")) {
            return toJsonArray(stmt.executeQuery());
        }
    }

    /** /api/boxers?division=Strawweight */
    private static String boxersInDivision(Map<String, String> params) throws SQLException {
        String division = params.get("division");
        if (division == null || division.isBlank()) {
            throw new BadRequest("Add ?division=... to the address, e.g. /api/boxers?division=Strawweight");
        }
        String sql = "SELECT " + BOXER_COLUMNS
                + " WHERE d.name = ? COLLATE NOCASE"
                + " ORDER BY b.rank IS NULL, b.rank, b.name"; // champion (0) first, unranked last
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, division);
            return toJsonArray(stmt.executeQuery());
        }
    }

    /** /api/boxer?id=1 */
    private static String oneBoxer(Map<String, String> params) throws SQLException {
        int id;
        try {
            id = Integer.parseInt(params.getOrDefault("id", ""));
        } catch (NumberFormatException e) {
            throw new BadRequest("Add ?id=... with a number to the address, e.g. /api/boxer?id=1");
        }
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement("SELECT " + BOXER_COLUMNS + " WHERE b.id = ?")) {
            stmt.setInt(1, id);
            String array = toJsonArray(stmt.executeQuery());
            if (array.equals("[]")) {
                throw new NotFound("No boxer with id " + id);
            }
            return array.substring(1, array.length() - 1); // just the one object, without [ ]
        }
    }

    // ---------- plumbing for the API ----------

    private interface Endpoint {
        String run(Map<String, String> params) throws Exception;
    }

    private static class BadRequest extends RuntimeException {
        BadRequest(String message) { super(message); }
    }

    private static class NotFound extends RuntimeException {
        NotFound(String message) { super(message); }
    }

    /** Runs an endpoint and sends back its JSON, or a JSON error message. */
    private static void handle(HttpExchange exchange, Endpoint endpoint) throws IOException {
        int status = 200;
        String json;
        try {
            if (!Files.exists(DB_FILE)) {
                throw new IllegalStateException("database/boxing.db doesn't exist yet. Run DatabaseLoader first.");
            }
            json = endpoint.run(readQuery(exchange));
        } catch (BadRequest e) {
            status = 400;
            json = errorJson(e.getMessage());
        } catch (NotFound e) {
            status = 404;
            json = errorJson(e.getMessage());
        } catch (Exception e) {
            status = 500;
            json = errorJson(e.getMessage());
            e.printStackTrace();
        }
        // Let pages opened from other addresses (like VS Code's Live Server) read the data
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        send(exchange, status, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
    }

    /** Turns "?division=Super%20Flyweight&x=1" into a map. */
    private static Map<String, String> readQuery(HttpExchange exchange) {
        Map<String, String> params = new HashMap<>();
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) {
            return params;
        }
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            params.put(key, value);
        }
        return params;
    }

    /** Turns database rows into a JSON array like [{"name":"...","wins":15}, ...]. */
    private static String toJsonArray(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        StringBuilder json = new StringBuilder("[");
        boolean firstRow = true;
        while (rs.next()) {
            if (!firstRow) json.append(',');
            firstRow = false;
            json.append('{');
            for (int col = 1; col <= meta.getColumnCount(); col++) {
                if (col > 1) json.append(',');
                json.append(quote(meta.getColumnLabel(col))).append(':');
                Object value = rs.getObject(col);
                if (value == null) {
                    json.append("null");
                } else if (value instanceof Number) {
                    json.append(value);
                } else {
                    json.append(quote(value.toString()));
                }
            }
            json.append('}');
        }
        return json.append(']').toString();
    }

    private static String errorJson(String message) {
        return "{\"error\":" + quote(message == null ? "Unknown error" : message) + "}";
    }

    /** Wraps text in quotes and escapes characters that would break JSON. */
    private static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char ch : text.toCharArray()) {
            switch (ch) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (ch < 0x20) out.append(String.format("\\u%04x", (int) ch));
                    else out.append(ch);
                }
            }
        }
        return out.append('"').toString();
    }

    // ---------- serving the website files ----------

    private static void serveFile(HttpExchange exchange) throws IOException {
        String path = URLDecoder.decode(exchange.getRequestURI().getPath(), StandardCharsets.UTF_8);
        if (path.endsWith("/")) {
            path += "index.html";
        }

        Path root = WEBSITE_FOLDER.toAbsolutePath().normalize();
        Path file = root.resolve(path.substring(1)).normalize();

        // Only serve files inside the website folder
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            byte[] body = ("Page not found: " + path).getBytes(StandardCharsets.UTF_8);
            send(exchange, 404, "text/plain; charset=utf-8", body);
            return;
        }
        send(exchange, 200, contentType(file), Files.readAllBytes(file));
    }

    private static String contentType(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (name.endsWith(".json")) return "application/json; charset=utf-8";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".gif")) return "image/gif";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".webp")) return "image/webp";
        if (name.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    private static void send(HttpExchange exchange, int status, String type, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", "no-store"); // always show the latest data
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
