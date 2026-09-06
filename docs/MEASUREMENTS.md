# Measurements

Everything here was taken off a Galaxy Z Flip 7 (SM-F766B, One UI 8, Android 16,
build `BP4A.251205.006.F766BXXSCBZH3`) over ADB. Nothing on this page is a guess;
where something is still unknown it says so.

## The panel

| | Display 0 | Display 1 |
|---|---|---|
| Role | main, inner | **cover / FlexWindow** |
| Size | 1080 x 2520 | **948 x 1048** |
| Density | 480 base, 420 forced | **420** |
| In dp at that density | 360 x 840 | **361 x 399** (`sw361dp`) |
| `canHostTasks` | true | **true** |
| Flags | | `PRESENTATION`, `SHOULD_SHOW_SYSTEM_DECORATIONS`, `TRUSTED`, `EXTRA_BUILT_IN_DISPLAY` |

The cover display is id 1, and has been on every unit looked at. miniTools still
finds it at runtime rather than hard-coding it — see `CoverDisplay.kt`.

`screencap` defaults to the cover panel on this device, which is convenient and
also not guaranteed; pass `-d 4633128672291735938` to be sure.

## The cutout

```
DisplayCutout{insets=Rect(0, 0 - 0, 220) boundingRect=Rect(428, 828 - 948, 1048)}
```

The camera island occupies the bottom-right 520 x 220 pixels. It is a cutout in
the display, so nothing renders there — the empty band under the task cards in a
cover-screen screenshot is this, not a layout fault.

**The digitiser does not have the same hole.** Touches under the island are still
reported. This is what the flash gesture is built on.

## Recents

The activity is:

```
com.sec.android.app.launcher/com.android.quickstep.RecentsActivity
```

It is `mRecentsComponent`, it is reachable from an ordinary app uid, and it
starts on the cover display with nothing more than a launch display id. It comes
up as `mActivityType=recents`, `sw361dp w361dp h399dp 420dpi`, bounds
`(0, 0 - 948, 1048)`.

### Density is not needed, and was measured to be sure

| Density | Cover screen in dp | What changed |
|---|---|---|
| **420** (native) | 361 x 399 | Correct. Carousel, neighbours peeking, cutout respected. |
| 340 | 446 x 493 | Chrome smaller. Cards identical. |
| 280 | 541 x 599 | Chrome smaller again. Cards identical. |
| 240 | 632 x 699 | Chrome noticeably small. Cards identical. |

The task cards are thumbnails scaled to the display, not views laid out in dp, so
they do not respond to density at all. Only the chip, the "Close all" button and
the navigation bar do.

An earlier screenshot at 420 appeared to show one oversized card with dead space
below it, which looked like a density fault and was not: it was the carousel's
scroll position, plus the cutout band. Re-running at 420 after the sweep produced
the same correct carousel as 340 and 240.

`wm density <n> -d 1` affects **only** the cover display and leaves the main
screen alone, so a per-display override was available and was simply not worth
its cost. `wm density reset -d 1` puts it back.

## The gesture zones

Captured with `getevent -lt` on `/dev/input/event5` (`sec_touchscreen2`, the
cover digitiser) while the phone was tapped deliberately. The device reports a
normalised 0..4095 on both axes; pixels below are `raw / 4096 * panel`.

### The flash — 27 taps

```
x  444 .. 501   (mean 475, spread 57)
y  916 .. 980   (mean 955, spread 64)
```

Every one of them is inside the cutout rectangle, which is the point. The flash
sits just inside the island's left edge — the lenses are to its right and report
nothing.

Zone shipped: **(416, 868) – (560, 1048)**. That is the cluster with roughly a
finger of margin on every side. Margin is free here: no other window on the phone
can be given this rectangle.

### The bottom-left corner — 2 taps

```
(100, 997) and (95, 966)
```

Zone shipped: **(0, 900) – (88, 1048)**. A swipe must travel 48px upward within
600ms to count.

The first version of this reached to x 280 and swallowed the back button whole,
which is what forced the navigation bar to be measured rather than assumed:

```
NavigationBar1  frame=Rect(0, 938 - 948, 1048)   height 110
```

Its buttons are **not** centred on the panel. They are centred on the strip left
of the camera island — back at x 163, home at x 310, 147 apart, symmetric about
x 236, which is the middle of 0..473. Back's slot therefore begins at **x 90**.

So the free corner is x 0..90, with or without the bar showing, and the zone
stops at 88. It runs up to y 900 — taller than the bar — only so the swipe has
somewhere to travel.

## Other apps already in these places

Both zones are already claimed by third-party cover-screen apps on this phone,
which is a good sign about the design and a real source of conflict:

```
apps.ijp.coverrecents          frame=Rect(0, 968 - 300, 1048)     the same corner
apps.ijp.coverscreen.launcher  frame=Rect(419, 861 - 559, 981)    the same flash
```

Whichever window is higher in the z-order takes the touch. miniTools currently
sits above both, so it wins, but nothing guarantees that ordering and two apps
racing for one rectangle is not a state either of them can resolve. If the
gestures ever stop responding, this is the first thing to look at.

`ZonesTest` asserts that every tap above still lands in the zone it was measured
from, that neither zone runs off the panel, that they do not overlap, and that
the corner never reaches the middle of the edge.

## Still unknown

- Whether the flash cluster sits in the same place on other Flip 7 units, or
  whether panel-to-panel variation matters. The shipped margin is sized for a
  finger, not for a different phone.
- Whether the corner strip ever loses a race with One UI's own edge gestures on
  screens other than the cover home. It has not so far.
- Whether background-activity-launch rules change for a service holding a
  visible accessibility overlay. `Recents.open` falls back to
  `GLOBAL_ACTION_RECENTS` if the explicit start is ever refused.
