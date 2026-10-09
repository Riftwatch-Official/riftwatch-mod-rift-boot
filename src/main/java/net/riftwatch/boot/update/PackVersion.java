package net.riftwatch.boot.update;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record PackVersion(String text, List<Integer> numbers, String suffix) implements Comparable<PackVersion> {
    private static final Pattern FORMAT = Pattern.compile("(\\d{1,6}(?:\\.\\d{1,6}){0,3})(?:-([0-9A-Za-z][0-9A-Za-z.-]{0,31}))?");

    static Optional<PackVersion> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String trimmed = text.trim();
        Matcher matcher = FORMAT.matcher(trimmed);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        List<Integer> numbers = new ArrayList<>();
        for (String part : matcher.group(1).split("\\.")) {
            numbers.add(Integer.parseInt(part));
        }
        return Optional.of(new PackVersion(trimmed, List.copyOf(numbers), matcher.group(2) == null ? "" : matcher.group(2)));
    }

    boolean newerThan(PackVersion other) {
        return compareTo(other) > 0;
    }

    @Override
    public int compareTo(PackVersion other) {
        int length = Math.max(numbers.size(), other.numbers.size());
        for (int i = 0; i < length; i++) {
            int mine = i < numbers.size() ? numbers.get(i) : 0;
            int theirs = i < other.numbers.size() ? other.numbers.get(i) : 0;
            if (mine != theirs) {
                return Integer.compare(mine, theirs);
            }
        }
        if (suffix.equals(other.suffix)) {
            return 0;
        }
        if (suffix.isEmpty()) {
            return 1;
        }
        if (other.suffix.isEmpty()) {
            return -1;
        }
        return suffix.compareTo(other.suffix);
    }

    @Override
    public String toString() {
        return text;
    }
}
