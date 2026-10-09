package net.riftwatch.boot.update;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class LaunchArguments {
    private static volatile List<String> arguments = List.of();

    private LaunchArguments() {
    }

    public static void capture(String[] values) {
        if (values != null) {
            arguments = Arrays.stream(values).filter(Objects::nonNull).toList();
        }
    }

    static Optional<String> value(String name) {
        return value(arguments, name);
    }

    static Optional<String> value(List<String> source, String name) {
        String flag = "--" + name;
        String inline = flag + "=";
        for (int i = 0; i < source.size(); i++) {
            String argument = source.get(i);
            if (argument.equals(flag) && i + 1 < source.size()) {
                return Optional.of(source.get(i + 1));
            }
            if (argument.startsWith(inline)) {
                return Optional.of(argument.substring(inline.length()));
            }
        }
        return Optional.empty();
    }
}
