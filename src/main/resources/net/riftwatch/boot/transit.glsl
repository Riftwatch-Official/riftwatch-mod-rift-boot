uniform float fmVarFloat_rw_preview_start;
uniform int fmVarExists_rw_preview_start;
uniform float fmVarFloat_rw_server_day_2;
uniform int fmVarExists_rw_server_day_2;

const bool SHOW_HUD = false;
const bool SHOW_STATUS = false;
const float PI = 3.14159265;
const float TAU = 6.28318531;
const float FRAME_ASPECT = 1.77777778;
const float LOCK_TOP_FRACTION = 0.0763;
const float TAGLINE_BOX_FRACTION = 0.2793;
const float TAGLINE_BOX_HEIGHT = 2.466;
const float HUD_FRACTION = 0.3853;
const vec2 HUD_BOX = vec2(18.48, 3.19);
const vec2 DIAL_OFFSET = vec2(0.63, 0.596);
const float DIAL_SIZE = 2.0;
const float READOUT_X = 3.37;
const float READOUT_UNIT = 0.088;
const vec2 BAR_SPAN = vec2(0.15, 0.45);
const float SEAM_FRACTION = 0.56;
const float SEAM_DROP = 0.028;
const float TERRAIN_BLOCK = 3.0;
const float ARC_EXPONENT = 0.35;
const float LUNAR_MONTH_DAYS = 29.530588;
const int NEW_MOON_REFERENCE = 947182440;
const float PREVIEW_SECONDS = 20.0;
const float PREVIEW_DRIFT = 14.0;
const float PREVIEW_GROW = 1.615;
const float STATUS_TEXT_OFFSET = 50.0;
const float STATUS_WIDTH = 46.0;
const float STATUS_MIN_WIDTH = 206.0;
const float STATUS_PAD_TOP = 9.0;
const float STATUS_HEIGHT = 37.0;
const float STATUS_TRACK_TOP = 16.0;
const float STATUS_TRACK_WIDTH = 24.0;
const float STATUS_TRACK_HEIGHT = 3.0;
const float STATUS_SWEEP_SECONDS = 1.5;
const float STATUS_SWEEP_SHARE = 0.26;

const vec3 NIGHT_TOP = vec3(7.0, 5.0, 9.0);
const vec3 NIGHT_HORIZON = vec3(24.0, 15.0, 13.0);
const vec3 DAY_TOP = vec3(46.0, 52.0, 61.0);
const vec3 DAY_HORIZON = vec3(92.0, 68.0, 50.0);
const vec3 DAWN_TOP = vec3(38.0, 24.0, 36.0);
const vec3 DAWN_HORIZON = vec3(176.0, 98.0, 52.0);
const vec3 DUSK_TOP = vec3(32.0, 17.0, 30.0);
const vec3 DUSK_HORIZON = vec3(158.0, 64.0, 30.0);
const vec3 SUN_BODY_A = vec3(255.0, 214.0, 150.0);
const vec3 SUN_BODY_B = vec3(255.0, 168.0, 96.0);
const vec3 SUN_CORE_A = vec3(255.0, 244.0, 214.0);
const vec3 SUN_CORE_B = vec3(255.0, 214.0, 160.0);
const vec3 MOON_FACE = vec3(233.0, 224.0, 207.0);
const vec3 MOON_CRATER = vec3(196.0, 184.0, 165.0);
const vec3 DAWN_GLOW = vec3(232.0, 150.0, 90.0);
const vec3 DUSK_GLOW = vec3(214.0, 104.0, 52.0);
const vec3 STAR = vec3(240.0, 226.0, 208.0);
const vec3 CLOUD_NIGHT = vec3(60.0, 48.0, 52.0);
const vec3 CLOUD_DAY = vec3(244.0, 226.0, 206.0);
const vec3 CLOUD_DAWN = vec3(238.0, 168.0, 116.0);
const vec3 CLOUD_DUSK = vec3(214.0, 120.0, 78.0);
const vec3 CLOUD_HIGHLIGHT = vec3(255.0, 244.0, 228.0);
const vec3 CLOUD_SHADE = vec3(20.0, 14.0, 12.0);
const vec3 TERRAIN_BASE = vec3(12.0, 8.0, 7.0);
const vec3 TERRAIN_RIM = vec3(255.0, 220.0, 180.0);
const vec3 LAMP = vec3(255.0, 190.0, 110.0);
const vec3 FORGE = vec3(17.0, 11.0, 8.0);
const vec3 EMBER = vec3(42.0, 26.0, 18.0);
const vec3 COPPER = vec3(217.0, 138.0, 79.0);
const vec3 COPPER_LIGHT = vec3(232.0, 180.0, 138.0);
const vec3 COPPER_DARK = vec3(138.0, 75.0, 35.0);
const vec3 SAND = vec3(201.0, 165.0, 138.0);
const vec3 MUTED = vec3(168.0, 138.0, 115.0);
const vec3 HAIRLINE = vec3(58.0, 40.0, 29.0);
const vec3 EDGE = vec3(7.0, 4.0, 3.0);

const int FONT[119] = int[119](
    14, 17, 19, 21, 25, 17, 14,
    4, 12, 4, 4, 4, 4, 14,
    14, 17, 1, 2, 4, 8, 31,
    31, 2, 4, 2, 1, 17, 14,
    2, 6, 10, 18, 31, 2, 2,
    31, 16, 30, 1, 1, 17, 14,
    6, 8, 16, 30, 17, 17, 14,
    31, 1, 2, 4, 8, 8, 8,
    14, 17, 17, 14, 17, 17, 14,
    14, 17, 17, 15, 1, 2, 12,
    30, 17, 17, 17, 17, 17, 30,
    0, 0, 14, 1, 15, 17, 15,
    0, 0, 17, 17, 15, 1, 14,
    0, 0, 0, 0, 0, 4, 8,
    0, 4, 4, 0, 4, 4, 0,
    0, 0, 0, 14, 0, 0, 0,
    0, 0, 0, 0, 0, 0, 0
);

uint hashU(uint x) {
    x ^= x >> 16u;
    x *= 0x7feb352du;
    x ^= x >> 15u;
    x *= 0x846ca68bu;
    x ^= x >> 16u;
    return x;
}

float hash3(int a, int b, int seed) {
    uint h = hashU(uint(a) * 1597334677u ^ hashU(uint(b) * 3812015801u ^ uint(seed) * 2654435761u));
    return float(h) / 4294967295.0;
}

int wrapInt(int v, int m) {
    int r = v - (v / m) * m;
    return r < 0 ? r + m : r;
}

float gridNoise(vec2 p, int w, int h, int seed) {
    vec2 f = fract(p);
    int x0 = int(floor(p.x));
    int y0 = int(floor(p.y));
    f = f * f * (3.0 - 2.0 * f);
    float a = hash3(wrapInt(x0, w), wrapInt(y0, h), seed);
    float b = hash3(wrapInt(x0 + 1, w), wrapInt(y0, h), seed);
    float c = hash3(wrapInt(x0, w), wrapInt(y0 + 1, h), seed);
    float d = hash3(wrapInt(x0 + 1, w), wrapInt(y0 + 1, h), seed);
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

vec4 over(vec4 base, vec3 color, float alpha) {
    return vec4(mix(base.rgb, color / 255.0, clamp(alpha, 0.0, 1.0)), 1.0);
}

struct Sky {
    float angle;
    float day;
    float night;
    float warm;
    bool dawn;
    vec3 top;
    vec3 horizon;
};

Sky sunState(float minutes) {
    Sky s;
    s.angle = ((minutes - 360.0) / 720.0) * PI;
    float height = sin(s.angle);
    s.day = smoothstep(-0.3, 0.25, height);
    s.night = 1.0 - s.day;
    s.warm = exp(-pow(height / 0.24, 2.0));
    s.dawn = cos(s.angle) > 0.0;
    vec3 top = mix(NIGHT_TOP, DAY_TOP, s.day);
    vec3 horizon = mix(NIGHT_HORIZON, DAY_HORIZON, s.day);
    s.top = mix(top, s.dawn ? DAWN_TOP : DUSK_TOP, s.warm * 0.75);
    s.horizon = mix(horizon, s.dawn ? DAWN_HORIZON : DUSK_HORIZON, s.warm * 0.9);
    return s;
}

float signOne(float v) {
    return v < 0.0 ? -1.0 : 1.0;
}

vec2 arcPoint(float angle, vec2 center, vec2 radius) {
    float c = cos(angle);
    float s = sin(angle);
    return vec2(
        floor(center.x - radius.x * signOne(c) * pow(abs(c), ARC_EXPONENT) + 0.5),
        floor(center.y - radius.y * signOne(s) * pow(abs(s), ARC_EXPONENT) + 0.5)
    );
}

bool inRect(vec2 p, vec2 origin, vec2 size) {
    return p.x >= origin.x && p.y >= origin.y && p.x < origin.x + size.x && p.y < origin.y + size.y;
}

bool cloudFilled(int c, int r, int columns, int period, int seed, float cut) {
    if (r < 0 || r > 3) {
        return false;
    }
    int cc = wrapInt(c, columns);
    float centre = 1.0 - abs(float(r) - 1.5) / 2.0;
    float n = gridNoise(vec2(float(cc) / float(columns) * float(period), float(r) * 0.55 + 0.3), period, 4, seed);
    return n * (0.55 + centre * 0.6) > cut;
}

vec4 drawClouds(vec4 color, vec2 u, int columns, vec2 cell, float layerY, float offset, int seed, float cut, vec3 tint, float alpha) {
    if (alpha <= 0.01) {
        return color;
    }
    float dy = u.y - layerY;
    if (dy < 0.0 || dy >= cell.y * 4.0) {
        return color;
    }
    int period = max(8, int(floor(float(columns) / 5.0 + 0.5)));
    float width = float(columns) * cell.x;
    float local = u.x + floor(mod(offset, width));
    int c = int(floor(local / cell.x));
    int r = int(floor(dy / cell.y));
    if (!cloudFilled(c, r, columns, period, seed, cut)) {
        return color;
    }
    float inner = dy - float(r) * cell.y;
    vec3 fill = tint;
    if (inner < 1.0 && !cloudFilled(c, r - 1, columns, period, seed, cut)) {
        fill = mix(tint, CLOUD_HIGHLIGHT, 0.35);
    } else if (inner >= cell.y - 1.0 && !cloudFilled(c, r + 1, columns, period, seed, cut)) {
        fill = mix(tint, CLOUD_SHADE, 0.35);
    }
    return over(color, fill, alpha);
}

int terrainHeight(int c, int columns, int maxH, bool narrow) {
    float v = gridNoise(vec2(float(c) / float(columns) * 12.0 * (narrow ? 0.7 : 1.3), 0.0), 12, 1, 905);
    return min(maxH, 1 + int(floor(v * v * float(maxH - 1) * 1.4 + 0.5)));
}

float baseline(float x, float heightU, float seamU, float widthU) {
    return heightU - seamU * (x / widthU);
}

int terrainTop(int c, int columns, int maxH, bool narrow, float heightU, float seamU, float widthU) {
    float x = float(c) * TERRAIN_BLOCK + TERRAIN_BLOCK / 2.0;
    return int(floor(baseline(x, heightU, seamU, widthU) + 0.5)) - terrainHeight(c, columns, maxH, narrow) * int(TERRAIN_BLOCK);
}

int treeSize(int c, int columns, int maxH, bool narrow) {
    if (c < 2 || c >= columns - 3) {
        return 0;
    }
    float chance = narrow ? 0.08 : 0.07;
    if (hash3(c, 1, 41) >= chance || hash3(c - 1, 1, 41) < chance || hash3(c - 2, 1, 41) < chance) {
        return 0;
    }
    if (terrainHeight(c, columns, maxH, narrow) != terrainHeight(c - 1, columns, maxH, narrow)) {
        return 0;
    }
    return 1 + (hash3(c, 2, 41) < 0.5 ? 0 : 1);
}

int fontRow(int glyph, int row) {
    return FONT[glyph * 7 + row];
}

int readoutGlyph(int i, int day, int digits, int hh, int mm, out int tone) {
    tone = 0;
    if (i == 0) return 10;
    if (i == 1) return 11;
    if (i == 2) return 12;
    if (i == 3) return 16;
    int k = i - 4;
    if (k < digits) {
        if (day <= 0) return 15;
        int place = digits - 1 - k;
        int d = day;
        for (int n = 0; n < 6; n++) {
            if (n < place) d /= 10;
        }
        return d - (d / 10) * 10;
    }
    k -= digits;
    tone = 1;
    if (k == 0) return 13;
    if (k == 1) return 16;
    tone = 2;
    if (k == 2) return hh / 10;
    if (k == 3) return hh - (hh / 10) * 10;
    if (k == 4) return 14;
    if (k == 5) return mm / 10;
    if (k == 6) return mm - (mm / 10) * 10;
    return 16;
}

bool glyphPixel(int glyph, vec2 local) {
    if (local.x < 0.0 || local.y < 0.0 || local.x >= 5.0 || local.y >= 7.0) {
        return false;
    }
    int row = fontRow(glyph, int(local.y));
    int bit = 4 - int(local.x);
    return ((row >> bit) & 1) == 1;
}

vec3 dialPixel(ivec2 q, float minutes, out bool hit) {
    hit = false;
    float rot = (minutes / 1440.0) * TAU;
    vec2 sunMark = vec2(floor(7.5 - sin(rot) * 3.6 - 1.0 + 0.5), floor(7.5 + cos(rot) * 3.6 - 1.0 + 0.5));
    vec2 moonMark = vec2(floor(7.5 + sin(rot) * 3.6 - 1.0 + 0.5), floor(7.5 - cos(rot) * 3.6 - 1.0 + 0.5));
    vec2 p = vec2(q);
    if (inRect(p, vec2(7.0, 2.0), vec2(2.0, 1.0))) {
        hit = true;
        return COPPER_LIGHT;
    }
    if (inRect(p, vec2(7.0, 1.0), vec2(2.0, 2.0))) {
        hit = true;
        return FORGE;
    }
    if (inRect(p, moonMark, vec2(2.0))) {
        hit = true;
        return vec3(233.0, 224.0, 207.0);
    }
    if (inRect(p, sunMark, vec2(2.0))) {
        hit = true;
        return vec3(255.0, 216.0, 143.0);
    }
    float dx = p.x - 7.5;
    float dy = p.y - 7.5;
    float dist = length(vec2(dx, dy));
    if (dist > 7.6) {
        return vec3(0.0);
    }
    hit = true;
    if (dist > 6.1) {
        return dx + dy < -1.0 ? COPPER_LIGHT : (dx + dy > 2.0 ? COPPER_DARK : COPPER);
    }
    return -sin(rot) * dx + cos(rot) * dy > 0.0 ? vec3(95.0, 107.0, 120.0) : vec3(28.0, 24.0, 38.0);
}

float hexSdf(vec2 p, float apothem) {
    const vec3 k = vec3(-0.866025404, 0.5, 0.577350269);
    p = abs(p.yx);
    p -= 2.0 * min(dot(k.xy, p), 0.0) * k.xy;
    p -= vec2(clamp(p.x, -k.z * apothem, k.z * apothem), apothem);
    return length(p) * sign(p.y);
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    vec2 res = iResolution.xy;
    float scale = max(1.0, fmGuiScale);
    float css = max(1.0, scale * 0.5);
    vec2 px = vec2(fragCoord.x, res.y - fragCoord.y);
    vec2 gui = vec2(ceil(res.x / scale), ceil(res.y / scale));
    float frame = min(res.x, res.y * FRAME_ASPECT);
    float cq = min(gui.x, gui.y * FRAME_ASPECT) / 100.0;
    float centerX = floor(gui.x / 2.0);
    float hudTop = floor(gui.y * HUD_FRACTION);
    float seamGui = floor(gui.y * SEAM_FRACTION + 0.5);

    float liveSeconds = iDate.w;
    float utcNow = float(fmUnixTimeSeconds - (fmUnixTimeSeconds / 86400) * 86400) + float(fmUnixTimeMilliseconds) / 1000.0;
    float elapsed = 1.0e9;
    if (SHOW_HUD && fmVarExists_rw_preview_start == 1 && fmVarFloat_rw_preview_start >= 0.0) {
        elapsed = mod(utcNow - fmVarFloat_rw_preview_start + 86400.0, 86400.0);
    }
    bool preview = elapsed < PREVIEW_SECONDS;
    float progress = 0.0;
    float minutes = liveSeconds / 60.0;
    float dayAdd = 0.0;
    if (preview) {
        progress = elapsed / PREVIEW_SECONDS;
        float startMinutes = mod((liveSeconds - elapsed) / 60.0 + 1440.0, 1440.0);
        float total = startMinutes + progress * 1440.0;
        minutes = mod(total, 1440.0);
        dayAdd = floor(total / 1440.0);
    }
    float drift = iTime + (PREVIEW_DRIFT - 1.0) * min(elapsed, PREVIEW_SECONDS);
    Sky st = sunState(minutes);

    float seamLeft = seamGui * scale;
    float drop = res.x * SEAM_DROP;
    float seamAt = seamLeft - drop * (px.x / res.x);

    vec2 bgUv = px / res - vec2(0.5, 0.45);
    float bgDist = length(bgUv / vec2(0.5, 0.55)) / 1.41421356;
    vec4 color = vec4(mix(EMBER, FORGE, clamp(bgDist / 0.7, 0.0, 1.0)) / 255.0, 1.0);

    if (px.y < seamAt) {
        float pixel = max(2.0, floor(frame / 250.0 + 0.5));
        vec2 u = floor(px / pixel);
        float widthU = ceil(res.x / pixel);
        float heightU = ceil(seamLeft / pixel);
        float seamU = drop / pixel;
        bool narrow = frame / css < 600.0;
        float spread = max(1.0, widthU / ceil(frame / pixel));
        float sun = narrow ? 7.0 : 11.0;
        float topY = sun / 2.0 + 3.0;
        vec2 arcCenter = vec2(widthU / 2.0, heightU - seamU * 0.5);
        vec2 arcRadius = vec2(widthU / 2.0 - sun / 2.0 - 6.0, arcCenter.y - topY);

        float t = clamp(u.y / heightU, 0.0, 1.0);
        vec3 mid = mix(st.top, st.horizon, 0.55);
        vec3 grad = t < 0.62 ? mix(st.top, mid, t / 0.62) : mix(mid, st.horizon, (t - 0.62) / 0.38);
        color = vec4(grad / 255.0, 1.0);

        vec2 sunPos = arcPoint(st.angle, arcCenter, arcRadius);
        if (st.warm > 0.05) {
            vec2 glowAt = vec2(sunPos.x, min(sunPos.y, heightU));
            float d = length(u - glowAt) / (widthU * 0.55);
            color = over(color, st.dawn ? DAWN_GLOW : DUSK_GLOW, 0.34 * st.warm * (1.0 - clamp(d, 0.0, 1.0)));
        }

        if (st.night > 0.04 && u.y < heightU * 0.78) {
            int sx = int(u.x);
            int sy = int(u.y);
            if (hash3(sx, sy, 1221) < 1.0 / 260.0) {
                float brightness = 0.35 + hash3(sx, sy, 1222) * 0.65;
                float frequency = 0.6 + hash3(sx, sy, 1223) * 2.2;
                float phase = hash3(sx, sy, 1224) * TAU;
                float twinkle = 0.55 + 0.45 * sin(iTime * frequency + phase);
                float q = floor(st.night * brightness * twinkle * 4.0 + 0.5) / 4.0;
                color = over(color, STAR, q);
            }
        }

        float moonAngle = st.angle + PI;
        if (moonAngle > PI * 1.5) {
            moonAngle -= TAU;
        }
        if (st.angle > -0.35 && st.angle < PI + 0.35) {
            vec3 body = mix(SUN_BODY_A, SUN_BODY_B, st.warm);
            vec3 core = mix(SUN_CORE_A, SUN_CORE_B, st.warm);
            vec2 corner = floor(sunPos - sun / 2.0 + 0.5);
            float ring = sun < 9.0 ? 1.0 : 2.0;
            if (inRect(u, corner - ring * 2.0, vec2(sun + ring * 4.0))) color = over(color, body, 0.14);
            if (inRect(u, corner - ring, vec2(sun + ring * 2.0))) color = over(color, body, 0.28);
            if (inRect(u, corner, vec2(sun))) color = over(color, body, 1.0);
            if (inRect(u, corner + ring, vec2(sun - ring * 2.0))) color = over(color, core, 1.0);
        }
        if (moonAngle > -0.35 && moonAngle < PI + 0.35) {
            vec2 moonPos = arcPoint(moonAngle, arcCenter, arcRadius);
            float size = sun - 1.0;
            vec2 corner = floor(moonPos - size / 2.0 + 0.5);
            float halo = length(u + 0.5 - moonPos) / (size * 1.6);
            color = over(color, vec3(232.0, 220.0, 200.0), 0.16 * (1.0 - clamp(halo, 0.0, 1.0)));
            if (inRect(u, corner, vec2(size))) {
                float ageDays = float(fmUnixTimeSeconds - NEW_MOON_REFERENCE) / 86400.0 + dayAdd;
                float age = mod(ageDays, LUNAR_MONTH_DAYS);
                float lit = (1.0 - cos((age / LUNAR_MONTH_DAYS) * TAU)) / 2.0;
                bool waxing = age < LUNAR_MONTH_DAYS / 2.0;
                ivec2 m = ivec2(u - corner);
                vec3 face = MOON_FACE;
                if ((m == ivec2(2, 1)) || (m == ivec2(3, 1)) || (m == ivec2(2, 2)) || (m == ivec2(6, 2)) || (m == ivec2(7, 3)) || (m == ivec2(6, 3)) || (m == ivec2(3, 6)) || (m == ivec2(2, 6)) || (m == ivec2(3, 7)) || (m == ivec2(7, 7)) || (m == ivec2(7, 6)) || (m == ivec2(1, 4)) || (m == ivec2(5, 5)) || (m == ivec2(8, 5))) {
                    face = MOON_CRATER;
                }
                color = over(color, face, 1.0);
                float dark = floor((1.0 - lit) * size + 0.5);
                float mx = float(m.x);
                bool shaded = waxing ? mx < dark : mx >= size - dark;
                if (dark > 0.0 && shaded) {
                    color = over(color, mix(st.top, MOON_FACE, 0.22), 0.95);
                }
            }
        }

        vec3 cloudTint = mix(mix(CLOUD_NIGHT, CLOUD_DAY, st.day), st.dawn ? CLOUD_DAWN : CLOUD_DUSK, st.warm * 0.8);
        float lockTop = gui.y * LOCK_TOP_FRACTION * scale / pixel;
        float lockBottom = (gui.y * TAGLINE_BOX_FRACTION + TAGLINE_BOX_HEIGHT * cq) * scale / pixel;
        float cloudShade = 0.45 + 0.55 * st.day;
        color = drawClouds(color, u, int(ceil((narrow ? 46.0 : 84.0) * spread)), vec2(4.0, 2.0), floor(lockBottom + 2.0 + 0.5), drift * 0.45 * 0.8, 77, 0.5, cloudTint, 0.26 * cloudShade);
        color = drawClouds(color, u, int(ceil((narrow ? 30.0 : 56.0) * spread)), vec2(6.0, 3.0), floor(max(max(topY - 8.0, lockTop - 15.0), 1.0) + 0.5), drift * 1.1 * 0.8 + 37.0, 311, 0.56, cloudTint, 0.34 * cloudShade);

        int columns = int(ceil(widthU / TERRAIN_BLOCK)) + 1;
        int maxH = narrow ? 3 : 4;
        int c = int(floor(u.x / TERRAIN_BLOCK));
        vec3 ground = mix(TERRAIN_BASE, st.horizon, 0.1);
        vec3 rim = mix(st.horizon, TERRAIN_RIM, 0.25);
        float rimAlpha = 0.55 - st.night * 0.3;
        int top = terrainTop(c, columns, maxH, narrow, heightU, seamU, widthU);
        int y = int(u.y);
        int x = int(u.x);
        bool solid = y >= top;
        bool rimHit = y == top;
        for (int k = -1; k <= 1; k++) {
            int cc = c + k;
            int size = treeSize(cc, columns, maxH, narrow);
            if (size == 0) continue;
            int tTop = terrainTop(cc, columns, maxH, narrow, heightU, seamU, widthU);
            int tx = cc * int(TERRAIN_BLOCK) + 1;
            int trunk = 4 + size;
            int crown = tTop - trunk - 6;
            if (x == tx && y >= tTop - trunk && y < tTop) solid = true;
            if (y == crown && x >= tx - 2 && x <= tx + 2) { solid = true; rimHit = true; }
            if (y >= crown + 1 && y <= crown + 4 && x >= tx - 3 && x <= tx + 3) solid = true;
            if (y == crown + 5 && x >= tx - 2 && x <= tx + 2) solid = true;
            if (y == crown + 1 && (x == tx - 3 || x == tx + 3)) rimHit = true;
        }
        if (solid) {
            color = over(color, ground, 1.0);
            if (rimHit) color = over(color, rim, rimAlpha);
            if (st.night > 0.3 && c >= 2 && c < columns - 3 && hash3(c, 3, 41) < 0.05 && x == c * int(TERRAIN_BLOCK) + 1 && y == top + 1) {
                float flicker = 0.75 + 0.25 * sin(iTime * 7.0 + hash3(c, 4, 41) * TAU);
                color = over(color, LAMP, ((st.night - 0.3) / 0.7) * flicker);
            }
        }

        vec2 vig = (px / vec2(res.x, seamLeft) - vec2(0.5, 0.52)) / vec2(0.6, 0.55);
        color = over(color, FORGE, 0.42 * (1.0 - clamp(length(vig) / 0.7, 0.0, 1.0)));
    }

    if (px.y > seamAt + 3.0 * css) {
        float r = max(10.0 * css, frame * 0.0175);
        float dx = sqrt(3.0) * r;
        float dy = 1.5 * r;
        float yTop = seamLeft - drop - r;
        float row0 = floor((px.y - yTop) / dy + 0.5);
        vec2 best = vec2(0.0);
        float bestDist = 1.0e9;
        for (int k = -1; k <= 1; k++) {
            float row = row0 + float(k);
            float offset = mod(row, 2.0) > 0.5 ? dx / 2.0 : 0.0;
            float cy = yTop + row * dy;
            float cx = floor((px.x - offset) / dx + 0.5) * dx + offset;
            for (int j = -1; j <= 1; j++) {
                vec2 centre = vec2(cx + float(j) * dx, cy);
                float d = length(px - centre);
                if (d < bestDist) {
                    bestDist = d;
                    best = centre;
                }
            }
        }
        vec2 mouse = vec2(fmMouse.x, res.y - fmMouse.y);
        float reach = max(90.0 * css, frame * 0.12);
        float md = length(best - mouse);
        float heat = md < reach ? pow(1.0 - md / reach, 1.6) : 0.0;
        float fade = clamp((best.y - (seamLeft - drop * (best.x / res.x))) / (r * 3.0), 0.0, 1.0);
        float alpha = (0.06 + 0.22 * heat) * (0.35 + 0.65 * fade);
        vec2 local = px - best;
        float outer = abs(hexSdf(local, r * 0.94 * 0.866025404));
        float inner = hexSdf(local, r * 0.8 * 0.866025404);
        float stroke = 0.5 * css;
        if (heat > 0.05 && inner < 0.0) color = over(color, COPPER, 0.08 * heat);
        color = over(color, COPPER, alpha * clamp(stroke + 0.5 - outer, 0.0, 1.0));
        color = over(color, COPPER, alpha * 0.55 * clamp(stroke + 0.5 - abs(inner), 0.0, 1.0));
    }

    float crack = abs(px.y - seamAt);
    color = over(color, COPPER, 0.22 * clamp(0.5 * css + 0.5 - abs(px.y - (seamAt + 4.0 * css)), 0.0, 1.0));
    color = over(color, SAND, 0.7 * clamp(0.6 * css + 0.5 - crack, 0.0, 1.0));

    float unit = cq * scale;
    vec2 hudSize = (HUD_BOX + vec2(preview ? PREVIEW_GROW : 0.0, 0.0)) * unit;
    vec2 hudMin = vec2(centerX * scale - floor(hudSize.x / 2.0), hudTop * scale);
    vec2 h = px - hudMin;
    if (SHOW_HUD && h.x >= 0.0 && h.y >= 0.0 && h.x < hudSize.x && h.y < hudSize.y) {
        bool border = h.x < css || h.y < css || h.x >= hudSize.x - css || h.y >= hudSize.y - css;
        color = border ? over(color, HAIRLINE, 1.0) : over(color, vec3(14.0, 9.0, 7.0), 0.78);
        if (!border && (h.x < 2.0 * css || h.y < 2.0 * css)) {
            color = over(color, COPPER_LIGHT, 0.05);
        }

        float dialUnit = DIAL_SIZE * unit / 16.0;
        ivec2 dq = ivec2(floor((h - DIAL_OFFSET * unit) / dialUnit));
        if (dq.x >= 0 && dq.y >= 0 && dq.x < 16 && dq.y < 16) {
            bool hit;
            vec3 dial = dialPixel(dq, minutes, hit);
            if (hit) color = over(color, dial, 1.0);
        }

        float serverDay = fmVarExists_rw_server_day_2 == 1 ? floor(fmVarFloat_rw_server_day_2 + 0.5) : 0.0;
        int day = serverDay > 0.0 ? int(serverDay + dayAdd) : 0;
        int digits = 2;
        if (day > 0) {
            digits = 1;
            int v = day;
            for (int n = 0; n < 5; n++) {
                if (v >= 10) {
                    v /= 10;
                    digits++;
                }
            }
        }
        int hh = int(floor(minutes / 60.0)) - (int(floor(minutes / 60.0)) / 24) * 24;
        int mm = int(floor(mod(minutes, 60.0)));
        float textUnit = READOUT_UNIT * unit;
        vec2 tl = (h - vec2(READOUT_X * unit, (hudSize.y - 7.0 * textUnit) / 2.0)) / textUnit;
        float shadow = max(1.0, 2.0 * css / textUnit);
        for (int pass = 0; pass < 2; pass++) {
            vec2 shifted = floor(pass == 0 ? tl - vec2(shadow) : tl);
            if (shifted.y < 0.0 || shifted.y >= 7.0 || shifted.x < 0.0) continue;
            int i = int(floor(shifted.x / 6.0));
            if (i >= 4 + digits + 7) continue;
            int tone;
            int glyph = readoutGlyph(i, day, digits, hh, mm, tone);
            if (glyphPixel(glyph, vec2(shifted.x - float(i) * 6.0, shifted.y))) {
                if (pass == 0) {
                    color = over(color, vec3(0.0), 0.55);
                } else {
                    color = over(color, tone == 0 ? SAND : (tone == 1 ? MUTED : COPPER_LIGHT), 1.0);
                }
            }
        }
    }

    if (SHOW_STATUS) {
        float textTop = floor(gui.y / 2.0) - STATUS_TEXT_OFFSET;
        vec2 panelSize = vec2(max(floor(STATUS_WIDTH * cq), STATUS_MIN_WIDTH), STATUS_HEIGHT) * scale;
        vec2 panelMin = vec2(centerX * scale - floor(panelSize.x / 2.0), (textTop - STATUS_PAD_TOP) * scale);
        vec2 s = px - panelMin;
        if (s.x >= 0.0 && s.y >= 0.0 && s.x < panelSize.x && s.y < panelSize.y) {
            bool border = s.x < css || s.y < css || s.x >= panelSize.x - css || s.y >= panelSize.y - css;
            color = border ? over(color, HAIRLINE, 1.0) : over(color, vec3(14.0, 9.0, 7.0), 0.78);
            if (!border && (s.x < 2.0 * css || s.y < 2.0 * css)) {
                color = over(color, COPPER_LIGHT, 0.05);
            }
            vec2 trackSize = vec2(floor(STATUS_TRACK_WIDTH * cq), STATUS_TRACK_HEIGHT) * scale;
            vec2 t = s - vec2(floor((panelSize.x - trackSize.x) / 2.0), (STATUS_PAD_TOP + STATUS_TRACK_TOP) * scale);
            if (t.x >= 0.0 && t.y >= 0.0 && t.x < trackSize.x && t.y < trackSize.y) {
                bool edge = t.x < css || t.y < css || t.x >= trackSize.x - css || t.y >= trackSize.y - css;
                vec3 track = edge ? EDGE : vec3(11.0, 7.0, 5.0);
                float inner = trackSize.x - 2.0 * css;
                float start = (fract(iTime / STATUS_SWEEP_SECONDS) * (1.0 + STATUS_SWEEP_SHARE) - STATUS_SWEEP_SHARE) * inner;
                float along = t.x - css;
                if (!edge && along >= start && along < start + STATUS_SWEEP_SHARE * inner) {
                    track = t.y < trackSize.y / 2.0 ? COPPER_LIGHT : COPPER;
                }
                color = over(color, track, 1.0);
            }
        }
    }

    if (preview) {
        vec2 barMin = vec2(-css, hudSize.y + floor(BAR_SPAN.x * unit));
        vec2 barMax = vec2(hudSize.x + css, hudSize.y + max(floor(BAR_SPAN.y * unit), floor(BAR_SPAN.x * unit) + 4.0 * css));
        if (h.x >= barMin.x && h.x < barMax.x && h.y >= barMin.y && h.y < barMax.y) {
            bool edge = h.x < barMin.x + css || h.x >= barMax.x - css || h.y < barMin.y + css || h.y >= barMax.y - css;
            vec3 bar = edge ? EDGE : vec3(11.0, 7.0, 5.0);
            float fill = barMin.x + css + (barMax.x - barMin.x - 2.0 * css) * progress;
            if (!edge && h.x < fill) {
                bar = h.y < (barMin.y + barMax.y) / 2.0 ? COPPER_LIGHT : COPPER;
            }
            color = over(color, bar, 1.0);
        }
    }

    fragColor = vec4(color.rgb, 1.0);
}
