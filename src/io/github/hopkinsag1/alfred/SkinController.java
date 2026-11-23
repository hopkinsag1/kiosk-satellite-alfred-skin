// SPDX-License-Identifier: Apache-2.0
package io.github.hopkinsag1.alfred;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import me.jxl.kiosk.plugins.KsTheme;
import me.jxl.kiosk.plugins.OverlayFactory;
import me.jxl.kiosk.plugins.OverlaySpec;
import me.jxl.kiosk.plugins.PluginHost;

/**
 * Draws the Voice Satellite turn while this plugin's skin is picked. Follows
 * the voice skin contract: draw only while selected, follow voice.overlay for
 * when to show, and honour the Appearance settings (Theme, Background, Text
 * size, Reactive activity bar). The spin plays when the assistant finishes
 * speaking (voice.state leaves "responding") and when the overlay closes.
 */
final class SkinController {
    static final String KEY = "alfred_bat";
    /** Where the docked bubble sits above the bottom edge, in dp. */
    static final int DOCK_INSET = 24;
    /** The turn's events, followed only while the skin is picked. voice.skin itself is followed all along. */
    static final String[] EVENTS = {"voice.overlay", "voice.level", "voice.text", "voice.appearance", "voice.state"};
    /** The full screen backdrop's opacity when Background is left at Skin default: the Bat Cave's near-solid black. */
    static final float DEFAULT_BACKGROUND = 0.96f;
    /** How long the exit spin keeps the overlay up after KS hides the turn, in ms. */
    static final long EXIT_MS = 750;
    /** How long the command-complete spin plays, in ms. A close that arrives sooner waits for the rest of it. */
    static final long DONE_MS = (long) (BatArt.DONE_SECONDS * 1000);
    static final int SPIN_OFF = 0, SPIN_CLOSE = 1, SPIN_ALL = 2;
    /** The colour settings' defaults, as the manifest declares them. Left at these, the skin keeps its light and dark palettes. */
    static final String DEFAULT_BAT = "#8FBADC", DEFAULT_TRACE = "#8FBADC", DEFAULT_TEXT = "#F2F7FB";

    private PluginHost host;
    private int generation;
    private boolean selected;
    private boolean skinHeard;
    private boolean appearanceHeard;
    /** Whether the turn's events are followed, which they are only while the skin is picked. */
    private boolean following;
    /** This Kiosk Satellite sends voice.state, so the end of speech is known rather than guessed. */
    private boolean stateFollowed;
    /** A voice.state or voice.overlay event arrived since following began, which is newer than a read. */
    private boolean stateHeard;
    private boolean overlayHeard;
    private String overlay = "hidden";
    private String drawn;
    /** The exit spin is playing and hideOverlay is scheduled. */
    private boolean hidePending;
    /** Counts closes and shows, so a close's delayed steps never act on a later one. */
    private int hideToken;
    private boolean spunThisTurn;

    // Read by the view on the main thread.
    volatile float level;
    volatile String state = "idle";
    volatile String command = "";
    volatile String answer = "";
    volatile String tool = "";
    volatile boolean streaming;
    volatile String theme = "auto";
    /** Null for Skin default. */
    volatile Float background;
    volatile float textScale = 1;
    volatile boolean reactive = true;
    // Settings.
    volatile String label = "ALFRED";
    volatile float traceAmp = 0.26f;
    volatile int spinMode = SPIN_ALL;
    /** The colour settings as opaque ARGB, or null while one is left at its default, which keeps the Bat Cave palette of the theme. */
    volatile Integer batColor, traceColor, textColor;
    // Motion shared across views: a change of overlay mode builds a new view mid-turn.
    final Trace trace = new Trace();
    volatile int spinKind = BatArt.SPIN_NONE;
    volatile long spinStartNs;
    /** The showing view. Touched on the main thread only. */
    private SkinView view;

    synchronized void start(PluginHost host) {
        this.host = host;
        int current = ++generation;
        // Only the skin choice is followed from the start, so the plugin costs nothing while another skin draws the turn.
        try {
            host.subscribe("voice.skin");
        } catch (IllegalArgumentException | UnsupportedOperationException olderHost) {
            try {
                host.status("This Kiosk Satellite version has no voice skins.", true);
            } catch (RuntimeException olderStill) {
                // No status line on this version either.
            }
            return;
        }
        readState(current);
    }

    /** Reads whether the skin is picked and where the turn stands. Events win over the read: they are newer. */
    private void readState(int current) {
        host.executeCommand("getVoiceState", Collections.<String, Object>emptyMap(), (ok, data, error) -> {
            synchronized (SkinController.this) {
                if (current != generation || !ok || !(data instanceof Map)) return;
                Map<?, ?> voice = (Map<?, ?>) data;
                if (!skinHeard) selected = Boolean.TRUE.equals(voice.get("skinSelected"));
                follow(false);
                if (following) {
                    if (!overlayHeard && voice.get("overlay") instanceof String) overlay = (String) voice.get("overlay");
                    if (!stateHeard && voice.get("state") instanceof String) state = (String) voice.get("state");
                }
                update();
            }
        });
    }

    /**
     * Follows the turn while the skin is picked and lets it go when another skin is.
     *
     * @param read whether to read the turn's state too: yes unless the caller holds a fresh read
     */
    private void follow(boolean read) {
        if (host == null || selected == following) return;
        following = selected;
        stateHeard = false;
        overlayHeard = false;
        for (String event : EVENTS) {
            boolean isState = "voice.state".equals(event);
            try {
                if (following) host.subscribe(event);
                else host.unsubscribe(event);
                if (isState) stateFollowed = following;
            } catch (IllegalArgumentException | UnsupportedOperationException olderHost) {
                // An event this Kiosk Satellite does not have. Without voice.state the end of speech is guessed from the words.
                if (isState) stateFollowed = false;
            }
        }
        if (!following) {
            // Nothing more is heard about the turn, so nothing of it may be left to show later.
            overlay = "hidden";
            state = "idle";
            level = 0;
            command = answer = tool = "";
            streaming = false;
            return;
        }
        int current = generation;
        if (read) readState(current);
        host.executeCommand("getVoiceAppearance", Collections.<String, Object>emptyMap(), (ok, data, error) -> {
            synchronized (SkinController.this) {
                if (current == generation && ok && data instanceof Map && !appearanceHeard) appearance((Map<?, ?>) data);
            }
        });
        host.executeCommand("getVoiceText", Collections.<String, Object>emptyMap(), (ok, data, error) -> {
            synchronized (SkinController.this) {
                if (current == generation && ok && data instanceof Map && command.isEmpty() && answer.isEmpty()) words((Map<?, ?>) data);
            }
        });
    }

    synchronized void configure(Map<String, Object> settings) {
        Object text = settings.get("label");
        label = text instanceof String ? ((String) text).trim() : "ALFRED";
        Object height = settings.get("traceHeight");
        traceAmp = "Low".equals(height) ? 0.16f : "Tall".equals(height) ? 0.36f : 0.26f;
        Object spin = settings.get("spin");
        spinMode = "Off".equals(spin) ? SPIN_OFF : "Close only".equals(spin) ? SPIN_CLOSE : SPIN_ALL;
        batColor = color(settings.get("batColor"), DEFAULT_BAT);
        traceColor = color(settings.get("traceColor"), DEFAULT_TRACE);
        textColor = color(settings.get("textColor"), DEFAULT_TEXT);
        restyleLater();
    }

    /** A colour setting as opaque ARGB: null for the default, a missing value or anything that is not #RRGGBB. */
    static Integer color(Object value, String fallback) {
        if (!(value instanceof String)) return null;
        String text = ((String) value).trim();
        if (text.equalsIgnoreCase(fallback) || !text.matches("#[a-fA-F0-9]{6}")) return null;
        return 0xFF000000 | Integer.parseInt(text.substring(1), 16);
    }

    /** The bat's colour: the one picked, or the Bat Cave accent of the theme. */
    int bat(boolean dark) {
        Integer picked = batColor;
        return picked != null ? picked : dark ? BatArt.DARK_ACCENT : BatArt.LIGHT_ACCENT;
    }

    /** The traces' colour: the one picked, or the Bat Cave accent of the theme. */
    int traces(boolean dark) {
        Integer picked = traceColor;
        return picked != null ? picked : dark ? BatArt.DARK_ACCENT : BatArt.LIGHT_ACCENT;
    }

    synchronized void onEvent(String event, Map<String, Object> payload) {
        if (host == null) return;
        if ("ks.voice.skin".equals(event)) {
            selected = Boolean.TRUE.equals(payload.get("selected"));
            skinHeard = true;
            follow(true);
        } else if ("ks.voice.overlay".equals(event)) {
            overlay = String.valueOf(payload.get("overlay"));
            overlayHeard = true;
        } else if ("ks.voice.level".equals(event)) {
            Object value = payload.get("level");
            level = value instanceof Number ? ((Number) value).floatValue() : 0;
            return;
        } else if ("ks.voice.state".equals(event)) {
            Object value = payload.get("state");
            String next = value instanceof String ? (String) value : "idle";
            stateHeard = true;
            if ("responding".equals(state) && !"responding".equals(next)) completed();
            state = next;
            return;
        } else if ("ks.voice.text".equals(event)) {
            words(payload);
            if (command.isEmpty() && answer.isEmpty()) spunThisTurn = false;
            // No voice.state on this KS: the spin goes on the end of the text instead of the end of speech.
            else if (!stateFollowed && !answer.isEmpty() && !streaming) completed();
            return;
        } else if ("ks.voice.appearance".equals(event)) {
            appearanceHeard = true;
            appearance(payload);
            return;
        } else {
            return;
        }
        update();
    }

    /** The assistant has finished: the command-complete spin, once a turn, while the overlay shows. */
    private void completed() {
        if (spinMode != SPIN_ALL || drawn == null || spunThisTurn) return;
        spunThisTurn = true;
        spin(BatArt.SPIN_DONE);
    }

    private void spin(int kind) {
        spinStartNs = System.nanoTime();
        spinKind = kind;
    }

    /** Which part of the turn the overlay is in. */
    String phase() {
        String now = state;
        if ("listening".equals(now)) return Trace.LISTENING;
        if ("processing".equals(now)) return Trace.THINKING;
        if ("responding".equals(now)) return Trace.ANSWERING;
        // No voice.state, or the answer stays up after it is spoken: guess from the words.
        if (!command.isEmpty() && answer.isEmpty() && tool.isEmpty()) return Trace.THINKING;
        return Trace.RESTING;
    }

    /** What the skin draws now: null, "full" or "docked". */
    synchronized String drawn() { return drawn; }

    private void appearance(Map<?, ?> settings) {
        theme = settings.get("theme") instanceof String ? (String) settings.get("theme") : "auto";
        background = settings.get("background") instanceof Number ? ((Number) settings.get("background")).floatValue() : null;
        textScale = settings.get("textScale") instanceof Number ? ((Number) settings.get("textScale")).floatValue() : 1;
        reactive = !Boolean.FALSE.equals(settings.get("reactive"));
        restyleLater();
    }

    private void words(Map<?, ?> text) {
        command = text.get("command") instanceof String ? (String) text.get("command") : "";
        answer = text.get("answer") instanceof String ? (String) text.get("answer") : "";
        streaming = Boolean.TRUE.equals(text.get("streaming"));
        Object tools = text.get("tools");
        tool = tools instanceof List && !((List<?>) tools).isEmpty() ? String.valueOf(((List<?>) tools).get(((List<?>) tools).size() - 1)) : "";
        SkinView current = view;
        if (current != null) current.post(current::refresh);
    }

    private void restyleLater() {
        SkinView current = view;
        if (current != null) current.post(current::restyle);
    }

    /** Shows the skin while it is picked and the overlay is up, in the look that matches it. */
    private void update() {
        String want = selected && ("full".equals(overlay) || "docked".equals(overlay)) ? overlay : null;
        if (want == null ? drawn == null : want.equals(drawn)) return;
        drawn = want;
        if (want == null) {
            level = 0;
            spunThisTurn = false;
            SkinView current = view;
            if (spinMode != SPIN_OFF && current != null) {
                // A command-complete spin that is still playing finishes first. Then the bat spins away on the
                // live view and the overlay comes down.
                hidePending = true;
                final int token = ++hideToken;
                final int startedIn = generation;
                final long wait = spinKind == BatArt.SPIN_DONE ? Math.max(0, DONE_MS - (System.nanoTime() - spinStartNs) / 1_000_000) : 0;
                current.postDelayed(() -> {
                    synchronized (SkinController.this) {
                        if (startedIn != generation || token != hideToken || !hidePending) return;
                        SkinView live = view;
                        if (live == null) {
                            hidePending = false;
                            host.hideOverlay(KEY);
                            return;
                        }
                        spin(BatArt.SPIN_EXIT);
                        live.postDelayed(() -> {
                            synchronized (SkinController.this) {
                                if (startedIn != generation || token != hideToken || !hidePending) return;
                                hidePending = false;
                                host.hideOverlay(KEY);
                            }
                        }, EXIT_MS);
                    }
                }, wait);
            } else {
                host.hideOverlay(KEY);
            }
            return;
        }
        hidePending = false;
        hideToken++;
        spinKind = BatArt.SPIN_NONE;
        if ("full".equals(want)) {
            host.showOverlay(KEY, animated(OverlaySpec.fullScreen().onTop(true).touchable(false).closeOnBack(false), OverlaySpec.FADE, 250), new Skin(false));
        } else {
            // A bubble at the bottom. Touches reach the dashboard around and under it.
            host.showOverlay(KEY, animated(OverlaySpec.at(OverlaySpec.BOTTOM).inset(DOCK_INSET).onTop(true).touchable(false).closeOnBack(false), OverlaySpec.SLIDE, 250), new Skin(true));
        }
    }

    /** Transitions, or none on Kiosk Satellite versions without them. */
    private static OverlaySpec animated(OverlaySpec spec, String transition, int ms) {
        try {
            return spec.enter(transition).exit(transition).transitionDuration(ms);
        } catch (LinkageError olderHost) {
            return spec;
        }
    }

    synchronized void stop() {
        host = null;
        generation++;
        selected = false;
        skinHeard = false;
        appearanceHeard = false;
        following = false;
        stateFollowed = false;
        stateHeard = false;
        overlayHeard = false;
        overlay = "hidden";
        drawn = null;
        hidePending = false;
        spunThisTurn = false;
        level = 0;
        state = "idle";
        command = answer = tool = "";
        streaming = false;
        theme = "auto";
        background = null;
        textScale = 1;
        reactive = true;
        spinKind = BatArt.SPIN_NONE;
    }

    private final class Skin implements OverlayFactory {
        private final boolean docked;

        Skin(boolean docked) { this.docked = docked; }

        @Override public View create(Context context, KsTheme theme) {
            SkinView skin = new SkinView(context, theme, docked);
            view = skin;
            return skin;
        }

        @Override public void onThemeChanged(View view, KsTheme theme) {
            ((SkinView) view).restyle(theme);
        }

        @Override public void onDestroy(View destroyed) {
            if (view == destroyed) view = null;
        }
    }

    /** Full screen: the backdrop, the bat with its traces, the label and the conversation. Docked: a card with a small bat beside the words. */
    private final class SkinView extends FrameLayout {
        private final boolean docked;
        private final BatView bat;
        private final TextView who;
        private final TextView said;
        private final TextView answered;
        private final LinearLayout card;
        private KsTheme ksTheme;
        private boolean lit;

        SkinView(Context context, KsTheme theme, boolean docked) {
            super(context);
            this.ksTheme = theme;
            this.docked = docked;
            bat = new BatView(context, docked);
            who = new TextView(context);
            said = new TextView(context);
            answered = new TextView(context);
            setClipChildren(false);
            setClipToPadding(false);
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
            who.setAllCaps(true);
            who.setLetterSpacing(0.25f);
            if (docked) {
                card = new LinearLayout(context);
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setClipChildren(false);
                card.setClipToPadding(false);
                card.setGravity(Gravity.CENTER_VERTICAL);
                int size = theme.px(84);
                card.addView(bat, new LinearLayout.LayoutParams(size, size));
                LinearLayout words = new LinearLayout(context);
                words.setOrientation(LinearLayout.VERTICAL);
                answered.setMaxLines(3);
                answered.setEllipsize(TextUtils.TruncateAt.END);
                words.addView(who);
                words.addView(said);
                words.addView(answered);
                LinearLayout.LayoutParams wordsParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                wordsParams.leftMargin = theme.px(16);
                card.addView(words, wordsParams);
                int width = Math.min(theme.px(560), screenWidth - theme.px(48));
                addView(card, new FrameLayout.LayoutParams(width, LayoutParams.WRAP_CONTENT));
            } else {
                card = null;
                who.setGravity(Gravity.CENTER);
                said.setGravity(Gravity.CENTER);
                answered.setGravity(Gravity.CENTER);
                LinearLayout column = new LinearLayout(context);
                column.setOrientation(LinearLayout.VERTICAL);
                column.setClipChildren(false);
                column.setClipToPadding(false);
                column.setGravity(Gravity.CENTER_HORIZONTAL);
                column.addView(bat, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.round(screenHeight * 0.58f)));
                LinearLayout.LayoutParams line = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                line.topMargin = theme.px(2);
                column.addView(who, line);
                LinearLayout.LayoutParams saidLine = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                saidLine.topMargin = theme.px(12);
                column.addView(said, saidLine);
                LinearLayout.LayoutParams answerLine = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                answerLine.topMargin = theme.px(8);
                column.addView(answered, answerLine);
                int side = theme.px(56);
                column.setPadding(side, 0, side, 0);
                addView(column, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            }
            restyle();
            refresh();
        }

        boolean dark() {
            return "dark".equals(theme) || (!"light".equals(theme) && ksTheme.isDark());
        }

        void restyle(KsTheme next) {
            ksTheme = next;
            restyle();
        }

        void restyle() {
            boolean dark = dark();
            int backdrop = dark ? 0xFF0A0E14 : 0xFFE9EEF3;
            Integer picked = textColor;
            int text = picked != null ? picked : dark ? 0xFFF2F7FB : 0xFF15202B;
            int muted = picked != null ? BatArt.withAlpha(picked, 0.7f) : dark ? 0xB3C7D6E4 : 0xFF5C6B7A;
            float scale = textScale;
            if (docked) {
                GradientDrawable bubble = new GradientDrawable();
                bubble.setColor(dark ? 0xFF10151C : 0xFFFFFFFF);
                bubble.setCornerRadius(ksTheme.px(14));
                bubble.setStroke(Math.max(1, ksTheme.px(1)), dark ? 0xFF26313F : 0xFFCBD5E0);
                card.setBackground(bubble);
                int pad = ksTheme.px(12);
                card.setPadding(pad, pad, ksTheme.px(20), pad);
                ksTheme.styleText(who, 10 * scale, 600, "onSurfaceVariant");
                ksTheme.styleText(said, 14 * scale, 400, "onSurfaceVariant");
                ksTheme.styleText(answered, 17 * scale, 500, "onSurface");
            } else {
                Float chosen = background;
                float opacity = chosen != null ? chosen : DEFAULT_BACKGROUND;
                setBackgroundColor(Color.argb(Math.round(255 * opacity), Color.red(backdrop), Color.green(backdrop), Color.blue(backdrop)));
                ksTheme.styleText(who, 13 * scale, 600, "onSurfaceVariant");
                ksTheme.styleText(said, 20 * scale, 300, "onSurfaceVariant");
                ksTheme.styleText(answered, 28 * scale, 400, "onSurface");
            }
            said.setTextColor(muted);
            answered.setTextColor(text);
            who.setText(label);
            who.setVisibility(label.isEmpty() ? GONE : VISIBLE);
            boolean was = lit;
            lit = !was;
            light(was); // re-apply the label colours for the new palette
            bat.invalidate();
        }

        /** The label glows in the accent while the assistant answers, and sits dim otherwise. */
        void light(boolean on) {
            if (on == lit) return;
            lit = on;
            boolean dark = dark();
            Integer picked = textColor;
            if (on) {
                // Lit, the label takes the text colour when one is picked and glows like the bat otherwise.
                int glow = picked != null ? picked : bat(dark);
                who.setTextColor(glow);
                who.setShadowLayer(dark ? ksTheme.px(14) : 0, 0, 0, BatArt.withAlpha(glow, 0.6f));
            } else {
                who.setTextColor(picked != null ? BatArt.withAlpha(picked, 0.55f) : dark ? 0x8CC7D6E4 : 0xFF5C6B7A);
                who.setShadowLayer(0, 0, 0, 0);
            }
        }

        void refresh() {
            said.setText(command);
            said.setVisibility(command.isEmpty() ? GONE : VISIBLE);
            answered.setText(answer.isEmpty() ? tool : answer);
            answered.setVisibility(answer.isEmpty() && tool.isEmpty() ? GONE : VISIBLE);
        }

        /** The bat and its traces, redrawn every frame. */
        private final class BatView extends View {
            private final BatArt art;

            BatView(Context context, boolean small) {
                super(context);
                art = new BatArt(trace, small, context.getResources().getDisplayMetrics().density);
            }

            @Override protected void onDraw(Canvas canvas) {
                try {
                    String phase = phase();
                    light(Trace.ANSWERING.equals(phase));
                    // With Reactive activity bar off nothing follows the voice. KS sends 0 then too.
                    boolean dark = dark();
                    boolean gone = art.draw(canvas, getWidth(), getHeight(), reactive ? level : 0, phase, bat(dark), traces(dark), traceAmp, spinKind, spinStartNs);
                    if (!gone) postInvalidateOnAnimation();
                } catch (RuntimeException error) {
                    // An exception in onDraw would end the app; a missed frame is the lesser harm.
                }
            }
        }
    }
}
