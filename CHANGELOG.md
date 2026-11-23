# Changelog

## 0.2.0

- The turn's events (overlay, level, text, appearance, state) are followed only while the skin is picked, so the plugin does no work while another skin draws the turn, such as the Assistant 2 skin.
- Fix an overlay that could come back with no turn showing after the skin was unpicked mid-turn and picked again.
- An older state read no longer overrides a newer overlay or state event.
- Draw the four voice traces from one line a frame instead of building four.
- Update the vendored SDK to the Kiosk Satellite 2026.11.28 interfaces.
- Declare `minAppVersion` 2026.10.39.

## 0.1.0

- First version: the glowing bat, the two voice traces, the thinking heartbeat, the command-complete and close spins, docked look, label and settings.
