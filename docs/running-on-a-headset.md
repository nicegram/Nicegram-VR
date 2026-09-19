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
