

![Platforms](https://img.shields.io/badge/platform-Android-green) [![Latest release](https://img.shields.io/github/v/release/jakobkreft/OnTime?color=orange&label=release)](https://github.com/jakobkreft/OnTime/releases)

[<img width="100%" height="auto" src="fastlane/metadata/android/en-US/images/featureGraphic.jpg" alt="OnTime feature graphic" />](https://f-droid.org/packages/si.jakobkreft.ontime/)

Try it out: [Live Demo](https://jakobkreft.github.io/Projects/PresentationTimer/index.html)

<!-- ![](https://api.visitorbadge.io/api/VisitorHit?user=jakobkreft&repo=OnTime&countColor=%237B1E7A)  -->
<div style="white-space: nowrap;">
  <a href="https://play.google.com/store/apps/details?id=si.jakobkreft.ontime&pcampaignid=MKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1" style="display: inline-block;">
    <img alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png" height="80px"/>
  </a>
  <a href="https://f-droid.org/packages/si.jakobkreft.ontime" style="display: inline-block;">
    <img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="80">
  </a>
  <a href="https://github.com/jakobkreft/OnTime/releases/latest" style="display: inline-block;">
    <img height="80" src="https://github.com/machiav3lli/oandbackupx/blob/034b226cea5c1b30eb4f6a6f313e4dadcbb0ece4/badge_github.png"/>
  </a>
</div>

**OnTime** keeps speakers on schedule at live events, presentations and lectures. The whole screen
is the signal: green while there is room, yellow when it is time to wrap up, red when the time is
nearly gone. Past zero the clock keeps counting upwards, so you always know how far over you ran.

## Features

- **Three durations per timer** — total time, plus yellow and red warnings set as the time remaining.
- **Sharp colour changes**, readable from across a room. No fades, no gradients.
- **Overtime clock** that keeps running past zero.
- **Named timers**, as many as you need, in a Timers list you can duplicate and delete from.
- **The screen stays on** while a timer is on screen.
- **Rotation never disturbs a running timer** — colour, countdown and overtime all carry through.
- **Everything fits one screen.** Nothing scrolls, in either orientation.
- **No permissions**, no network access, no tracking.

## Usage

1. Set the total time and the two warning points. Durations can be typed as `25:00`, `90`, `25m` or
   `1h 30m`; the editor shows what it understood before you save.
2. Press play. Glance at the background colour to see where you stand.
3. Tap the timer's name in the top bar to switch to another saved timer, or to add one.

## Building

Requires a JDK 17 or newer and the Android SDK. Everything else is fetched by the Gradle wrapper.

```sh
./gradlew assembleDebug          # debug APK
./gradlew testDebugUnitTest lint # tests and static analysis
./gradlew assembleRelease        # unsigned, minified release APK
```

The release build is deliberately reproducible: no VCS metadata, no dependency-info block, no
signing configuration, and every dependency pinned in `gradle/libs.versions.toml`.

## Architecture

Kotlin and Jetpack Compose, one activity, no fragments.

| | |
|---|---|
| `domain/` | `DurationText` parses and formats durations; `TimerSnapshot` turns a preset plus an elapsed time into the phase, the remaining time and the overtime. Both are pure and unit-tested. |
| `data/` | `Preset` and `RunState`, persisted as one JSON document via `PresetRepository`. |
| `ui/` | `TimerViewModel` holds the state; `RunScreen`, `PresetsScreen` and `AboutScreen` draw it. |

A running timer stores *when it started*, never how far it has got, and every displayed value is
derived from that timestamp and the clock. That is why rotation, backgrounding and process death
cannot disturb a run: there is no elapsed-time state to lose.

## License

This project is licensed under the **GPL-3.0-or-later**. See the [LICENSE](./LICENSE) file for details.

Developed by **Jakob Kreft**.
