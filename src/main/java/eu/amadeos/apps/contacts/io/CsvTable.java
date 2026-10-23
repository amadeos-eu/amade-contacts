package eu.amadeos.apps.contacts.io;

import java.util.ArrayList;
import java.util.List;

/**
 * Comma separated values as spreadsheets and address books write them (RFC 4180): fields in double quotes may
 * hold the separator, line breaks and doubled quotes. The separator is a comma, a semicolon (German Excel) or a
 * tab, whichever the first line has most of outside quotes.
 */
public record CsvTable(List<String> headers, List<List<String>> rows) {
    public CsvTable {
        headers = List.copyOf(headers);
        rows = rows.stream().map(List::copyOf).toList();
    }

    public static CsvTable parse(String text) {
        String t = text.startsWith("﻿") ? text.substring(1) : text;
        char separator = separator(t);
        List<List<String>> lines = new ArrayList<>();
        List<String> line = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean any = false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < t.length() && t.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
                any = true;
            } else if (c == separator) {
                line.add(field.toString());
                field.setLength(0);
                any = true;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < t.length() && t.charAt(i + 1) == '\n') i++;
                if (any || field.length() > 0) {
                    line.add(field.toString());
                    lines.add(line);
                }
                line = new ArrayList<>();
                field.setLength(0);
                any = false;
            } else {
                field.append(c);
                any = true;
            }
        }
        if (any || field.length() > 0) {
            line.add(field.toString());
            lines.add(line);
        }
        if (lines.isEmpty()) return new CsvTable(List.of(), List.of());
        List<String> headers = lines.getFirst().stream().map(String::strip).toList();
        List<List<String>> rows = new ArrayList<>();
        for (List<String> row : lines.subList(1, lines.size())) {
            if (row.stream().allMatch(String::isBlank)) continue;
            List<String> padded = new ArrayList<>(row);
            while (padded.size() < headers.size()) padded.add("");
            rows.add(padded);
        }
        return new CsvTable(headers, rows);
    }

    /** The value of a column in a row, empty if the row is shorter. */
    public static String cell(List<String> row, int column) {
        return column >= 0 && column < row.size() ? row.get(column).strip() : "";
    }

    private static char separator(String text) {
        int comma = 0, semicolon = 0, tab = 0;
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') quoted = !quoted;
            else if (!quoted && (c == '\n' || c == '\r')) break;
            else if (!quoted && c == ',') comma++;
            else if (!quoted && c == ';') semicolon++;
            else if (!quoted && c == '\t') tab++;
        }
        if (tab > comma && tab > semicolon) return '\t';
        return semicolon > comma ? ';' : ',';
    }

    /** Writes one line with every field quoted, lines ended with CRLF as RFC 4180 and Outlook expect. */
    public static String line(List<String> fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) sb.append(',');
            String f = fields.get(i) == null ? "" : fields.get(i);
            sb.append('"').append(f.replace("\"", "\"\"")).append('"');
        }
        return sb.append("\r\n").toString();
    }
}
