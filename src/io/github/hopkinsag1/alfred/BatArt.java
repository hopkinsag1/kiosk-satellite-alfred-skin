// SPDX-License-Identifier: Apache-2.0
package io.github.hopkinsag1.alfred;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

/**
 * Draws one frame: the two traces out from the bat, the halo, the bat with
 * its glow, and the spin. Everything is strokes and gradients; the glow is
 * stacked strokes, no blur, so a frame is cheap on a slow tablet. Positions
 * are relative to the view, the bat in its own 64 by 32 units under a matrix.
 */
final class BatArt {
    static final int DARK_ACCENT = 0xFF8FBADC, DARK_DEEP = 0xFF4F7CA0;
    static final int LIGHT_ACCENT = 0xFF4F7CA0, LIGHT_DEEP = 0xFF284664;
    static final int SPIN_NONE = 0, SPIN_DONE = 1, SPIN_EXIT = 2;
    static final float DONE_SECONDS = 1.3f, EXIT_SECONDS = 0.75f;

    private final Trace trace;
    private final boolean compact;
    private final float density;
    private final Path bat = BatPath.build();
    private final Path line = new Path();
    private final Matrix matrix = new Matrix();
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int width, height;
    private int shadersBat, shadersTrace;
    private Shader traceFade, halo, batFill;
    private float shown, glow = 0.2f, breath;
    private long lastNs;

    BatArt(Trace trace, boolean compact, float density) {
        this.trace = trace;
        this.compact = compact;
        this.density = density;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL);
    }

    /**
     * @param level the voice level KS sent, 0 while Reactive activity bar is off
     * @param phase Trace.LISTENING, THINKING, ANSWERING or RESTING
     * @param accent the bat's colour, opaque ARGB: its fill, glow, halo and the spin's rings
     * @param traceColor the traces' colour, opaque ARGB
     * @param amp how far the traces spike, as a share of the view's height
     * @param spinKind SPIN_NONE, SPIN_DONE or SPIN_EXIT
     * @param spinStartNs when the spin started, System.nanoTime
     * @return whether the exit spin has finished, so the view can stop drawing
     */
    boolean draw(Canvas canvas, int w, int h, float level, String phase, int accent, int traceColor, float amp, int spinKind, long spinStartNs) {
        long now = System.nanoTime();
        float dt = lastNs == 0 ? 1 / 60f : Math.min(0.05f, (now - lastNs) / 1e9f);
        lastNs = now;
        float t = (now % 1_000_000_000_000L) / 1e9f;
        if (w != width || h != height || accent != shadersBat || traceColor != shadersTrace) build(w, h, accent, traceColor);

        // Ease toward the latest level: KS sends about 30 a second.
        shown += (level - shown) * (level > shown ? 0.55f : 0.3f);
        float glowTarget = Trace.ANSWERING.equals(phase) ? 0.3f + 0.7f * shown
            : Trace.LISTENING.equals(phase) ? 0.15f + 0.4f * shown
            : Trace.THINKING.equals(phase) ? 0.25f + 0.2f * (float) Math.sin(t * 3) : 0.18f;
        glow += (glowTarget - glow) * (glowTarget > glow ? 0.4f : 0.15f);
        breath += ((Trace.THINKING.equals(phase) ? (float) Math.sin(t * 2.2) : 0) - breath) * 0.1f;
        trace.push(phase, shown, dt, t);

        float cx = w / 2f, cy = compact ? h * 0.46f : h * 0.5f;
        float batW = compact ? w * 0.82f : Math.min(w * 0.34f, h * 0.78f);
        float k = batW / BatPath.WIDTH;
        float dx = compact ? Math.max(1, w / 60f) : Math.max(2, w / 300f);
        float rise = amp * h;

        float spinDeg = 0, scale = 1, burst = 0, alpha = 1;
        boolean exitDone = false;
        if (spinKind != SPIN_NONE) {
            float dur = spinKind == SPIN_DONE ? DONE_SECONDS : EXIT_SECONDS;
            float u = Math.min(1, (now - spinStartNs) / 1e9f / dur);
            float e = 1 - (1 - u) * (1 - u) * (1 - u);
            if (spinKind == SPIN_DONE) {
                spinDeg = 3 * 360 * e;
                scale = 1 + 0.32f * (float) Math.sin(u * Math.PI);
                burst = 1 - u;
            } else {
                spinDeg = 2 * 360 * e;
                scale = Math.max(0.001f, 1 - e);
                alpha = 1 - e * e;
                burst = 0.6f * (1 - u);
                exitDone = u >= 1;
            }
        }
        if (exitDone) return true;

        // The traces: mirrored upper and lower lines from the bat to each edge, fading out.
        // The view is as wide on both sides of the bat, so one fade and one line serve all four.
        int n = Math.min(Trace.SIZE, (int) Math.ceil((w / 2f) / dx) + 2);
        float under = (compact ? 3 : 7) * density, main = (compact ? 1.2f : 2.4f) * density;
        // One line is built a frame, from the bat's centre out to the right and down, and drawn four times mirrored.
        line.rewind();
        line.moveTo(0, trace.at(0) * rise);
        for (int i = 1; i < n; i++) line.lineTo(i * dx, trace.at(i) * rise);
        int underColor = withAlpha(traceColor, 0.18f * alpha), mainAlpha = Math.round(255 * alpha);
        for (int side = -1; side <= 1; side += 2) {
            for (int dir = -1; dir <= 1; dir += 2) {
                canvas.save();
                canvas.translate(cx, cy);
                canvas.scale(side, dir);
                stroke.setShader(null);
                stroke.setColor(underColor);
                stroke.setStrokeWidth(under);
                canvas.drawPath(line, stroke);
                stroke.setShader(traceFade);
                stroke.setColor(Color.WHITE);
                stroke.setAlpha(mainAlpha);
                stroke.setStrokeWidth(main);
                canvas.drawPath(line, stroke);
                canvas.restore();
            }
        }
        stroke.setShader(null);

        // Halo and the burst rings of the spin.
        fill.setShader(halo);
        fill.setColor(Color.WHITE);
        fill.setAlpha(Math.round(255 * Math.min(1, (0.10f + 0.38f * glow + 0.3f * burst) * alpha)));
        canvas.drawRect(cx - batW, cy - batW, cx + batW, cy + batW, fill);
        fill.setShader(null);
        if (burst > 0) {
            for (int r = 0; r < 2; r++) {
                float u = 1 - burst + r * 0.25f;
                if (u <= 0 || u >= 1) continue;
                stroke.setColor(withAlpha(accent, 0.5f * (1 - u) * alpha));
                stroke.setStrokeWidth((2 + 6 * (1 - u)) * density);
                canvas.drawCircle(cx, cy, batW * (0.3f + 0.9f * u), stroke);
            }
        }

        // The bat: stacked strokes for the glow, a hairline edge, then the gradient fill.
        canvas.save();
        matrix.reset();
        matrix.postTranslate(-BatPath.WIDTH / 2, -BatPath.HEIGHT / 2);
        float s = k * scale * (1 + 0.02f * breath);
        matrix.postScale(s, s);
        matrix.postRotate(spinDeg);
        matrix.postTranslate(cx, cy);
        canvas.concat(matrix);
        for (int i = 5; i >= 1; i--) {
            stroke.setColor(withAlpha(accent, (0.035f + 0.11f * glow + 0.08f * burst) * alpha / (float) Math.sqrt(i)));
            stroke.setStrokeWidth(i * 2.2f);
            canvas.drawPath(bat, stroke);
        }
        stroke.setColor(withAlpha(accent, 0.9f * alpha));
        stroke.setStrokeWidth(0.5f);
        canvas.drawPath(bat, stroke);
        fill.setShader(batFill);
        fill.setColor(Color.WHITE);
        fill.setAlpha(Math.round(255 * alpha));
        canvas.drawPath(bat, fill);
        fill.setShader(null);
        canvas.restore();
        return false;
    }

    private void build(int w, int h, int accent, int traceColor) {
        width = w; height = h; shadersBat = accent; shadersTrace = traceColor;
        int deep = deep(accent);
        float cx = w / 2f, cy = compact ? h * 0.46f : h * 0.5f;
        float batW = compact ? w * 0.82f : Math.min(w * 0.34f, h * 0.78f);
        int[] fade = {withAlpha(traceColor, 0.95f), withAlpha(traceColor, 0.45f), withAlpha(traceColor, 0)};
        float[] stops = {0, 0.75f, 1};
        // In the trace's own space: from the bat's centre out to the edge. The mirrored draws flip it with the line.
        traceFade = new LinearGradient(0, 0, Math.max(1, cx), 0, fade, stops, Shader.TileMode.CLAMP);
        halo = new RadialGradient(cx, cy, Math.max(1, batW * 0.9f), new int[] {accent, withAlpha(accent, 0)}, null, Shader.TileMode.CLAMP);
        batFill = new LinearGradient(0, 0, 0, BatPath.HEIGHT, accent, deep, Shader.TileMode.CLAMP);
    }

    /** The darker shade the bat's fill runs down to: the Bat Cave pair for its own accents, two thirds of the brightness otherwise. */
    static int deep(int accent) {
        if (accent == DARK_ACCENT) return DARK_DEEP;
        if (accent == LIGHT_ACCENT) return LIGHT_DEEP;
        int r = (accent >> 16 & 0xFF) * 2 / 3, g = (accent >> 8 & 0xFF) * 2 / 3, b = (accent & 0xFF) * 2 / 3;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    static int withAlpha(int color, float alpha) {
        int a = Math.round(255 * Math.max(0, Math.min(1, alpha)));
        return (a << 24) | (color & 0x00FFFFFF);
    }
}
