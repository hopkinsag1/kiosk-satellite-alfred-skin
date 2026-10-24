// SPDX-License-Identifier: Apache-2.0
package io.github.hopkinsag1.alfred;

import java.util.Arrays;

/**
 * The history behind the two traces: one sample a frame, newest at the bat,
 * travelling out to both edges of the screen. Pure Java, so it is testable
 * without a device, and kept by the controller so a change of overlay mode
 * (which builds a new view) keeps the trace running.
 */
final class Trace {
    static final int SIZE = 720;
    /** Values for a frame: what the trace shows for each part of the turn. */
    static final String LISTENING = "listening", THINKING = "thinking", ANSWERING = "answering", RESTING = "resting";

    private final float[] hist = new float[SIZE];
    private int head;
    private int count;
    private float heart;

    Trace() {
        Arrays.fill(hist, 0.03f);
    }

    /**
     * Adds the frame's sample.
     *
     * @param phase which part of the turn the overlay is in
     * @param level the eased voice level, 0 to 1
     * @param dt seconds since the previous frame
     * @param t seconds, any origin, for the idle ripple
     */
    void push(String phase, float level, float dt, float t) {
        float v;
        if (THINKING.equals(phase)) {
            // A heartbeat once a second, travelling out from the bat.
            heart = (heart + dt) % 1f;
            v = qrs(heart) * 0.9f + 0.02f;
        } else if (LISTENING.equals(phase) || ANSWERING.equals(phase)) {
            // The level is an envelope; per-sample jitter gives it the spiky look of audio.
            float gain = LISTENING.equals(phase) ? 0.7f : 1f;
            v = level * gain * (0.35f + 0.65f * hash(count));
            if (level < 0.02f) v = 0.02f + 0.015f * hash(count * 3);
        } else {
            v = 0.025f + 0.02f * (float) Math.sin(t * 1.4) + 0.01f * hash(count);
        }
        head = (head + 1) % SIZE;
        hist[head] = v;
        count++;
    }

    /** The sample {@code back} frames ago; 0 is the newest. */
    float at(int back) {
        return hist[((head - back) % SIZE + SIZE) % SIZE];
    }

    int count() {
        return count;
    }

    /** One heartbeat over u in [0, 1): a small P bump, the sharp QRS spike and the T bump. */
    static float qrs(float u) {
        return bump(u, .18f, .03f, .18f) + bump(u, .30f, .010f, 1f) + bump(u, .33f, .012f, .35f) + bump(u, .48f, .035f, .28f);
    }

    private static float bump(float u, float centre, float width, float height) {
        float d = (u - centre) / width;
        return height * (float) Math.exp(-d * d);
    }

    /** A repeatable pseudo-random value in [0, 1) for a sample index. */
    static float hash(int n) {
        double x = Math.sin(n * 12.9898) * 43758.5453;
        return (float) (x - Math.floor(x));
    }
}
