package net.riftwatch.boot;

import static org.lwjgl.opengl.GL32C.*;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.neoforged.fml.earlydisplay.ColourScheme;
import net.neoforged.fml.earlydisplay.DisplayWindow;
import net.neoforged.fml.earlydisplay.EarlyFramebuffer;
import net.neoforged.fml.earlydisplay.RenderElement;
import net.neoforged.fml.loading.FMLPaths;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RiftwatchWindow extends DisplayWindow {
    static final String NAME = "riftwatch";
    static final Logger LOGGER = LoggerFactory.getLogger("RIFTWATCH-BOOT");
    private static final ColourScheme.Colour LETTERBOX = new ColourScheme.Colour(12, 8, 7);
    private static final long INSTALL_DELAY_MILLIS = 5;
    private static final int DEFAULT_WIDTH = 854;
    private static final int DEFAULT_HEIGHT = 480;

    private volatile int alpha = 255;
    private Method overlayFactory;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Runnable start(String mcVersion, String forgeVersion) {
        try {
            darkenLetterbox();
            write(DisplayWindow.class, this, "colourScheme", ColourScheme.BLACK);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Riftwatch boot screen could not set the dark colour scheme", e);
        }
        Runnable tick = super.start(mcVersion, forgeVersion);
        try {
            ScheduledExecutorService scheduler = (ScheduledExecutorService) read(DisplayWindow.class, this, "renderScheduler");
            scheduler.schedule(() -> install(mcVersion, forgeVersion), INSTALL_DELAY_MILLIS, TimeUnit.MILLISECONDS);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("Riftwatch boot screen not installed, NeoForge's screen stays", e);
        }
        return tick;
    }

    private void install(String mcVersion, String forgeVersion) {
        try {
            Object current = read(DisplayWindow.class, this, "elements");
            if (!(current instanceof List<?> list)) {
                LOGGER.error("Riftwatch boot screen not installed, NeoForge's element list is missing");
                return;
            }
            List<RenderElement> original = new ArrayList<>(list.stream().map(RenderElement.class::cast).toList());
            Map<String, String> options = readOptions();
            int guiScale = parseInt(options.get("guiScale"));
            boolean unicodeFont = Boolean.parseBoolean(options.get("forceUnicodeFont"));
            BootScene scene = new BootScene(this, mcVersion, forgeVersion, guiScale, unicodeFont);
            write(DisplayWindow.class, this, "elements", new SceneElements(this, original, scene));
            LOGGER.info("Riftwatch boot screen installed");
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("Riftwatch boot screen not installed, NeoForge's screen stays", e);
        }
    }

    int alpha() {
        return alpha;
    }

    void fitFramebuffer() throws ReflectiveOperationException {
        int[] size = windowSize();
        RenderElement.DisplayContext current = context();
        if (size == null || (current.width() == size[0] && current.height() == size[1] && current.scale() == 1)) {
            return;
        }
        resize(new RenderElement.DisplayContext(size[0], size[1], 1, current.elementShader(), current.colourScheme(), current.performance()));
    }

    void restoreFramebuffer() {
        try {
            RenderElement.DisplayContext current = context();
            int scale = (int) read(DisplayWindow.class, this, "fbScale");
            if (current.width() != DEFAULT_WIDTH || current.height() != DEFAULT_HEIGHT || current.scale() != scale) {
                resize(new RenderElement.DisplayContext(DEFAULT_WIDTH, DEFAULT_HEIGHT, scale, current.elementShader(), current.colourScheme(), current.performance()));
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("NeoForge's frame size could not be restored", e);
        }
    }

    private int[] windowSize() throws ReflectiveOperationException {
        int width;
        int height;
        if (read(DisplayWindow.class, this, "windowTick") != null) {
            width = (int) read(DisplayWindow.class, this, "fbWidth");
            height = (int) read(DisplayWindow.class, this, "fbHeight");
        } else {
            long handle = (long) read(DisplayWindow.class, this, "window");
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer w = stack.mallocInt(1);
                IntBuffer h = stack.mallocInt(1);
                GLFW.glfwGetFramebufferSize(handle, w, h);
                width = w.get(0);
                height = h.get(0);
            }
        }
        return width > 0 && height > 0 ? new int[] { width, height } : null;
    }

    private void resize(RenderElement.DisplayContext next) throws ReflectiveOperationException {
        EarlyFramebuffer framebuffer = (EarlyFramebuffer) read(DisplayWindow.class, this, "framebuffer");
        int texture = (int) read(EarlyFramebuffer.class, framebuffer, "texture");
        int previousUnit = glGetInteger(GL_ACTIVE_TEXTURE);
        int previousUnpack = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
        glActiveTexture(GL_TEXTURE0);
        int previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, next.scaledWidth(), next.scaledHeight(), 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glBindTexture(GL_TEXTURE_2D, previousTexture);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, previousUnpack);
        glActiveTexture(previousUnit);
        write(EarlyFramebuffer.class, framebuffer, "context", next);
        write(DisplayWindow.class, this, "context", next);
        glViewport(0, 0, next.scaledWidth(), next.scaledHeight());
        glClear(GL_COLOR_BUFFER_BIT);
        next.elementShader().activate();
        next.elementShader().updateScreenSizeUniform(next.scaledWidth(), next.scaledHeight());
    }

    private static Map<String, String> readOptions() {
        try {
            Path file = FMLPaths.GAMEDIR.get().resolve("options.txt");
            return Files.readAllLines(file).stream()
                    .map(line -> line.split(":", 2))
                    .filter(parts -> parts.length == 2)
                    .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1], (first, second) -> second));
        } catch (IOException | RuntimeException e) {
            return Map.of();
        }
    }

    private static int parseInt(String value) {
        try {
            return value == null ? 0 : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public void render(int alpha) {
        this.alpha = alpha;
        super.render(alpha);
    }

    @Override
    public void addMojangTexture(int textureId) {
    }

    @Override
    public void updateModuleReads(ModuleLayer layer) {
        Module neoforge = layer.findModule("neoforge").orElseThrow();
        RiftwatchWindow.class.getModule().addReads(neoforge);
        Class<?> overlay = Class.forName(neoforge, "net.neoforged.neoforge.client.loading.NeoForgeLoadingOverlay");
        if (overlay == null) {
            throw new IllegalStateException("NeoForgeLoadingOverlay not found");
        }
        overlayFactory = Arrays.stream(overlay.getMethods())
                .filter(method -> Modifier.isStatic(method.getModifiers()) && method.getName().equals("newInstance"))
                .findFirst()
                .orElseThrow();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Supplier<T> loadingOverlay(Supplier<?> mc, Supplier<?> ri, Consumer<Optional<Throwable>> ex, boolean fade) {
        try {
            return (Supplier<T>) overlayFactory.invoke(null, mc, ri, ex, this);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("NeoForge loading overlay could not be created", e);
        }
    }

    private static void darkenLetterbox() throws ReflectiveOperationException {
        write(ColourScheme.class, ColourScheme.BLACK, "background", LETTERBOX);
    }

    static Object read(Class<?> owner, Object target, String name) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    static void write(Class<?> owner, Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
