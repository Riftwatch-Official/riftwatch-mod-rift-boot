package net.riftwatch.boot.update;

import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleDescriptor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class ServiceJars {
    static final Set<String> SERVICES = Set.of(
            "cpw.mods.modlauncher.api.ITransformationService",
            "net.neoforged.neoforgespi.locating.IModFileCandidateLocator",
            "net.neoforged.neoforgespi.locating.IModFileReader",
            "net.neoforged.neoforgespi.locating.IDependencyLocator",
            "net.neoforged.neoforgespi.earlywindow.GraphicsBootstrapper",
            "net.neoforged.neoforgespi.earlywindow.ImmediateWindowProvider");
    private static final String SERVICES_DIR = "META-INF/services/";
    private static final String MODULE_INFO = "module-info.class";

    private ServiceJars() {
    }

    static boolean isServiceJar(Path jar) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry moduleInfo = zip.getEntry(MODULE_INFO);
            if (moduleInfo != null) {
                try (InputStream input = zip.getInputStream(moduleInfo)) {
                    return ModuleDescriptor.read(input).provides().stream()
                            .anyMatch(provides -> SERVICES.contains(provides.service()));
                }
            }
            for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements();) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.startsWith(SERVICES_DIR)) {
                    continue;
                }
                String service = name.substring(SERVICES_DIR.length());
                if (SERVICES.contains(service) && hasProvider(zip, entry)) {
                    return true;
                }
            }
            return false;
        } catch (IOException | RuntimeException e) {
            UpdateLog.LOG.warn("{} could not be inspected, treated as a service jar: {}", jar.getFileName(), UpdateException.describe(e));
            return true;
        }
    }

    private static boolean hasProvider(ZipFile zip, ZipEntry entry) throws IOException {
        try (InputStream input = zip.getInputStream(entry)) {
            String content = new String(input.readNBytes(1 << 16), StandardCharsets.UTF_8);
            return content.lines()
                    .map(line -> line.contains("#") ? line.substring(0, line.indexOf('#')) : line)
                    .anyMatch(line -> !line.isBlank());
        }
    }
}
