package net.riftwatch.boot.update;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

final class FileOps {
    private static final String TEMP_SUFFIX = ".riftwatch-tmp";

    private FileOps() {
    }

    static MessageDigest sha512Digest() {
        try {
            return MessageDigest.getInstance("SHA-512");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-512 is missing in this Java runtime", e);
        }
    }

    static String sha512(byte[] content) {
        return HexFormat.of().formatHex(sha512Digest().digest(content));
    }

    static String sha512(Path file) throws IOException {
        MessageDigest digest = sha512Digest();
        byte[] buffer = new byte[1 << 16];
        try (InputStream input = Files.newInputStream(file)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static byte[] readIfExists(Path file) throws IOException {
        try {
            return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
        } catch (NoSuchFileException e) {
            return null;
        }
    }

    static void writeAtomically(Path target, byte[] content) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + TEMP_SUFFIX);
        Files.write(temp, content);
        move(temp, target);
    }

    static void move(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        List<Path> paths;
        try (Stream<Path> walk = Files.walk(root)) {
            paths = walk.sorted(Comparator.reverseOrder()).toList();
        }
        for (Path path : paths) {
            Files.deleteIfExists(path);
        }
    }

    static void deleteEmptyParents(Path file, Path stop) throws IOException {
        Path parent = file.getParent();
        while (parent != null && parent.startsWith(stop) && !parent.equals(stop)) {
            try (Stream<Path> children = Files.list(parent)) {
                if (children.findAny().isPresent()) {
                    return;
                }
            }
            Files.delete(parent);
            parent = parent.getParent();
        }
    }
}
