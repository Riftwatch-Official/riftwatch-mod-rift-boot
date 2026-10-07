package net.riftwatch.boot;

import static org.lwjgl.opengl.GL32C.*;
import static org.lwjgl.stb.STBTruetype.*;

import java.nio.ByteBuffer;
import net.neoforged.fml.earlydisplay.QuadHelper;
import net.neoforged.fml.earlydisplay.STBHelper;
import net.neoforged.fml.earlydisplay.SimpleBufferBuilder;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.system.MemoryStack;

final class PixelFont {
    static final int LINE_HEIGHT = 10;
    static final int CAP_HEIGHT = 7;

    private static final String FONT = "Monocraft.ttf";
    private static final int FONT_BUFFER = 200000;
    private static final int EM_PIXELS = 9;
    private static final int FIRST = 32;
    private static final int COUNT = 127 - FIRST;
    private static final int ATLAS = 128;
    private static final int CELL_W = 8;
    private static final int CELL_H = 14;
    private static final int COLUMNS = ATLAS / CELL_W;
    private static final int COVERAGE_THRESHOLD = 128;

    private final int texture;
    private final int[] advance = new int[COUNT];
    private final int[] offsetX = new int[COUNT];
    private final int[] offsetY = new int[COUNT];
    private final int[] width = new int[COUNT];
    private final int[] height = new int[COUNT];
    private final int[] cellX = new int[COUNT];
    private final int[] cellY = new int[COUNT];

    PixelFont(int unit) {
        ByteBuffer ttf = STBHelper.readFromClasspath(FONT, FONT_BUFFER);
        STBTTFontinfo info = STBTTFontinfo.create();
        if (!stbtt_InitFont(info, ttf)) {
            throw new IllegalStateException("Monocraft could not be loaded");
        }
        float scale = stbtt_ScaleForMappingEmToPixels(info, EM_PIXELS);
        ByteBuffer atlas = BufferUtils.createByteBuffer(ATLAS * ATLAS);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var x0 = stack.mallocInt(1);
            var y0 = stack.mallocInt(1);
            var x1 = stack.mallocInt(1);
            var y1 = stack.mallocInt(1);
            var advanceWidth = stack.mallocInt(1);
            var bearing = stack.mallocInt(1);
            for (int i = 0; i < COUNT; i++) {
                int codepoint = FIRST + i;
                stbtt_GetCodepointHMetrics(info, codepoint, advanceWidth, bearing);
                advance[i] = Math.round(advanceWidth.get(0) * scale);
                stbtt_GetCodepointBitmapBox(info, codepoint, scale, scale, x0, y0, x1, y1);
                int w = Math.min(CELL_W, x1.get(0) - x0.get(0));
                int h = Math.min(CELL_H, y1.get(0) - y0.get(0));
                cellX[i] = (i % COLUMNS) * CELL_W;
                cellY[i] = (i / COLUMNS) * CELL_H;
                offsetX[i] = x0.get(0);
                offsetY[i] = y0.get(0);
                width[i] = Math.max(0, w);
                height[i] = Math.max(0, h);
                if (w > 0 && h > 0) {
                    ByteBuffer glyph = BufferUtils.createByteBuffer(w * h);
                    stbtt_MakeCodepointBitmap(info, glyph, w, h, w, scale, scale, codepoint);
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) {
                            int coverage = glyph.get(y * w + x) & 0xFF;
                            atlas.put((cellY[i] + y) * ATLAS + cellX[i] + x, (byte) (coverage >= COVERAGE_THRESHOLD ? 0xFF : 0));
                        }
                    }
                }
            }
        }
        texture = glGenTextures();
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, texture);
        glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
        glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
        glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, ATLAS, ATLAS, 0, GL_RED, GL_UNSIGNED_BYTE, atlas);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
    }

    int texture() {
        return texture;
    }

    int width(String text, int scale) {
        int total = 0;
        for (int i = 0; i < text.length(); i++) {
            total += advance[index(text.charAt(i))];
        }
        return total * scale;
    }

    void draw(SimpleBufferBuilder buffer, String text, int x, int y, int scale, int colour) {
        int pen = x;
        int baseline = y + CAP_HEIGHT * scale;
        for (int i = 0; i < text.length(); i++) {
            int glyph = index(text.charAt(i));
            if (width[glyph] > 0 && height[glyph] > 0) {
                float left = pen + offsetX[glyph] * scale;
                float top = baseline + offsetY[glyph] * scale;
                float u0 = cellX[glyph] / (float) ATLAS;
                float v0 = cellY[glyph] / (float) ATLAS;
                float u1 = (cellX[glyph] + width[glyph]) / (float) ATLAS;
                float v1 = (cellY[glyph] + height[glyph]) / (float) ATLAS;
                QuadHelper.loadQuad(buffer, left, left + width[glyph] * scale, top, top + height[glyph] * scale, u0, u1, v0, v1, colour);
            }
            pen += advance[glyph] * scale;
        }
    }

    private static int index(char c) {
        return c >= FIRST && c < FIRST + COUNT ? c - FIRST : '?' - FIRST;
    }
}
