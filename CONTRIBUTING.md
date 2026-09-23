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

### It needs about 25 GB, and it does not say so politely when it runs out

Measured on 23 September 2026 on the development machine: `TMessagesProj/.cxx` alone reached
**12 GB** and the two `build/` trees another **13 GB**. When the disk filled, the build failed
like this:

```
StarAppsSheet.java:28: error: error while writing <anonymous ...>:
  .../classes/org/telegram/ui/Components/StarAppsSheet$1.class: No space left on device
```

That is a `javac` **error** on a source file, in a file nobody touched. It reads exactly like a
code defect and is not one — check `df -h` before believing it.

**What is safe to delete when it happens**, in the order worth trying:

| Path | Size then | Cost of deleting |
|---|---|---|
| `TMessagesProj/.cxx/*/*/{x86,x86_64,armeabi-v7a}` | 8.5 GB | **it comes straight back** unless you build with `-PquestAbiOnly` — see below. |
| `~/.gradle/caches/build-cache-1` | 1.1 GB | measured: the next build took **35 minutes**. Not worth 1.1 GB. |
| `TMessagesProj*/build/intermediates` | 12.7 GB | a full Java/dex rebuild. Leave `.cxx` alone or the native rebuild is added to it. |

Never delete `.cxx/*/*/arm64-v8a` to save space: that is the one ABI this build actually needs,
and it is the slowest thing in the project to regenerate.

### `-PquestAbiOnly`, and why deleting the other ABIs alone does nothing

`TMessagesProj_AppQuest` sets `abiFilters "arm64-v8a"` (`TMessagesProj_AppQuest/build.gradle:204`), and it is easy to
read that as "this build compiles one ABI". It does not. `abiFilters` on the app decides what is
**packaged**; the library module `TMessagesProj` has no filter of its own, so CMake compiles all
four ABIs on every build and the Quest APK then throws three of them away.

That was measured the hard way on 23 September 2026: the three unused ABI trees were deleted to
free 8.5 GB, and the next `assembleQuestDebug` rebuilt every one of them.

```sh
./gradlew -PquestAbiOnly :TMessagesProj_AppQuest:assembleQuestDebug
```

restricts the library to `arm64-v8a` for that invocation. **Both CI workflows pass it**, because
both build only the Quest APK.

The default is deliberately unchanged: the Huawei and standard flavours are built from the same
module and need all four ABIs, and whether they stay maintained in this fork is an open question
for the operator (finding A-06, `docs/plan.md` Q-03). If the answer is "unmaintained", the flag
becomes the default and this section gets shorter.

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
