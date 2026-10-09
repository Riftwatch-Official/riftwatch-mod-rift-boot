package net.riftwatch.boot.update;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LineSettings implements KeyDocument {
    private static final String BOM = "﻿";
    private static final Pattern TOML_TABLE = Pattern.compile("^\\s*\\[\\s*([^\\[\\]]+?)\\s*\\]\\s*(?:#.*)?$");
    private static final Pattern TOML_ARRAY_TABLE = Pattern.compile("^\\s*\\[\\[\\s*([^\\[\\]]+?)\\s*\\]\\]\\s*(?:#.*)?$");
    private static final Pattern TOML_KEY = Pattern.compile("^\\s*([A-Za-z0-9_\\-]+(?:\\s*\\.\\s*[A-Za-z0-9_\\-]+)*|\"(?:[^\"\\\\]|\\\\.)*\"|'[^']*')\\s*=\\s*");
    private static final Pattern PROPERTIES_KEY = Pattern.compile("^\\s*((?:[^\\s=:#!\\\\]|\\\\.)(?:[^\\s=:\\\\]|\\\\.)*)(?:\\s*[=:]\\s*|\\s+)");
    private static final Pattern SPACED_KEY = Pattern.compile("^\\s*([^\\s=#][^=]*?)\\s*=\\s*");

    enum Syntax {
        TOML,
        PROPERTIES,
        SPACED
    }

    private record Line(String text, String section, String key, boolean header, boolean opaque, boolean continuation, int valueStart, int valueEnd) {
        boolean isKey() {
            return key != null && !header && !continuation;
        }

        boolean belongsTo(String name) {
            return section.equals(name) && (header || key != null);
        }

        String value() {
            return text.substring(valueStart, valueEnd);
        }

        Line withValue(String value) {
            String changed = text.substring(0, valueStart) + value + text.substring(valueEnd);
            return new Line(changed, section, key, false, false, false, valueStart, valueStart + value.length());
        }
    }

    private final Syntax syntax;
    private final List<Line> lines;
    private final String separator;
    private final boolean trailingNewline;
    private final boolean bom;

    private LineSettings(Syntax syntax, List<Line> lines, String separator, boolean trailingNewline, boolean bom) {
        this.syntax = syntax;
        this.lines = lines;
        this.separator = separator;
        this.trailingNewline = trailingNewline;
        this.bom = bom;
    }

    static Optional<KeyDocument> parse(Syntax syntax, byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        boolean bom = text.startsWith(BOM);
        if (bom) {
            text = text.substring(1);
        }
        String separator = text.contains("\r\n") ? "\r\n" : "\n";
        boolean trailing = text.endsWith("\n");
        List<String> physical = new ArrayList<>(Arrays.asList(text.split("\r?\n", -1)));
        if (trailing) {
            physical.remove(physical.size() - 1);
        }
        List<Line> lines = new ArrayList<>();
        String section = "";
        int arrayTables = 0;
        String openKey = null;
        TomlScan scan = null;
        for (String raw : physical) {
            if (openKey != null) {
                lines.add(new Line(raw, section, openKey, false, true, true, -1, -1));
                boolean closed = syntax == Syntax.TOML ? scan.scan(raw, 0) >= 0 : !continues(raw);
                if (closed) {
                    openKey = null;
                }
                continue;
            }
            if (syntax == Syntax.TOML) {
                Matcher arrayTable = TOML_ARRAY_TABLE.matcher(raw);
                if (arrayTable.matches()) {
                    section = "[[" + arrayTable.group(1) + "]]" + arrayTables++;
                    lines.add(new Line(raw, section, null, true, false, false, -1, -1));
                    continue;
                }
                Matcher table = TOML_TABLE.matcher(raw);
                if (table.matches()) {
                    section = table.group(1).replaceAll("\\s*\\.\\s*", ".");
                    lines.add(new Line(raw, section, null, true, false, false, -1, -1));
                    continue;
                }
            }
            String trimmed = raw.stripLeading();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || (syntax == Syntax.PROPERTIES && trimmed.startsWith("!"))) {
                lines.add(new Line(raw, section, null, false, false, false, -1, -1));
                continue;
            }
            Matcher key = keyPattern(syntax).matcher(raw);
            if (!key.find()) {
                lines.add(new Line(raw, section, null, false, false, false, -1, -1));
                continue;
            }
            String name = syntax == Syntax.TOML ? key.group(1).replaceAll("\\s*\\.\\s*", ".") : key.group(1);
            String fullKey = section.isEmpty() ? name : section + "." + name;
            int start = key.end();
            int end;
            if (syntax == Syntax.TOML) {
                scan = new TomlScan();
                end = scan.scan(raw, start);
            } else {
                end = continues(raw) ? -1 : trimEnd(raw, start, raw.length());
            }
            if (end < 0) {
                lines.add(new Line(raw, section, fullKey, false, true, false, -1, -1));
                openKey = fullKey;
            } else {
                lines.add(new Line(raw, section, fullKey, false, false, false, start, end));
            }
        }
        if (openKey != null) {
            return Optional.empty();
        }
        return Optional.of(new LineSettings(syntax, lines, separator, trailing, bom));
    }

    private static Pattern keyPattern(Syntax syntax) {
        return switch (syntax) {
            case TOML -> TOML_KEY;
            case PROPERTIES -> PROPERTIES_KEY;
            case SPACED -> SPACED_KEY;
        };
    }

    private static boolean continues(String line) {
        int backslashes = 0;
        for (int i = line.length() - 1; i >= 0 && line.charAt(i) == '\\'; i--) {
            backslashes++;
        }
        return backslashes % 2 == 1;
    }

    private static int trimEnd(String line, int start, int end) {
        int position = end;
        while (position > start && Character.isWhitespace(line.charAt(position - 1))) {
            position--;
        }
        return position;
    }

    @Override
    public Map<String, String> values() {
        Map<String, String> values = new LinkedHashMap<>();
        for (Line line : lines) {
            if (line.isKey() && !line.opaque()) {
                values.put(line.key(), line.value());
            }
        }
        return values;
    }

    @Override
    public boolean opaque(String key) {
        return lines.stream().anyMatch(line -> line.isKey() && line.opaque() && line.key().equals(key));
    }

    @Override
    public void take(String key, KeyDocument from) {
        if (!(from instanceof LineSettings other) || other.syntax != syntax) {
            throw new IllegalArgumentException("Settings formats differ for " + key);
        }
        int source = other.lastKeyLine(key);
        if (source < 0 || other.lines.get(source).opaque()) {
            return;
        }
        Line donor = other.lines.get(source);
        int target = lastKeyLine(key);
        if (target >= 0) {
            if (!lines.get(target).opaque()) {
                lines.set(target, lines.get(target).withValue(donor.value()));
            }
            return;
        }
        int at = insertionIndex(donor.section());
        if (at >= 0) {
            lines.add(at, donor);
            return;
        }
        if (!lines.isEmpty() && !lines.get(lines.size() - 1).text().isBlank()) {
            lines.add(new Line("", "", null, false, false, false, -1, -1));
        }
        other.headerLine(donor.section()).ifPresent(lines::add);
        lines.add(donor);
    }

    @Override
    public void remove(String key) {
        int index = lastKeyLine(key);
        if (index >= 0 && !lines.get(index).opaque()) {
            lines.remove(index);
        }
    }

    @Override
    public byte[] render() {
        StringBuilder text = new StringBuilder();
        if (bom) {
            text.append(BOM);
        }
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                text.append(separator);
            }
            text.append(lines.get(i).text());
        }
        if (trailingNewline) {
            text.append(separator);
        }
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }

    private int lastKeyLine(String key) {
        for (int i = lines.size() - 1; i >= 0; i--) {
            Line line = lines.get(i);
            if (line.isKey() && line.key().equals(key)) {
                return i;
            }
        }
        return -1;
    }

    private Optional<Line> headerLine(String name) {
        return lines.stream().filter(line -> line.header() && line.section().equals(name)).findFirst();
    }

    private int insertionIndex(String name) {
        int last = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).belongsTo(name)) {
                last = i;
            }
        }
        if (last >= 0) {
            return last + 1;
        }
        if (!name.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).header()) {
                return i;
            }
        }
        return lines.size();
    }

    private static final class TomlScan {
        private int depth;
        private char quote;
        private boolean triple;
        private boolean escaped;

        int scan(String line, int from) {
            int i = from;
            while (i < line.length()) {
                char c = line.charAt(i);
                if (quote != 0) {
                    if (escaped) {
                        escaped = false;
                        i++;
                    } else if (c == '\\' && quote == '"') {
                        escaped = true;
                        i++;
                    } else if (triple && line.startsWith(String.valueOf(quote).repeat(3), i)) {
                        quote = 0;
                        triple = false;
                        i += 3;
                        if (depth == 0) {
                            return i;
                        }
                    } else if (!triple && c == quote) {
                        quote = 0;
                        i++;
                        if (depth == 0) {
                            return i;
                        }
                    } else {
                        i++;
                    }
                    continue;
                }
                if (c == '"' || c == '\'') {
                    triple = line.startsWith(String.valueOf(c).repeat(3), i);
                    quote = c;
                    i += triple ? 3 : 1;
                } else if (c == '[' || c == '{') {
                    depth++;
                    i++;
                } else if (c == ']' || c == '}') {
                    depth--;
                    i++;
                    if (depth <= 0) {
                        depth = 0;
                        return i;
                    }
                } else if (c == '#') {
                    if (depth == 0) {
                        return trimEnd(line, from, i);
                    }
                    break;
                } else {
                    i++;
                }
            }
            if (quote != 0 && !triple) {
                quote = 0;
                escaped = false;
            }
            if (depth > 0 || quote != 0) {
                return -1;
            }
            return trimEnd(line, from, line.length());
        }
    }
}
