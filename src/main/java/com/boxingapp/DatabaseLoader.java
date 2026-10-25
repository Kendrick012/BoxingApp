package com.boxingapp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rebuilds database/boxing.db from the files in the database folder:
 *   1. schema.sql     creates the tables (deletes the old ones first)
 *   2. divisions.csv  loaded into the divisions table
 *   3. boxers.csv     loaded into the boxers table
 *
 * Run this every time you change one of those files.
 * Run it with the project's root folder (BoxingApp) as the working directory.
 */
public class DatabaseLoader {

    private static final Path DB_FOLDER = Path.of("database");
    private static final String DB_URL = "jdbc:sqlite:database/boxing.db";

    public static void main(String[] args) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            conn.setAutoCommit(false); // all or nothing: a bad row means no half-loaded database

            runSchema(conn);
            Map<String, Integer> divisionIds = loadDivisions(conn);
            int boxerCount = loadBoxers(conn, divisionIds);

            conn.commit();
            System.out.println("Done! Loaded " + divisionIds.size() + " divisions and "
                    + boxerCount + " boxers into database/boxing.db");
        } catch (Exception e) {
            System.err.println("Loading failed, nothing was saved.");
            System.err.println("Reason: " + e.getMessage());
        }
    }

    /** Runs every statement in schema.sql. */
    private static void runSchema(Connection conn) throws IOException, SQLException {
        StringBuilder sql = new StringBuilder();
        for (String line : Files.readAllLines(DB_FOLDER.resolve("schema.sql"), StandardCharsets.UTF_8)) {
            if (!line.trim().startsWith("--")) { // skip comment lines
                sql.append(line).append('\n');
            }
        }
        try (Statement stmt = conn.createStatement()) {
            for (String statement : sql.toString().split(";")) {
                if (!statement.isBlank()) {
                    stmt.execute(statement);
                }
            }
        }
    }

    /** Loads divisions.csv and returns each division's name mapped to its new id. */
    private static Map<String, Integer> loadDivisions(Connection conn) throws IOException, SQLException {
        Map<String, Integer> ids = new HashMap<>();
        String sql = "INSERT INTO divisions (name, weight_limit_lbs) VALUES (?, ?)";

        try (PreparedStatement insert = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (CsvRow row : readCsv("divisions.csv")) {
                String name = row.require("name");
                insert.setString(1, name);
                setNumber(insert, 2, row.get("weight_limit_lbs"), row, true);
                insert.executeUpdate();

                try (var keys = insert.getGeneratedKeys()) {
                    keys.next();
                    ids.put(name.toLowerCase(), keys.getInt(1));
                }
            }
        }
        return ids;
    }

    /** Loads boxers.csv, matching each boxer's division name to a division id. */
    private static int loadBoxers(Connection conn, Map<String, Integer> divisionIds)
            throws IOException, SQLException {
        String sql = "INSERT INTO boxers (name, nickname, organization, division_id, rank, titles, nationality, stance, "
                + "age, height_cm, reach_cm, wins, losses, draws, kos, image_url) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        int count = 0;
        Map<String, String> takenRanks = new HashMap<>(); // "division|organization|rank" -> boxer who has it

        try (PreparedStatement insert = conn.prepareStatement(sql)) {
            for (CsvRow row : readCsv("boxers.csv")) {
                String name = row.require("name");
                insert.setString(1, name);
                setText(insert, 2, row.get("nickname"));

                // Organization (WBA, WBC, IBF, WBO ...): stored in capitals so "wbc" and "WBC" match
                String organization = row.get("organization").toUpperCase();
                setText(insert, 3, organization);

                String division = row.get("division");
                if (division.isEmpty()) {
                    insert.setNull(4, Types.INTEGER);
                } else {
                    Integer divisionId = divisionIds.get(division.toLowerCase());
                    if (divisionId == null) {
                        throw new IllegalArgumentException(row.where() + ": division \"" + division
                                + "\" isn't in divisions.csv. Check the spelling or add it there.");
                    }
                    insert.setInt(4, divisionId);
                }

                // Rank codes:  C  = champion           (stored as 0)
                //              SC = regular champion   (stored as 0.5, so it sorts right after C)
                //              IC = interim champion   (stored as 0.7, after SC)
                //              1, 2, 3 ... = contenders;  empty = unranked
                String rank = rankCode(row.get("rank"));
                if (!rank.isEmpty()) {
                    String key = division.toLowerCase() + "|" + organization + "|" + rank;
                    String holder = takenRanks.putIfAbsent(key, name);
                    if (holder != null) {
                        throw new IllegalArgumentException(row.where() + ": " + name + " and " + holder
                                + " are both " + rankLabel(rank) + " in " + division
                                + (organization.isEmpty() ? "" : " (" + organization + ")") + ".");
                    }
                }
                setNumber(insert, 5, rank, row, true);
                setText(insert, 6, row.get("titles"));

                setText(insert, 7, row.get("nationality"));
                setText(insert, 8, row.get("stance"));
                setNumber(insert, 9, row.get("age"), row, false);
                setNumber(insert, 10, row.get("height_cm"), row, false);
                setNumber(insert, 11, row.get("reach_cm"), row, false);

                String wins = orZero(row.get("wins"));
                String kos = orZero(row.get("kos"));
                setNumber(insert, 12, wins, row, false);
                setNumber(insert, 13, orZero(row.get("losses")), row, false);
                setNumber(insert, 14, orZero(row.get("draws")), row, false);
                setNumber(insert, 15, kos, row, false);
                if (Integer.parseInt(kos) > Integer.parseInt(wins)) {
                    throw new IllegalArgumentException(row.where() + ": " + name + " has more KOs ("
                            + kos + ") than wins (" + wins + ").");
                }
                setText(insert, 16, row.get("image_url"));

                insert.executeUpdate();
                count++;
            }
        }
        return count;
    }

    // ---------- small helpers ----------

    /** Turns the rank typed in the CSV into the number stored in the database. */
    private static String rankCode(String rank) {
        return switch (rank.toUpperCase()) {
            case "C", "CHAMPION" -> "0";
            case "SC" -> "0.5";
            case "IC" -> "0.7";
            default -> rank;
        };
    }

    /** Words for a rank, used in error messages. */
    private static String rankLabel(String rank) {
        return switch (rank) {
            case "0" -> "champion";
            case "0.5" -> "regular champion (SC)";
            case "0.7" -> "interim champion (IC)";
            default -> "rank " + rank;
        };
    }

    private static String orZero(String value) {
        return value.isEmpty() ? "0" : value;
    }

    /** Empty cells become NULL in the database. */
    private static void setText(PreparedStatement stmt, int index, String value) throws SQLException {
        if (value.isEmpty()) {
            stmt.setNull(index, Types.VARCHAR);
        } else {
            stmt.setString(index, value);
        }
    }

    private static void setNumber(PreparedStatement stmt, int index, String value, CsvRow row, boolean decimal)
            throws SQLException {
        if (value.isEmpty()) {
            stmt.setNull(index, decimal ? Types.REAL : Types.INTEGER);
            return;
        }
        try {
            if (decimal) {
                stmt.setDouble(index, Double.parseDouble(value));
            } else {
                stmt.setInt(index, Integer.parseInt(value));
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(row.where() + ": \"" + value + "\" should be a number.");
        }
    }

    /** One line of a CSV file, looked up by column name. */
    private record CsvRow(String file, int lineNumber, Map<String, String> values) {
        String get(String column) {
            return values.getOrDefault(column, "").trim();
        }

        String require(String column) {
            String value = get(column);
            if (value.isEmpty()) {
                throw new IllegalArgumentException(where() + ": the \"" + column + "\" column can't be empty.");
            }
            return value;
        }

        String where() {
            return file + " line " + lineNumber;
        }
    }

    /** Reads a CSV file. The first line is the header. Blank lines and lines without commas are skipped. */
    private static List<CsvRow> readCsv(String fileName) throws IOException {
        List<String> lines = Files.readAllLines(DB_FOLDER.resolve(fileName), StandardCharsets.UTF_8);
        List<CsvRow> rows = new ArrayList<>();
        if (lines.isEmpty()) {
            return rows;
        }

        List<String> header = splitCsvLine(lines.get(0).replace("﻿", "")); // drop Excel's hidden BOM
        for (int i = 1; i < lines.size(); i++) {
            // Skip blank lines and lines with no commas, like "STRAWWEIGHT" or "=====".
            // That lets you use headings and divider lines to organize the file.
            if (lines.get(i).isBlank() || !lines.get(i).contains(",")) {
                continue;
            }
            List<String> cells = splitCsvLine(lines.get(i));
            Map<String, String> values = new HashMap<>();
            for (int c = 0; c < header.size(); c++) {
                values.put(header.get(c).trim(), c < cells.size() ? cells.get(c) : "");
            }
            rows.add(new CsvRow(fileName, i + 1, values));
        }
        return rows;
    }

    /** Splits one CSV line on commas. Put a value in "quotes" if it contains a comma. */
    private static List<String> splitCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"'); // "" inside quotes means one " character
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        cells.add(current.toString());
        return cells;
    }
}
