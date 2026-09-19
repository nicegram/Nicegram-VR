# Contributing

## Build it first

```sh
git clone --recurse-submodules https://github.com/nicegram/Nicegram-VR.git
cd Nicegram-VR
cp local.properties.example local.properties   # then fill in the two values
./gradlew :TMessagesProj_AppQuest:assembleQuestDebug
adb install -r TMessagesProj_AppQuest/build/outputs/apk/quest/debug/nicegram-vr.apk
```

The submodules are not optional and the clone is large: thirteen of them, pinned to the exact
revisions upstream pinned, including FFmpeg and BoringSSL. A first native build takes a while;
it is compiled for `arm64-v8a` only, because that is what Quest 3 and 3S are.

## One trap, before you commit anything

`git add -A` in a clone whose submodules are not initialised **stages their deletion**: git sees
thirteen missing directories and records thirteen removed pins, and the repository stops building
for everyone. Clone with `--recurse-submodules`, or stage your files by name. Check before you
commit:

```sh
git diff --cached --name-status | grep '^D' || echo "submodule pins intact"
```

## What this fork is for

A Telegram client that is usable in a headset. That is a narrow goal, and it is worth saying
what follows from it:

- **Quiet by default.** Nothing is shown until the user names who may interrupt. A change that
  makes the client notify by default will be declined however well it is written.
- **The display policy stays on the device.** Never call `account.updateNotifySettings` from
  this build. It is account-wide and would silence the user's phone.
- **Nothing is reachable only by a long press.** Holding a ray steady for a second is a gesture
  the hand loses. Every action needs a visible target of at least 64 dp.
- **Recognised speech is never sent on its own.** Dictation fills the input field; the user
  presses send.
- **60 fps is a store requirement, not a preference.** Animation that cannot hold it does not
  ship. Animate transform and opacity; leave layout properties alone.

## Staying close to upstream

Changes to shared Telegram code are kept small, marked with a `Nicegram VR:` comment saying
why, and preferred as a hook over an edit. There is currently exactly one such hook, in
`NotificationsController.processNewMessages`, and it is inert on every other flavour. Everything
else lives in `TMessagesProj_AppQuest`.

To take a newer upstream:

```sh
git remote add upstream https://github.com/DrKLO/Telegram.git
git fetch upstream
git merge upstream/master --allow-unrelated-histories   # once; ordinary merges after that
```

## Before a pull request

- `./gradlew :TMessagesProj_AppQuest:assembleQuestDebug` passes.
- The change runs on a physical Quest 3 or 3S, and the pull request says which.
- If it touches notifications, it also says that notification settings on a second device with
  the same account were checked and did not change.
