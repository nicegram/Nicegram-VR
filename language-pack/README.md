# The language pack — why the Russian is not a resource folder

This directory holds the **upload source** for the Nicegram cloud language pack. Nothing in it
is compiled into the APK, and that is the entire point.

## The mechanism, measured rather than assumed

`TMessagesProj_AppQuest/build.gradle:124` carries

```gradle
localeFilters += ["zz"]
```

so **every Android locale is stripped from the package**. Telegram serves its own language packs
at runtime, and this build follows it. A `res/values-ru/` folder in the headset module is
compiled, merged into the APK, and then dropped: measured on the 19 September debug build, where
`aapt2 dump configurations` reported no locale config at all. It was tried, it shipped nothing,
and it was deleted at `fc887365` — the commit named *"the localisation that never shipped"*.

The channel that does reach a user is the pack. `LocaleController` asks it by resource **entry
name** before falling back to the compiled resource, so a string becomes Russian when
`vr_silence_title` exists in the pack — not when a `values-ru/` file exists in this repository.

## What to upload

`strings_vr.ru.xml` — every key of `TMessagesProj_AppQuest/src/main/res/values/strings_vr.xml`
under exactly the same entry name. The English stays where it is and remains the fallback for
every language the pack does not answer for.

## What is checked, and by what

`TMessagesProj_AppQuest/src/test/java/org/telegram/vr/quest/LanguagePackParityTest.java` runs on
every push and fails when:

| | Why it matters |
|---|---|
| a key exists on one side only | it reaches a Russian user as an English string |
| the two disagree about `%1$s` / `%2$d` | `String.format` throws as the screen draws, on a device, in one language |
| `res/values-ru/` comes back | a resource that looks like it works, and ships nothing |

`LanguagePackReachabilityTest` covers the other half: no string in the headset module may be read
through a `Context`, because a `Context` read never asks the pack. Five of them were, including
the first-run screen — finding A-31.

## Voice

From the brand pack at
`nicegram-product-workspace/public/projects/nicegram-vr/design/docs/brand/strings.md`:
**шлем** not «хедсет», **панель** not «окно», **тишина** not «режим не беспокоить», **код входа**
not «QR-код». An error names its cause and the next step — there is no «что-то пошло не так» in
the register. A button names the action, not the object.

The 47 keys that existed before the dictation, layout, about and intro work were written for
these entry names in September and are carried over unchanged from `fc887365^`; the 54 added
since were written against the same pack.
