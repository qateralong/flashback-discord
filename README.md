# Flashback Discord Track

Client-side Fabric addon for Flashback: records Discord audio as a separate replay track, plays it in the editor and mixes it into exports.

## Features

- Records Discord audio as its own track in Flashback replays.
- Plays the track back in the Flashback editor.
- Mixes it into exported videos.
- Capture backends for Windows loopback and PipeWire, with a Java Sound fallback.
- In-game HUD and `/discordtrack` settings (device, gain, quality and more).

## Compatibility

Minecraft 1.21.1, Fabric Loader and Fabric API. Client-side only.

## Requires

- Flashback (client mod).

## Build

```bash
./gradlew build
```

The jar appears in `build/libs/`.

## License

All Rights Reserved.
