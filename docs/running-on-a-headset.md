# Running it on a headset

Sideloading, which is how development happens until the store question is settled.

## Once, on the headset

Developer mode has to be on for the account that owns the device, and the headset has to trust
this computer. Both are done from the headset and the phone app; neither can be done from here.

## Connect

USB is the boring path and the one to use when something is wrong:

```sh
adb devices -l
```

Over Wi-Fi, with the headset and the computer on the same network:

```sh
adb connect <headset-ip>:5555
adb devices -l      # expect: model:Quest_3 ... device
```

**`device offline`, or a device that is listed but answers nothing, usually means a stale
entry rather than a problem.** An address survives in adb's list after the headset sleeps,
changes address or drops off the network, and every command against it then fails in a way that
looks like a broken build. Clear it and reconnect:

```sh
adb disconnect <headset-ip>:5555
adb connect <headset-ip>:5555
```

If the reconnect times out, the headset is asleep or on another network. Put it on, check its
address in the headset's Wi-Fi settings, and try again. Nothing on the computer will fix it.

## Build and install

```sh
./gradlew :TMessagesProj_AppQuest:assembleQuestDebug
adb install -r TMessagesProj_AppQuest/build/outputs/apk/quest/debug/nicegram-vr.apk
```

The first build compiles FFmpeg, BoringSSL, libvpx, dav1d, openh264 and TDLib for `arm64-v8a`
and takes a long time. Later builds reuse it. Installing over an existing copy keeps its data;
`adb uninstall app.nicegram.vr` when you want a genuinely first run, which is the only way to
see the first-launch screens again.

## Watch it

```sh
adb logcat -c                                   # clear, so what follows is yours
adb logcat | grep -iE "nicegram|tmessages|AndroidRuntime"
```

The two lines worth looking for on a first run:

- `__NO_GOOGLE_PLAY_SERVICES__` — the push provider took the intended path. Its absence means
  something else supplied a provider and the build is not what this repository describes.
- Any `AndroidRuntime` stack trace — on a headset a crash can look like the app simply never
  appearing, because there is no window to show the dialog in.


## Measured on a Quest 3 — 19 September 2026

The first run on real hardware. Everything below is a reading, not an expectation; where a
number is a baseline rather than a verdict, it says so.

**The device.** Quest 3, Horizon OS on Android 14 (SDK 34), build `UP1A.231005.007.A1`,
`arm64-v8a`. Whole display 4128×2208. System locale `ru_RU`.

**Install and start.** `adb connect 192.168.0.253:5555` then
`adb install -r -t nicegram-vr.apk` — 134 MB over Wi-Fi, **12 s**, `Success`. Launched with
`monkey -p app.nicegram.vr -c android.intent.category.LAUNCHER 1`. No `FATAL`, no
`AndroidRuntime` stack. `tgnet` wrote its per-account config files, so the network stack came
up. Horizon OS moved the app `HIGH_PERCEPTION -> CRITICAL_PERCEPTION`, which is its way of
saying the panel is the thing the wearer is looking at.

**The panel, from `dumpsys activity activities`.** Note the date on these: they are the
*landscape* panel this fork asked for until the evening of the same day, when the manifest was
changed to `portrait` and Horizon OS reshaped it to **500×800 px — `sw400dp w400dp h640dp nrml
port`** (finding A-21). The readings below are kept because the density proof was taken against
them; everything about the shape is superseded.

**The landscape panel, as measured:**

```
sw640dp w1024dp h640dp 200dpi lrg land night -touch -keyb -nav
mBounds=Rect(0, 0 - 1280, 800)   mWindowingMode=multi-window   [ru_RU]
```

1280×800 px at 200 dpi, so the system density is **1.25**. Landscape, dark mode on, no
touchscreen — all as designed for.

**A-01 is proven here, and this is how.** `uiautomator dump` reports the login screen's floating
button at `[974,214][1082,322]` — **108×108 px**. Upstream declares that button as **56×56 dp**
(`FragmentFloatingButton.createDefaultLayoutParamsBig`, `:189`). 108 / 56 = **1.929 px/dp**,
against the 1.25 × 1.54 = **1.925** this build intends. The headset scale reaches the running
interface; without the fix it would be 1.25 and the button would measure 70 px. A measurement
this direct exists because the fix was in the one place density is assigned, and it is the
acceptance A-01 was owed.

**The tablet threshold — A-08, answered.** The panel reports `sw640dp`, and the package defines
`bool/isTablet` as `true` for `sw600dp` (`aapt2 dump resources`: `() false`, `(sw600dp) true`).
**The app therefore runs in tablet mode on a Quest 3.** Whether that splits the screen depends
on a second condition: `LaunchActivity` takes the two-column branch only when
`!AndroidUtilities.isInMultiwindow`, and the panel's windowing mode **is** `multi-window`, so
the split is most likely collapsed to one full-width column. *Most likely, not measured* — the
sign-in screen is not the split layout, and the split can only be seen once an account is
signed in. Upstream already carries the switch to settle it either way:
`SharedConfig.forceDisableTabletMode` (`SharedConfig.java:627`, default `false`).

**Frames, on an almost static screen.** `dumpsys gfxinfo` after start-up and the login form:

| | |
|---|---|
| Frames rendered | 4073 |
| Janky | 243 (**5.97%**) |
| 50th percentile | **11 ms** |
| 90th percentile | **22 ms** |
| 95th / 99th | 27 ms / 48 ms |
| Missed Vsync | 42 |

The budget for 60 fps is 16.67 ms. The median frame fits; **one frame in ten does not**, before
any list has been scrolled. Read it as a baseline and not a verdict: this is a debug build with
no minification, and the counters include process start-up. The VRC gate needs the same reading
taken on a release build while scrolling a real chat list, which is the next device task.

**Memory.** TOTAL PSS **210 MB**, RSS 311 MB, native heap 45 MB, Dalvik heap 15 MB.

**The scale that the panel then disproved.** Signed in, the chat list showed **four chats** on a
1280 px-wide panel — reported from inside the headset, and reproduced exactly by arithmetic:
800 px / (1.25 × 1.54) = 415.6 dp of height, minus 48 dp of action bar and 44 dp of folder
strip, over a 70 dp `DialogCell`, is 4.6 rows. The 1.54 came from an assumed panel of
"1440×900 dp at 1.3 m"; this one is 1024×640 dp. Re-based the same evening — steps are now
absolute multipliers on the system density (0.85 / 1.0 / 1.25 / 1.54) with the default at 1.0,
which is 7 rows, and the setting names the count rather than a size. Finding A-20.

**A screenshot of the panel cannot be taken with `adb`.** `adb exec-out screencap` returns the
4128×2208 compositor frame — passthrough and immersive layers only; the 2D panel is composited
by the spatial shell and is not in it. Two captures confirmed it, one while an immersive app was
in front and one while our panel was the active window. **The evidence channel for layout is
therefore `uiautomator dump`**, which works, names the package, and gives exact pixel bounds for
every node — every number in this section came from it. Anyone writing a device check should
plan for that and not for pictures.

**One thing seen in passing.** The hierarchy shows a *«Тестировать backend»* checkbox on the
sign-in screen — upstream's test-server toggle, which appears because this is a debug build. It
must not be reachable in anything published.

**And a sharper reason for A-19 than the one written at the time.** The sign-in screen came up
in Russian — *«Номер телефона»*, *«Проверьте код страны и введите свой номер телефона»* — because
upstream's strings arrive from the language pack and the system is `ru_RU`. Had the headset
module's own strings stayed in Android resources, they would have been the only English text on
an otherwise Russian screen.

## What to check on a first run, in this order

1. It starts, and stays up for half an hour without dying.
2. Sign-in completes. Until the sign-in-by-code screen exists, this is the phone-number path,
   typed with a ray, and it is as unpleasant as that sounds — that is the reason the screen is
   next on the roadmap.
3. The chat list holds its frame rate while scrolling.
4. **Nothing notifies.** Then add one exception, send yourself a message from that person, and
   check it appears — and that a message from anyone else does not.
5. On a second device signed into the same account, open the notification settings for those
   chats and confirm **nothing changed there**. This is the check the whole silence design
   exists to pass, and it is the one that cannot be done from the headset alone.
