// SPDX-License-Identifier: Apache-2.0
package io.github.hopkinsag1.alfred;

import java.util.*;
import me.jxl.kiosk.plugins.OverlayFactory;
import me.jxl.kiosk.plugins.OverlaySpec;
import me.jxl.kiosk.plugins.PluginHost;

/** Exercises the controller against a fake host: no device, no views. */
public final class AlfredSkinTest {
    static final class Host implements PluginHost {
        final Set<String> subscriptions = new HashSet<>();
        final Map<String, CommandCallback> reads = new HashMap<>();
        final Map<String, OverlaySpec> overlays = new HashMap<>();
        int shows, hides;
        public void executeCommand(String command, Map<String, Object> arguments, CommandCallback callback) { assert arguments.isEmpty(); reads.put(command, callback); }
        public void subscribe(String event) { subscriptions.add(event); }
        public void showOverlay(String key, OverlaySpec spec, OverlayFactory factory) { assert key.matches("[a-z][a-z0-9_]{0,39}") : key; overlays.put(key, spec); shows++; }
        public void hideOverlay(String key) { overlays.remove(key); hides++; }
        public void showWindow(String title, String message, String button) {}
        public void hideWindow() {}
        public void log(String message) {}
        public void status(String message, boolean error) {}
    }

    static Map<String, Object> map(Object... pairs) {
        Map<String, Object> value = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) value.put((String) pairs[i], pairs[i + 1]);
        return value;
    }

    public static void main(String[] args) {
        Host host = new Host();
        AlfredPlugin plugin = new AlfredPlugin();
        SkinController skin = new SkinController();
        skin.start(host);
        skin.configure(map("label", " Alfred ", "traceHeight", "Tall", "spin", "Command complete and close"));
        assert host.subscriptions.containsAll(Arrays.asList(SkinController.EVENTS)) && host.subscriptions.contains("voice.state");
        assert host.reads.containsKey("getVoiceState") && host.reads.containsKey("getVoiceText") && host.reads.containsKey("getVoiceAppearance");
        assert "Alfred".equals(skin.label) && skin.traceAmp > 0.3f && skin.spinMode == SkinController.SPIN_ALL;

        // Not picked: the turn is KS's to draw.
        host.reads.get("getVoiceState").onResult(true, map("enabled", true, "state", "idle", "skinSelected", false, "overlay", "hidden"), null);
        skin.onEvent("ks.voice.overlay", map("overlay", "full"));
        assert host.overlays.isEmpty() && skin.drawn() == null;

        // Picked: it draws full screen, on top, passing touches through.
        skin.onEvent("ks.voice.skin", map("selected", true));
        OverlaySpec spec = host.overlays.get(SkinController.KEY);
        assert spec != null && spec.onTop() && !spec.touchable() && !spec.closeOnBack();
        assert "full".equals(skin.drawn()) && skin.spinKind == BatArt.SPIN_NONE;

        // The phases follow voice.state, with the words as the fallback.
        skin.onEvent("ks.voice.state", map("state", "listening"));
        assert Trace.LISTENING.equals(skin.phase());
        skin.onEvent("ks.voice.level", map("level", 0.5));
        assert skin.level == 0.5f;
        skin.onEvent("ks.voice.state", map("state", "processing"));
        skin.onEvent("ks.voice.text", map("command", "is the garage closed", "answer", "", "tools", Collections.emptyList(), "streaming", false));
        assert Trace.THINKING.equals(skin.phase());
        skin.onEvent("ks.voice.state", map("state", "responding"));
        skin.onEvent("ks.voice.text", map("command", "is the garage closed", "answer", "It is, sir.", "tools", Collections.emptyList(), "streaming", false));
        assert Trace.ANSWERING.equals(skin.phase()) && skin.spinKind == BatArt.SPIN_NONE : "no spin while still speaking";

        // Speech ends: the command-complete spin, once.
        skin.onEvent("ks.voice.state", map("state", "idle"));
        assert skin.spinKind == BatArt.SPIN_DONE && Trace.RESTING.equals(skin.phase());
        skin.spinKind = BatArt.SPIN_NONE;
        skin.onEvent("ks.voice.state", map("state", "responding"));
        skin.onEvent("ks.voice.state", map("state", "idle"));
        assert skin.spinKind == BatArt.SPIN_NONE : "one spin a turn";

        // Docked replaces the full look; the spin state resets for the new view.
        skin.onEvent("ks.voice.overlay", map("overlay", "docked"));
        OverlaySpec docked = host.overlays.get(SkinController.KEY);
        assert docked != null && docked != spec && docked.onTop() && !docked.touchable() && "docked".equals(skin.drawn());

        // Hidden with no live view: the overlay goes at once.
        skin.onEvent("ks.voice.overlay", map("overlay", "hidden"));
        assert host.overlays.isEmpty() && skin.drawn() == null && host.hides == 1;

        // A new turn resets the once-a-turn guard.
        skin.onEvent("ks.voice.overlay", map("overlay", "full"));
        skin.onEvent("ks.voice.text", map("command", "", "answer", "", "tools", Collections.emptyList(), "streaming", false));
        skin.onEvent("ks.voice.state", map("state", "responding"));
        skin.onEvent("ks.voice.state", map("state", "idle"));
        assert skin.spinKind == BatArt.SPIN_DONE;

        // Spin off: nothing spins.
        skin.configure(map("spin", "Off"));
        skin.spinKind = BatArt.SPIN_NONE;
        skin.onEvent("ks.voice.text", map("command", "", "answer", "", "tools", Collections.emptyList()));
        skin.onEvent("ks.voice.state", map("state", "responding"));
        skin.onEvent("ks.voice.state", map("state", "idle"));
        assert skin.spinKind == BatArt.SPIN_NONE;

        // Appearance settings reach the fields the view reads.
        skin.onEvent("ks.voice.appearance", map("theme", "light", "background", 0.5, "textScale", 1.5, "reactive", false));
        assert "light".equals(skin.theme) && skin.background == 0.5f && skin.textScale == 1.5f && !skin.reactive;

        // Stop clears everything and ignores late events.
        skin.stop();
        skin.onEvent("ks.voice.skin", map("selected", true));
        assert skin.drawn() == null && skin.level == 0 && skin.command.isEmpty();

        // The plugin entry point wires the controller.
        plugin.start(host, map());
        plugin.stop();
        try { plugin.execute("nope", map()); assert false; } catch (IllegalArgumentException expected) {}
        System.out.println("AlfredSkinTest passed");
    }
}
