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
screen at that density — including after a crash. miniTools writes itself a note
when it applies one and clears it on service connect and on boot.

### Why the override is opt-in and defaults to native

Setting it works. Putting it back reliably does not, and the reason is worth
recording because it is not obvious.

The switcher's task is `translucent=true`. The cover home therefore stays
resumed *behind* it rather than being stopped, and it announces itself with a
window-state change while the switcher is still on screen. Logging every event
during one gesture gives exactly two:

```
pkg=com.sec.android.app.launcher  cls=com.android.quickstep.RecentsActivity
pkg=com.android.systemui          cls=...subscreen.SubHomeActivity
```

The second arrives two or three seconds after the switcher opens, with the
switcher still up and in use. "The user left the switcher" and "the home behind
the switcher spoke up" are the *same event*, so a restore keyed on it fires
mid-use: the density is applied, the switcher renders at it, and a moment later
it snaps back to native under the reader's hands.

An accessibility service that does not read window content has no way to tell
those two apart. So the override ships opt-in, off by default, and the panel is
left at its native 420 unless somebody chooses otherwise. A density that flickers
is worse than one that is merely larger than you wanted.

370 remains the right-looking number if that is ever solved.

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

Zone shipped: **(416, 868) – (560, 950)**. It stops short of Samsung's
aspect-ratio button at (426, 952) – (522, 1048), shown for apps its cover launcher
opened, which an accessibility overlay would otherwise cover. That is the cluster with roughly a
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

## Rotation, and what it costs the switcher

Samsung pins the cover panel to portrait and cannot be talked out of it.
`wm user-rotation -d 1 lock 1` is accepted and ignored, and so is
`set-ignore-orientation-request`; the panel reports `mSupportAutoRotation=true`
and stays 948 x 1048 through all of it.

What works is asking rather than telling. A display rotates to satisfy the
topmost window that expresses an orientation, and the cover screen never turns
because the thing on it asks for `SCREEN_ORIENTATION_NOSENSOR`. A window of our
own — zero by zero, invisible, `screenOrientation = SENSOR` — is enough, and it
needs no permission at all as an accessibility overlay.

### The cost, which is Samsung's bug and not ours

Rotating once breaks the switcher's layout until it is repaired. From the
launcher's own log while broken:

```
RecentInsetsManagerImpl: standardInsetsType: 131, isValidWindowInsets: false
RecentInsetsManagerImpl: Use savedInsets: InsetsData(standardInsets=
    Insets{left=0, top=0, right=220, bottom=0}
```

`right=220` is the camera cutout **as it is in landscape**. The launcher keeps one
global saved-insets value and falls back to it whenever it cannot read live ones —
and on this phone it never can, because the cover panel always reports
`isValidWindowInsets: false`. So the saved value is the only value it ever uses,
a rotation writes a sideways one into it, and every switcher afterwards is laid
out against that.

| Last writer of savedInsets | Value | Cover switcher |
|---|---|---|
| boot | `bottom=220` | correct |
| a rotation | `right=220` | cards narrow, "Close all" under the island |
| switcher on display 0 | `top=109, bottom=126` | correct shape, sits ~109px low |

It survives a launcher restart, which is what makes it look unfixable: the value
is not in the launcher's process. It does not survive a reboot.

### What boot does that nothing else does

Catching the launcher starting up shows the whole mechanism:

```
isValidWindowInsets: true
updateInsetsData, rotation: 0, isPort: true, insets: {left=0, top=109, right=0, bottom=126}
isValidWindowInsets: false
updateInsetsData, rotation: 0, isPort: true, insets: {left=0, top=0,   right=0, bottom=220}
```

There are two paths, not one. When insets are readable it uses them; when they
are not — the cover, always — `updateInsetsData` still runs and **computes the
right answer from the cutout**: `bottom=220`, exactly what the panel needs.

So the correct value is derivable at runtime. It is simply only derived during
the launcher's own start-up. A force-stop re-runs it and repairs the switcher
exactly. Nothing an ordinary app can reach does: a density change on the cover
produces only `Use savedInsets`, a virtual display at the same size and density
reports valid insets but its values are never persisted, and restarting the
switcher does not help because the switcher is not what computes them.

Force-stopping another package needs a signature permission, so the exact repair
stays a thing the user performs. miniTools opens the page with the button on it.

**The approximate repair.** Start the switcher once on display 0. That is the only display
where insets are ever valid, so it rewrites the saved value with an upright one.
This phone has no inner panel at all — display 0 is permanently `state OFF` — and
starting an activity there neither wakes it nor disturbs the cover screen, so the
repair is invisible. It writes the inner display's insets rather than the cover's,
which is why the result sits slightly low. Slightly low beats sideways.

None of this is reachable from the cover side: the density lever does nothing,
`am kill` will not touch the launcher because it is the home process, and
force-stop is not an ordinary app's to call.

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

All zones are stored against the stock 948 × 1048 panel and scaled to the
current resolution at runtime; density does not move them.

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
