package net.riftwatch.boot.update;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.Set;

public final class UpdateService implements ITransformationService {
    static final String NAME = "riftwatch_update";
    private static final String CLIENT_TARGET = "forgeclient";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void onLoad(IEnvironment environment, Set<String> otherServices) {
    }

    @Override
    public void initialize(IEnvironment environment) {
        try {
            String target = environment.getProperty(IEnvironment.Keys.LAUNCHTARGET.get()).orElse("");
            if (!CLIENT_TARGET.equals(target)) {
                return;
            }
            Path root = environment.getProperty(IEnvironment.Keys.GAMEDIR.get()).orElse(Path.of("")).toAbsolutePath().normalize();
            new PackUpdater(
                    root,
                    new WindowDisplay(),
                    PackSource::fromSystem,
                    PackSignature.embedded(),
                    bootVersion(),
                    LaunchArguments.value("fml.mcVersion").orElse(null),
                    LaunchArguments.value("fml.neoForgeVersion").orElse(null),
                    () -> System.exit(0),
                    action -> Runtime.getRuntime().addShutdownHook(new Thread(action, "Riftwatch-Settings-Snapshot")))
                    .run();
        } catch (Throwable failure) {
            UpdateLog.LOG.error("Riftwatch updater could not run, the game starts with the installed pack", failure);
        }
    }

    @Override
    public List<? extends ITransformer<?>> transformers() {
        return List.of();
    }

    static String bootVersion() {
        try (InputStream input = UpdateService.class.getResourceAsStream("boot.properties")) {
            if (input == null) {
                return "unknown";
            }
            Properties properties = new Properties();
            properties.load(input);
            return properties.getProperty("version", "unknown");
        } catch (IOException e) {
            return "unknown";
        }
    }
}
