// SPDX-License-Identifier: Apache-2.0
package io.github.hopkinsag1.alfred;

public final class TraceTest {
    public static void main(String[] args) {
        // The heartbeat peaks at the QRS spike and is nearly flat between beats.
        assert Trace.qrs(0.30f) > 0.99f && Trace.qrs(0.30f) <= 1.4f;
        assert Trace.qrs(0.80f) < 0.01f && Trace.qrs(0.0f) < 0.01f;
        // The hash is repeatable and inside [0, 1).
        for (int i = 0; i < 1000; i++) { float h = Trace.hash(i); assert h >= 0 && h < 1 && h == Trace.hash(i); }
        Trace trace = new Trace();
        assert trace.at(0) == 0.03f && trace.at(Trace.SIZE - 1) == 0.03f;
        // Answering at full level spikes; resting stays low; the newest sample is at 0.
        trace.push(Trace.ANSWERING, 1f, 1 / 60f, 0);
        float newest = trace.at(0);
        assert newest >= 0.35f && newest <= 1f : newest;
        trace.push(Trace.RESTING, 0f, 1 / 60f, 1);
        assert trace.at(1) == newest && trace.at(0) < 0.07f;
        // Silence while listening keeps a faint line rather than nothing.
        trace.push(Trace.LISTENING, 0f, 1 / 60f, 2);
        assert trace.at(0) > 0.01f && trace.at(0) < 0.04f;
        // The ring wraps without losing order.
        for (int i = 0; i < Trace.SIZE * 2; i++) trace.push(Trace.THINKING, 0, 1 / 60f, i / 60f);
        assert trace.count() == Trace.SIZE * 2 + 3;
        float peak = 0; for (int i = 0; i < 60; i++) peak = Math.max(peak, trace.at(i));
        assert peak > 0.5f : "a heartbeat every second: " + peak;
        System.out.println("TraceTest passed");
    }
}
