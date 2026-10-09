package net.riftwatch.boot.update;

import java.util.regex.Pattern;

record PathGlob(String glob, Pattern pattern) {
    static PathGlob of(String glob) {
        StringBuilder regex = new StringBuilder();
        int i = 0;
        while (i < glob.length()) {
            char current = glob.charAt(i);
            if (current == '*' && glob.startsWith("**/", i)) {
                regex.append("(?:.*/)?");
                i += 3;
            } else if (current == '*' && glob.startsWith("**", i)) {
                regex.append(".*");
                i += 2;
            } else if (current == '*') {
                regex.append("[^/]*");
                i++;
            } else if (current == '?') {
                regex.append("[^/]");
                i++;
            } else {
                regex.append(Pattern.quote(String.valueOf(current)));
                i++;
            }
        }
        return new PathGlob(glob, Pattern.compile(regex.toString()));
    }

    boolean matches(String path) {
        return pattern.matcher(path).matches();
    }
}
