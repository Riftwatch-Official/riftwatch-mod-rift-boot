package net.riftwatch.boot;

import static org.lwjgl.opengl.GL32C.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.neoforged.fml.earlydisplay.ElementShader;
import net.neoforged.fml.earlydisplay.QuadHelper;
import net.neoforged.fml.earlydisplay.RenderElement;
import net.neoforged.fml.earlydisplay.SimpleBufferBuilder;
import net.neoforged.fml.loading.progress.ProgressMeter;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

final class BootScene {
    private static final float FRAME_ASPECT = 1.77777778f;
    private static final float TAU = 6.2831853f;
    private static final int UNIT_WORDMARK = 12;
    private static final int UNIT_GLOW = 13;
    private static final int UNIT_TAGLINE = 14;
    private static final int UNIT_FONT = 15;
    private static final int MIN_GUI_WIDTH = 320;
    private static final int MIN_GUI_HEIGHT = 240;

    private static final int SAND = 0xC9A58A;
    private static final int MUTED = 0xA88A73;
    private static final int COPPER = 0xD98A4F;
    private static final int COPPER_LIGHT = 0xE8B48A;
    private static final int COPPER_DARK = 0x8A4B23;
    private static final int EDGE = 0x070403;
    private static final int TRACK = 0x0B0705;
    private static final int WHITE = 0xFFFFFF;
    private static final int DIAL_DAY = 0x5F6B78;
    private static final int DIAL_NIGHT = 0x1C1826;
    private static final int DIAL_SUN = 0xFFD88F;
    private static final int DIAL_MOON = 0xE9E0CF;
    private static final int DIAL_MARK = 0x110B08;

    private static final float WORDMARK_X = -33.5f;
    private static final float WORDMARK_FRACTION = 0.0884f;
    private static final float WORDMARK_Y = -2.59f;
    private static final float WORDMARK_W = 65.0f;
    private static final float WORDMARK_H = 13.05f;
    private static final float TAGLINE_X = -21.02f;
    private static final float TAGLINE_FRACTION = 0.2896f;
    private static final float TAGLINE_Y = -0.076f;
    private static final float TAGLINE_W = 42.04f;
    private static final float TAGLINE_H = 1.223f;
    private static final float BARS_FRACTION = 0.635f;
    private static final float BARS_WIDTH = 40.0f;
    private static final int BAR_STEP = 21;
    private static final int BAR_HEIGHT = 6;
    private static final float SWEEP_WIDTH = 0.22f;
    private static final float SWEEP_SECONDS = 1.6f;
    private static final float MEMORY_FRACTION = 0.024f;
    private static final int MEMORY_HEIGHT = 3;
    private static final int EDGE_INSET = 4;
    private static final int DIAL_PIXELS = 16;
    private static final float DIAL_TURN_SECONDS = 8.0f;
    private static final int MAX_BARS = 2;
    private static final int MESSAGE_FADE_START = 4000;
    private static final int MESSAGE_FADE_LENGTH = 4000;

    private static final String VERTEX_SHADER = """
            #version 150 core
            in vec2 corner;
            void main() {
                gl_Position = vec4(corner, 0.0, 1.0);
            }
            """;
    private static final String FRAGMENT_HEADER = """
            #version 150 core
            uniform vec3 iResolution;
            uniform float iTime;
            uniform vec4 iDate;
            uniform float fmGuiScale;
            uniform vec4 fmMouse;
            uniform int fmUnixTimeSeconds;
            uniform int fmUnixTimeMilliseconds;
            uniform float rwAlpha;
            out vec4 rwColor;
            """;
    private static final String FRAGMENT_MAIN = """
            void main() {
                vec4 color;
                mainImage(color, vec2(gl_FragCoord.x, iResolution.y - gl_FragCoord.y));
                rwColor = vec4(color.rgb, rwAlpha);
            }
            """;

    private record Text(String string, int x, int y, int colour) {}

    private final RiftwatchWindow window;
    private final String[] versions;
    private final int guiScaleOption;
    private final boolean unicodeFont;
    private final long startNanos = System.nanoTime();
    private final SimpleBufferBuilder buffer = new SimpleBufferBuilder(4096);
    private final List<Text> texts = new ArrayList<>();
    private boolean ready;
    private int skyProgram;
    private int skyVao;
    private int resolutionUniform;
    private int timeUniform;
    private int dateUniform;
    private int guiScaleUniform;
    private int mouseUniform;
    private int unixSecondsUniform;
    private int unixMillisUniform;
    private int alphaUniform;
    private int wordmark;
    private int glow;
    private int tagline;
    private PixelFont font;
    private int scale;
    private int guiWidth;
    private int guiHeight;
    private float cq;

    BootScene(RiftwatchWindow window, String mcVersion, String forgeVersion, int guiScaleOption, boolean unicodeFont) {
        this.window = window;
        this.guiScaleOption = guiScaleOption;
        this.unicodeFont = unicodeFont;
        this.versions = new String[] {
                "NeoForge " + (forgeVersion == null ? "" : forgeVersion.split("-")[0]),
                "Minecraft " + (mcVersion == null ? "" : mcVersion)
        };
    }

    void paint() throws IOException {
        RenderElement.DisplayContext context = window.context();
        ElementShader shader = context.elementShader();
        int width = context.scaledWidth();
        int height = context.scaledHeight();
        int previousUnit = glGetInteger(GL_ACTIVE_TEXTURE);
        try {
            if (!ready) {
                initialize();
                ready = true;
            }
            scale = guiScale(guiScaleOption, unicodeFont, width, height);
            guiWidth = (width + scale - 1) / scale;
            guiHeight = (height + scale - 1) / scale;
            cq = Math.min(guiWidth, guiHeight * FRAME_ASPECT) / 100f;
            float alpha = window.alpha() / 255f;
            float seconds = (System.nanoTime() - startNanos) / 1.0e9f;
            texts.clear();
            drawSky(width, height, seconds, alpha);
            shader.activate();
            shader.updateScreenSizeUniform(width, height);
            drawImages(shader, alpha);
            drawPanels(shader, seconds, alpha);
            drawTexts(shader, alpha);
        } finally {
            glBindVertexArray(0);
            glActiveTexture(previousUnit);
            shader.activate();
            shader.updateScreenSizeUniform(width, height);
        }
    }

    static int guiScale(int option, boolean unicode, int width, int height) {
        int result = 1;
        while (result != option && result < width && result < height
                && width / (result + 1) >= MIN_GUI_WIDTH && height / (result + 1) >= MIN_GUI_HEIGHT) {
            result++;
        }
        if (unicode && result % 2 != 0) {
            result++;
        }
        return result;
    }

    private void initialize() throws IOException {
        String sky = readResource("transit.glsl");
        skyProgram = link(VERTEX_SHADER, FRAGMENT_HEADER + sky + FRAGMENT_MAIN);
        resolutionUniform = glGetUniformLocation(skyProgram, "iResolution");
        timeUniform = glGetUniformLocation(skyProgram, "iTime");
        dateUniform = glGetUniformLocation(skyProgram, "iDate");
        guiScaleUniform = glGetUniformLocation(skyProgram, "fmGuiScale");
        mouseUniform = glGetUniformLocation(skyProgram, "fmMouse");
        unixSecondsUniform = glGetUniformLocation(skyProgram, "fmUnixTimeSeconds");
        unixMillisUniform = glGetUniformLocation(skyProgram, "fmUnixTimeMilliseconds");
        alphaUniform = glGetUniformLocation(skyProgram, "rwAlpha");

        skyVao = glGenVertexArrays();
        int vbo = glGenBuffers();
        glBindVertexArray(skyVao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, new float[] { -1f, -1f, 3f, -1f, -1f, 3f }, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 0, 0L);
        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        wordmark = loadTexture("wordmark.png", UNIT_WORDMARK);
        glow = loadTexture("wordmark_glow.png", UNIT_GLOW);
        tagline = loadTexture("tagline.png", UNIT_TAGLINE);
        font = new PixelFont(UNIT_FONT);
    }

    private void drawSky(int width, int height, float seconds, float alpha) {
        LocalDateTime now = LocalDateTime.now();
        long millis = System.currentTimeMillis();
        glUseProgram(skyProgram);
        glUniform3f(resolutionUniform, width, height, 1f);
        glUniform1f(timeUniform, seconds);
        glUniform4f(dateUniform, now.getYear(), now.getMonthValue() - 1, now.getDayOfMonth(), now.toLocalTime().toNanoOfDay() / 1.0e9f);
        glUniform1f(guiScaleUniform, scale);
        glUniform4f(mouseUniform, -100000f, -100000f, 0f, 0f);
        glUniform1i(unixSecondsUniform, (int) (millis / 1000L));
        glUniform1i(unixMillisUniform, (int) (millis % 1000L));
        glUniform1f(alphaUniform, alpha);
        glBindVertexArray(skyVao);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glBindVertexArray(0);
    }

    private void drawImages(ElementShader shader, float alpha) {
        float centre = guiWidth / 2f;
        float x = centre + WORDMARK_X * cq;
        float y = guiHeight * WORDMARK_FRACTION + WORDMARK_Y * cq;
        float w = WORDMARK_W * cq;
        float h = WORDMARK_H * cq;
        float breathe = (float) (0.51 + 0.19 * Math.cos(System.currentTimeMillis() * 0.000785398));
        image(shader, UNIT_GLOW, glow, x, y, w, h, breathe * alpha);
        image(shader, UNIT_WORDMARK, wordmark, x, y, w, h, alpha);
        image(shader, UNIT_TAGLINE, tagline, centre + TAGLINE_X * cq, guiHeight * TAGLINE_FRACTION + TAGLINE_Y * cq, TAGLINE_W * cq, TAGLINE_H * cq, alpha);
    }

    private void image(ElementShader shader, int unit, int texture, float x, float y, float w, float h, float alpha) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, texture);
        shader.updateTextureUniform(unit);
        shader.updateRenderTypeUniform(ElementShader.RenderType.TEXTURE);
        buffer.begin(SimpleBufferBuilder.Format.POS_TEX_COLOR, SimpleBufferBuilder.Mode.QUADS);
        QuadHelper.loadQuad(buffer, x * scale, (x + w) * scale, y * scale, (y + h) * scale, 0f, 1f, 0f, 1f, colour(WHITE, alpha));
        buffer.draw();
    }

    private void drawPanels(ElementShader shader, float seconds, float alpha) {
        shader.updateRenderTypeUniform(ElementShader.RenderType.BAR);
        buffer.begin(SimpleBufferBuilder.Format.POS_TEX_COLOR, SimpleBufferBuilder.Mode.QUADS);
        int barWidth = Math.round(BARS_WIDTH * cq);
        int left = (guiWidth - barWidth) / 2;
        drawProgress(left, barWidth, seconds, alpha);
        drawMemory(left, barWidth, alpha);
        drawVersions(alpha);
        drawMessages(alpha);
        drawDial(seconds, alpha);
        buffer.draw();
    }

    private void drawProgress(int left, int barWidth, float seconds, float alpha) {
        List<ProgressMeter> meters = StartupNotificationManager.getCurrentProgress();
        int top = Math.round(guiHeight * BARS_FRACTION);
        for (int i = 0; i < meters.size() && i < MAX_BARS; i++) {
            ProgressMeter meter = meters.get(i);
            int labelY = top + i * BAR_STEP;
            int trackY = labelY + PixelFont.LINE_HEIGHT;
            quad(left, trackY, barWidth, BAR_HEIGHT, EDGE, alpha);
            int innerX = left + 1;
            int innerY = trackY + 1;
            int innerW = barWidth - 2;
            int innerH = BAR_HEIGHT - 2;
            quad(innerX, innerY, innerW, innerH, TRACK, 0.85f * alpha);
            String amount = "";
            if (meter.steps() == 0) {
                float segment = SWEEP_WIDTH * innerW;
                float phase = (seconds % SWEEP_SECONDS) / SWEEP_SECONDS;
                float start = -segment + phase * (innerW + segment);
                fill(innerX + Math.max(0f, start), innerY, Math.min(innerW, start + segment) - Math.max(0f, start), innerH, alpha);
            } else {
                float progress = Math.max(0f, Math.min(1f, meter.progress()));
                fill(innerX, innerY, Math.round(innerW * progress), innerH, alpha);
                amount = Math.round(progress * 100f) + "%";
            }
            int amountWidth = textWidth(amount);
            if (!amount.isEmpty()) {
                texts.add(new Text(amount, left + barWidth - amountWidth, labelY, colour(COPPER_LIGHT, alpha)));
            }
            String label = fit(meter.label().getText(), barWidth - amountWidth - 4);
            texts.add(new Text(label, left, labelY, colour(SAND, alpha)));
        }
    }

    private void fill(float x, float y, float w, float h, float alpha) {
        if (w <= 0f) {
            return;
        }
        quad(x, y, w, 1, COPPER_LIGHT, alpha);
        quad(x, y + 1, w, h - 1, COPPER, alpha);
    }

    private void drawMemory(int left, int barWidth, float alpha) {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        long max = runtime.maxMemory();
        float share = max > 0 ? (float) used / max : 0f;
        String label = String.format(Locale.ROOT, "Memory: %d/%d MB (%.1f%%)", used >> 20, max >> 20, share * 100f);
        int labelY = Math.max(EDGE_INSET, Math.round(guiHeight * MEMORY_FRACTION));
        texts.add(new Text(label, left + (barWidth - textWidth(label)) / 2, labelY, colour(MUTED, alpha)));
        int trackY = labelY + PixelFont.LINE_HEIGHT;
        quad(left, trackY, barWidth, MEMORY_HEIGHT, EDGE, alpha);
        quad(left + 1, trackY + 1, barWidth - 2, MEMORY_HEIGHT - 2, TRACK, 0.85f * alpha);
        quad(left + 1, trackY + 1, Math.round((barWidth - 2) * Math.min(1f, share)), MEMORY_HEIGHT - 2, COPPER_DARK, alpha);
    }

    private void drawVersions(float alpha) {
        int right = guiWidth - EDGE_INSET;
        int top = guiHeight - 2 * EDGE_INSET - DIAL_PIXELS - versions.length * PixelFont.LINE_HEIGHT;
        for (int i = 0; i < versions.length; i++) {
            texts.add(new Text(versions[i], right - textWidth(versions[i]), top + i * PixelFont.LINE_HEIGHT, colour(MUTED, alpha)));
        }
    }

    private void drawMessages(float alpha) {
        List<StartupNotificationManager.AgeMessage> messages = StartupNotificationManager.getMessages();
        int bottom = guiHeight - EDGE_INSET - PixelFont.LINE_HEIGHT;
        for (int i = 0; i < messages.size(); i++) {
            StartupNotificationManager.AgeMessage message = messages.get(i);
            float fade = Math.max(0f, Math.min(1f, (MESSAGE_FADE_START + MESSAGE_FADE_LENGTH - message.age()) / (float) MESSAGE_FADE_LENGTH));
            float base = i == 0 ? 0.8f : 0.55f;
            if (fade * base < 0.02f) {
                continue;
            }
            String text = fit(message.message().getText(), guiWidth - 2 * EDGE_INSET - DIAL_PIXELS - 8);
            texts.add(new Text(text, EDGE_INSET, bottom - i * PixelFont.LINE_HEIGHT, colour(SAND, base * fade * alpha)));
        }
    }

    private void drawDial(float seconds, float alpha) {
        int x0 = guiWidth - EDGE_INSET - DIAL_PIXELS;
        int y0 = guiHeight - EDGE_INSET - DIAL_PIXELS;
        double rotation = (seconds / DIAL_TURN_SECONDS) * TAU;
        double sin = Math.sin(rotation);
        double cos = Math.cos(rotation);
        for (int y = 0; y < DIAL_PIXELS; y++) {
            for (int x = 0; x < DIAL_PIXELS; x++) {
                double dx = x - 7.5;
                double dy = y - 7.5;
                double distance = Math.hypot(dx, dy);
                if (distance > 7.6) {
                    continue;
                }
                int tone;
                if (distance > 6.1) {
                    tone = dx + dy < -1 ? COPPER_LIGHT : dx + dy > 2 ? COPPER_DARK : COPPER;
                } else {
                    tone = -sin * dx + cos * dy > 0 ? DIAL_DAY : DIAL_NIGHT;
                }
                quad(x0 + x, y0 + y, 1, 1, tone, alpha);
            }
        }
        quad(x0 + (int) Math.round(7.5 - sin * 3.6 - 1), y0 + (int) Math.round(7.5 + cos * 3.6 - 1), 2, 2, DIAL_SUN, alpha);
        quad(x0 + (int) Math.round(7.5 + sin * 3.6 - 1), y0 + (int) Math.round(7.5 - cos * 3.6 - 1), 2, 2, DIAL_MOON, alpha);
        quad(x0 + 7, y0 + 1, 2, 2, DIAL_MARK, alpha);
        quad(x0 + 7, y0 + 2, 2, 1, COPPER_LIGHT, alpha);
    }

    private void drawTexts(ElementShader shader, float alpha) {
        if (texts.isEmpty()) {
            return;
        }
        glActiveTexture(GL_TEXTURE0 + UNIT_FONT);
        glBindTexture(GL_TEXTURE_2D, font.texture());
        shader.updateTextureUniform(UNIT_FONT);
        shader.updateRenderTypeUniform(ElementShader.RenderType.FONT);
        buffer.begin(SimpleBufferBuilder.Format.POS_TEX_COLOR, SimpleBufferBuilder.Mode.QUADS);
        for (Text text : texts) {
            font.draw(buffer, text.string(), (text.x() + 1) * scale, (text.y() + 1) * scale, scale, shadow(text.colour()));
            font.draw(buffer, text.string(), text.x() * scale, text.y() * scale, scale, text.colour());
        }
        buffer.draw();
    }

    private void quad(float x, float y, float w, float h, int rgb, float alpha) {
        QuadHelper.loadQuad(buffer, x * scale, (x + w) * scale, y * scale, (y + h) * scale, 0f, 0f, 0f, 0f, colour(rgb, alpha));
    }

    private int textWidth(String text) {
        return font.width(text, 1);
    }

    private String fit(String text, int maxWidth) {
        if (textWidth(text) <= maxWidth) {
            return text;
        }
        String cut = text;
        while (!cut.isEmpty() && textWidth(cut + "...") > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "...";
    }

    private static int shadow(int abgr) {
        return (abgr & 0xFF000000) | ((abgr & 0x00FCFCFC) >> 2);
    }

    private static int colour(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    private static String readResource(String name) throws IOException {
        return new String(readBytes(name), StandardCharsets.UTF_8);
    }

    private static byte[] readBytes(String name) throws IOException {
        try (InputStream in = BootScene.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IOException("Missing resource " + name);
            }
            return in.readAllBytes();
        }
    }

    private static int link(String vertexSource, String fragmentSource) {
        int vertex = compile(GL_VERTEX_SHADER, vertexSource);
        int fragment = compile(GL_FRAGMENT_SHADER, fragmentSource);
        int program = glCreateProgram();
        glBindAttribLocation(program, 0, "corner");
        glAttachShader(program, vertex);
        glAttachShader(program, fragment);
        glLinkProgram(program);
        glDetachShader(program, vertex);
        glDetachShader(program, fragment);
        glDeleteShader(vertex);
        glDeleteShader(fragment);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(program);
            glDeleteProgram(program);
            throw new IllegalStateException("Sky shader link failed: " + log);
        }
        return program;
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException("Sky shader compile failed: " + log);
        }
        return shader;
    }

    private static int loadTexture(String name, int unit) throws IOException {
        byte[] bytes = readBytes(name);
        ByteBuffer encoded = MemoryUtil.memAlloc(bytes.length);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            encoded.put(bytes).flip();
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);
            ByteBuffer pixels = STBImage.stbi_load_from_memory(encoded, width, height, channels, 4);
            if (pixels == null) {
                throw new IOException("Could not decode " + name + ": " + STBImage.stbi_failure_reason());
            }
            int texture = glGenTextures();
            glActiveTexture(GL_TEXTURE0 + unit);
            glBindTexture(GL_TEXTURE_2D, texture);
            glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
            glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
            glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width.get(0), height.get(0), 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            glGenerateMipmap(GL_TEXTURE_2D);
            STBImage.stbi_image_free(pixels);
            return texture;
        } finally {
            MemoryUtil.memFree(encoded);
        }
    }
}
