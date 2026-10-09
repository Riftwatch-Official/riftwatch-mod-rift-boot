package net.riftwatch.boot.update;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class JsonSettings implements KeyDocument {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();
    private static final String JOIN = "/";

    private final JsonObject root;
    private final String indent;
    private final String separator;
    private final boolean trailingNewline;
    private final Map<String, List<String>> paths = new LinkedHashMap<>();
    private final Map<String, JsonElement> leaves = new LinkedHashMap<>();

    private JsonSettings(JsonObject root, String indent, String separator, boolean trailingNewline) {
        this.root = root;
        this.indent = indent;
        this.separator = separator;
        this.trailingNewline = trailingNewline;
        index(root, List.of());
    }

    static Optional<KeyDocument> parse(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        JsonElement element = JsonParser.parseString(text);
        if (!element.isJsonObject()) {
            return Optional.empty();
        }
        String indent = text.lines()
                .skip(1)
                .filter(line -> !line.isBlank())
                .findFirst()
                .map(line -> line.substring(0, line.length() - line.stripLeading().length()))
                .orElse("");
        String separator = text.contains("\r\n") ? "\r\n" : "\n";
        return Optional.of(new JsonSettings(element.getAsJsonObject(), indent, separator, text.endsWith("\n")));
    }

    private void index(JsonObject object, List<String> prefix) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            List<String> path = new ArrayList<>(prefix);
            path.add(entry.getKey());
            JsonElement value = entry.getValue();
            if (value.isJsonObject() && value.getAsJsonObject().size() > 0) {
                index(value.getAsJsonObject(), path);
            } else {
                String key = String.join(JOIN, path);
                paths.put(key, List.copyOf(path));
                leaves.put(key, value);
            }
        }
    }

    @Override
    public Map<String, String> values() {
        Map<String, String> values = new LinkedHashMap<>();
        leaves.forEach((key, value) -> values.put(key, text(value)));
        return values;
    }

    private static String text(JsonElement value) {
        if (value.isJsonPrimitive()) {
            return value.getAsString();
        }
        return value.isJsonNull() ? "null" : value.toString();
    }

    @Override
    public boolean opaque(String key) {
        return false;
    }

    @Override
    public void take(String key, KeyDocument from) {
        if (!(from instanceof JsonSettings other)) {
            throw new IllegalArgumentException("Settings formats differ for " + key);
        }
        List<String> path = other.paths.get(key);
        JsonElement value = other.leaves.get(key);
        if (path == null || value == null) {
            return;
        }
        JsonObject cursor = root;
        for (int i = 0; i < path.size() - 1; i++) {
            JsonElement child = cursor.get(path.get(i));
            if (child == null || !child.isJsonObject()) {
                child = new JsonObject();
                cursor.add(path.get(i), child);
            }
            cursor = child.getAsJsonObject();
        }
        JsonElement copy = value.deepCopy();
        cursor.add(path.get(path.size() - 1), copy);
        paths.put(key, path);
        leaves.put(key, copy);
    }

    @Override
    public void remove(String key) {
        List<String> path = paths.get(key);
        if (path == null) {
            return;
        }
        JsonObject cursor = root;
        for (int i = 0; i < path.size() - 1; i++) {
            JsonElement child = cursor.get(path.get(i));
            if (child == null || !child.isJsonObject()) {
                return;
            }
            cursor = child.getAsJsonObject();
        }
        cursor.remove(path.get(path.size() - 1));
        paths.remove(key);
        leaves.remove(key);
    }

    @Override
    public byte[] render() {
        StringWriter text = new StringWriter();
        try {
            JsonWriter writer = GSON.newJsonWriter(text);
            writer.setIndent(indent);
            GSON.toJson(root, writer);
            writer.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String rendered = text.toString();
        if (!separator.equals("\n")) {
            rendered = rendered.replace("\n", separator);
        }
        return (trailingNewline ? rendered + separator : rendered).getBytes(StandardCharsets.UTF_8);
    }
}
