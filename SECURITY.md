# Security

## Reporting

Report a suspected vulnerability privately through this repository's GitHub Security Advisories,
or to the address in the organisation profile. Please do not open a public issue for something
that is exploitable.

## What this project does and does not hold

**No credential belongs in this repository.** The two values the build needs — `TELEGRAM_APP_ID`
and `TELEGRAM_APP_HASH` — are read from `local.properties` (ignored by git) or from the
environment. The build fails by name when they are missing rather than substituting a default,
because a client that builds with an empty `api_id` fails later, in the hands of a user, and far
more quietly.

The upstream SafetyNet key and Google OAuth client id were removed rather than inherited. They
belong to the official Telegram application, they are useless on a device with no Google Play
services, and a key committed to an open repository is a published key.

## Speech recognition

Dictation sends audio off the device to whichever recognition service the user configures. That
is a disclosure, not an implementation detail: the client names the recipient on screen before
the first recording, and the microphone indicator is visible for the whole of every recording.
No recognition endpoint or token is compiled into the application; both are entered by the user
and stored on the device.

## Notifications

This build keeps its display policy **local to the device**. It does not call
`account.updateNotifySettings`, because that setting is account-wide: writing it here would
silence the user's phone as well. Anything that changes notification behaviour must preserve
that boundary, and the test for it is performed on a second device.
