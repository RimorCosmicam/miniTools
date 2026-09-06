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

### Density: the first sweep was wrong

An earlier version of this page said density changed nothing. That was measured
badly and the conclusion was false.

**Samsung's launcher survives a density change on purpose.** Its manifest carries

```
com.samsung.android.keepalive.density=true
```

so `wm density <n> -d 1` does not restart it, and an already-running
`RecentsActivity` that is merely resumed keeps the layout it built at the old
density. The first sweep changed the density and then brought the existing
switcher to the front, so it re-measured the same stale layout three times and
reported that nothing moved. It also left the launcher running stale for the rest
of that session, which produced portrait cards and a "Close all" under the camera
island — a fault that looked like a bug in the phone and was an artefact of the
measurement.

Force-stop the launcher, or start the switcher with `FLAG_ACTIVITY_CLEAR_TASK`,
and the density lands:

| Density | Cover screen in dp | Verdict |
|---|---|---|
| 420 (native) | 361 x 399 | correct, but larger than wanted |
| 400 | 379 x 419 | closer |
| **370** | **410 x 453** | **chosen** |
| 340 | 446 x 493 | too small |
| 460 / 480 / 500 / 525 | 330 / 316 / 303 / 289 wide | progressively too big |

370 is the shipped figure, arrived at by looking at it on the panel.

### Setting it from an app

`wm density <n> -d 1` affects only the cover display and leaves the main screen
alone, so a per-display override is exactly the right shape. Doing it from an
unprivileged app is not possible; doing it from a *permitted* one is:

| Step | Result |
|---|---|
| `pm grant … WRITE_SECURE_SETTINGS` | one-time, survives reboots and updates |
| plain reflection on `IWindowManager` | 20 of ~231 methods visible; no setter |
| hand-rolled `VMRuntime.setHiddenApiExemptions` | dead on Android 16 |
| `HiddenApiBypass.addHiddenApiExemptions("")` | works — 231 methods visible |
| `setForcedDisplayDensityForUser(1, 370, 0)` | OK |
| `clearForcedDisplayDensityForUser(1, 0)` | OK, restores |

The permission is never the obstacle; the hidden-API blocklist is. And because
`CLEAR_TASK` builds a fresh switcher anyway, the new density is picked up without
force-stopping the launcher at all.

**The override is global and sticky.** It is a property of the display, not of
the switcher, so anything that sets it without restoring leaves the whole cover
screen at that density — including after a crash. Any shipped version of this
needs a restore that runs on service connect and on boot, not only on the way out
of the switcher.

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

## Refreshing the switcher

Started without `FLAG_ACTIVITY_CLEAR_TASK`, an existing `RecentsActivity` is only
resumed. The system's recent-task list has already moved on — an app used a
moment ago genuinely is at the front of `dumpsys activity recents` — but the
switcher redraws the model it built when it was last created, so that app is
missing from what is on screen. Samsung's own gesture never hits this because it
enters through quickstep, which reloads the model on the way in.

`NEW_TASK | CLEAR_TASK | TASK_ON_HOME` (`0x1000C000`) forces a fresh instance,
and a fresh instance reads the list again. Back still returns to the cover home.

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
