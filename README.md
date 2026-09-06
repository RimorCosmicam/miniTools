<img src="tools.png" width="88" alt="miniTools">

# miniTools

Your folded phone, keeping its own tools.

miniTools puts things the cover display should already have onto the cover
display. Two of them so far: One UI's own task switcher, and a launcher for
every app on the phone — both reachable without unfolding it.

Built for the Galaxy Z Flip 7 FlexWindow. It runs there and nowhere else.

## The tools

**Recents** — Samsung's task switcher, on the cover screen, at the panel's native
density. Nothing is drawn, imitated or reskinned; the real one is simply asked to
appear somewhere it never gets asked. Two ways in, your choice of either or both:
swipe up from the bottom-left corner, or press and hold the flash.

**Launcher** — every app on the phone, in a grid on the cover screen. Its Good
Lock widget *is* the grid, not a card that opens one: on a cover screen the
widget carousel is the home screen, and a card there costs a swipe and a tap to
say the tool's name. The word
top left, the order top right — A–Z, Z–A, NEW, USED — and tapping the order
cycles it. Hold an app to favourite or hide it; favourites lead every order and
are still sorted among themselves, so starring something never scrambles the sort
you asked for. Hold the order, or tap the title, for the rest: the card
background and the title can each be turned off, and hidden apps are put back
from a list of their own.

USED is the one order the package manager cannot answer. Usage access is optional
and is offered only when that order is chosen; without it the grid falls back to
the launches miniTools made itself.

**Nothing else running** — no daemon, no pairing, no ADB, no
`WRITE_SECURE_SETTINGS`, no density override. Install it, switch it on, fold the
phone.

## The gestures

**Bottom-left corner, swipe up.** Everything to the left of the back button, and
nothing else. The cover navigation bar's buttons are centred on the strip beside
the camera island rather than on the panel, which puts back's slot at x 90 — so
the zone stops at 88 and the bottom centre stays Samsung Pay's. That is 88px of
a 948px edge.

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
