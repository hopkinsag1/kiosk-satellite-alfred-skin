# Changelog

## 0.3.1

- Rewrite the README for people new to the plugin, with an install button, the steps to turn the skin on and a troubleshooting section. No change to the plugin itself.

## 0.3.0

- Add **Bat color**, **Trace color** and **Text color** settings. Left at their defaults, the skin looks as before in both themes.

## 0.2.0

- The turn's events (overlay, level, text, appearance, state) are followed only while the skin is picked, so the plugin does no work while another skin draws the turn, such as the Assistant 2 skin.
- Fix an overlay that could come back with no turn showing after the skin was unpicked mid-turn and picked again.
- An older state read no longer overrides a newer overlay or state event.
- Draw the four voice traces from one line a frame instead of building four.
- Update the vendored SDK to the Kiosk Satellite 2026.11.28 interfaces.
- Declare `minAppVersion` 2026.10.39.

## 0.1.0

- First version: the glowing bat, the two voice traces, the thinking heartbeat, the command-complete and close spins, docked look, label and settings.
