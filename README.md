<img src="tools.png" width="88" alt="miniTools">

# miniTools

Your folded phone, keeping its own tools.

miniTools puts things the cover display should already have onto the cover
display. The first of them is Recents: One UI's own task switcher, reachable
without unfolding the phone.

Built for the Galaxy Z Flip 7 FlexWindow. It runs there and nowhere else.

## What it does

- **Recents** — Samsung's task switcher, on the cover screen, at the panel's
  native density. Nothing is drawn, imitated or reskinned; the real one is
  simply asked to appear somewhere it never gets asked.
- **Two ways in, your choice of either or both** — swipe up from the bottom-left
  corner, or press and hold the flash.
- **Nothing else running** — no daemon, no pairing, no ADB, no `WRITE_SECURE_SETTINGS`.
  Install it, switch it on, fold the phone.

## The gestures

**Bottom-left corner, swipe up.** The bottom centre is where Samsung Pay lives
and it stays Samsung Pay's. The strip is 280px wide — 30% of the panel — and it
is the only part of the edge miniTools claims.

**The flash, pressed and held.** The camera island is a cutout in the *display*,
not in the digitiser: the panel keeps reporting touches under it even though
nothing can be drawn there. So the flash is a button that costs no screen at
all. The lenses beside it report nothing, and a press that strays onto them is
simply lost rather than delivered somewhere wrong.

Both zones are invisible. They are places on the panel, not controls, and
drawing something in either would be decoration announcing itself.

## About the density

The cover panel runs at 420dpi and Recents renders correctly there, so miniTools
changes nothing.

This was worth measuring rather than assuming. Dropping the display to 340, 280
and 240 only shrinks the chrome — the "3 active apps" chip, the "Close all"
button, the navigation bar. The task cards never change size, because they are
thumbnails scaled to the display rather than laid out in dp. An override would
buy a slightly smaller button in exchange for a privileged permission, a setting
that has to be put back on the way out, and a phone left in a strange state if
the app dies while it holds one. It is not a trade worth making.

The numbers behind that, and behind the two gesture zones, are in
[`docs/MEASUREMENTS.md`](docs/MEASUREMENTS.md).

## Getting it on the cover screen

Install Good Lock, add the MultiStar module, then
**I ♡ Galaxy Foldable → Launcher Widget** and enable miniTools. Fold the phone,
swipe to the widget, launch.

Then switch the accessibility service on. It is what gives miniTools a window
that outlives its own activity and sits over the cover screen — the only way an
ordinary app gets one. It subscribes to no events and reads no window content;
the configuration in
[`accessibility_service_config.xml`](app/src/main/res/xml/accessibility_service_config.xml)
is the narrowest one that still returns a window.

## Building

Everything is built by GitHub Actions. Push to `main` and take the artifact from
the run, or start the workflow by hand.

```
gh run download <run-id> -R RimorCosmicam/miniTools -n minitools-debug-apk
```

The workflow runs the unit tests before it builds, so a green run is one where
the gesture zones still contain every tap they were measured from.

## The family

[MontUI](https://github.com/RimorCosmicam/Mont) is the language it is written in.
[miniMont](https://github.com/RimorCosmicam/miniMont) ·
[MiniDex](https://github.com/RimorCosmicam/miniDex) ·
[miniPape](https://github.com/RimorCosmicam/miniPape) ·
[AirMate](https://github.com/RimorCosmicam/AirMate)

## Open source

MIT. Do what you like with it (but let me know, I love cool stuff).
