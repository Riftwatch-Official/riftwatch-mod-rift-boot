package net.riftwatch.boot.update;

import java.util.Map;

interface KeyDocument {
    Map<String, String> values();

    boolean opaque(String key);

    void take(String key, KeyDocument from);

    void remove(String key);

    byte[] render();

    static boolean same(String first, String second) {
        if (first == null || second == null) {
            return first == null && second == null;
        }
        String a = unquote(first.trim());
        String b = unquote(second.trim());
        if (a.equals(b)) {
            return true;
        }
        if (isBoolean(a) && isBoolean(b)) {
            return a.equalsIgnoreCase(b);
        }
        Double x = number(a);
        Double y = number(b);
        return x != null && y != null && Math.abs(x - y) <= 1e-6;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            if ((first == '"' || first == '\'') && value.charAt(value.length() - 1) == first) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static boolean isBoolean(String value) {
        return value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false");
    }

    private static Double number(String value) {
        if (value.isEmpty() || !(Character.isDigit(value.charAt(0)) || value.charAt(0) == '-' || value.charAt(0) == '+' || value.charAt(0) == '.')) {
            return null;
        }
        try {
            return Double.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
