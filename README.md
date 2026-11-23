# Alfred (Bat Cave)

A voice skin for [Kiosk Satellite](https://github.com/jxlarrea/kiosk-satellite), built on the Bat Cave bat. While it is picked, the assistant is the bat instead of the usual overlay.

![The Alfred skin answering: the glowing bat with two voice traces spiking out from its wings](assets/voice-skin.png)

- **Listening:** the two traces follow your voice out from the bat's wings, and the bat glows a little with it.
- **Thinking:** a heartbeat pulse travels outward once a second while the bat breathes.
- **Answering:** the bat's glow follows the spoken words and the traces spike with them. The label under the bat lights up.
- **Command complete:** when the assistant finishes speaking, the bat spins like the 1966 scene change, then settles with the answer still on screen.
- **Close:** when the answer's time is up, the bat spins away and the overlay hides.

What you said and the answer show under the bat, as with the built-in skins. Docked overlay mode shows a small bat beside the conversation.

## Requirements

- Kiosk Satellite 2026.10.39 or later.
- The native Voice Satellite runtime turned on. Skins draw only its turns.

## Installation

### From a build

1. Open **Settings > Plugin Manager** in Kiosk Satellite and turn on **Enable Plugins**.
2. Download the ZIP from the latest **Build** run under **Actions**, or from a release.
3. **Plugin Manager > Developer Tools > Install from ZIP**, pick the ZIP, trust it and enable the plugin.

### From this repository (after a release is published)

1. **Settings > Plugin Manager > Add plugin**, paste this repository's URL and choose **Preview**.
2. Review the plugin and choose **Trust and install**, then enable it.

### Pick the skin

Open **Settings > Voice Satellite > Appearance > Assistant 1 skin** (or **Skin** on older versions) and pick **Alfred (Bat Cave)**. **Preview** shows it for five seconds.

## Settings

| Setting | What it does |
| --- | --- |
| Label under the bat | Small tracked capitals under the bat, lit while the assistant answers. Default `ALFRED`; empty hides it. |
| Trace height | How far the two voice traces spike: Low, Normal or Tall. |
| Bat spin | **Command complete and close** (default), **Close only**, or **Off**. |
| Bat color | The bat, its glow and the rings of the spin. Default Bat Cave blue. |
| Trace color | The two voice traces. Default Bat Cave blue. |
| Text color | The answer, what you said and the label. Default near white (near black under the Light theme). |

The skin also follows **Voice Satellite > Appearance**: Theme picks the palette, Background sets the backdrop's opacity (Skin default is near-solid Bat Cave black), Text size scales the conversation, and with Reactive activity bar off nothing moves with the voice.

## Data

The plugin reads only the kiosk's own Voice Satellite state: whether its skin is picked, the voice level, the phase of the turn and the words shown on screen. It sends nothing anywhere and does not talk to Home Assistant.

## Build

GitHub Actions builds a test ZIP on every push (**Actions > Build**). Publishing a release tagged with the version, such as `v0.1.0`, runs **Release**, which attaches the files Kiosk Satellite installs.

To build locally: Python 3, JDK 17 or newer and an Android SDK with `platforms;android-35` and `build-tools;35.0.0`, then `python3 tools/test.py` and `python3 tools/build.py --android-platform 35`.

The SDK under `sdk/` and the build tools under `tools/` come from [kiosk-satellite-plugins](https://github.com/jxlarrea/kiosk-satellite-plugins) (Apache-2.0). The bat is the Bat Cave design kit's `bat.svg`.

## License

[Apache-2.0](LICENSE)
