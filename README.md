# Phone

A complete phone app for Android — dial, find contacts, **place and receive calls in this
app**, with a Recents list, dual-SIM support and an in-call screen. No third-party dialler
is involved once it is set as the default phone app.

```
Recents        All calls / Missed calls, straight from the system call log
Keypad         T9 smart dial, one call button per SIM
Contacts       Search by name, number or keypad digits
In call        Answer, decline, hang up, mute, speaker, DTMF
```

## Features

**Recents**
- Reads the system call log (`READ_CALL_LOG`), so incoming, outgoing, missed and rejected
  calls are all there — with a SIM badge, call type, country and relative time
- `All` / `Missed Calls` filter
- Tap a row to call back

**Keypad**
- T9 keypad with letter groups; long-press `0` for `+`, long-press ⌫ to clear
- Smart dial: matches contacts by number *or* by name converted to keypad digits
  (`6279` → Mary)
- **Dual SIM**: one green call button per SIM, and the call is routed to that SIM
- Optional WhatsApp shortcut next to the call button

**Contacts**
- `ContactsContract`, grouped one entry per person, multi-number picker
- Search by name, number or T9 digits

**In call** (`InCallActivity`, drawn by `PhoneCallService`)
- Incoming call screen with Answer / Decline, shown over the lock screen
- Active call: hang up, mute, speaker, DTMF keypad, live duration
- Calls placed from the keypad or a contact open the same screen

**Settings**
- Default phone app status and a one-tap request for the role
- Blocked numbers — blocked callers are rejected before the phone rings
- WhatsApp button, haptic feedback, SIM & network settings, sounds, about

## Becoming the default phone app

Android only lets the **default phone app** show the in-call screen, read the call log and
handle incoming calls. On first launch the app asks; you can also use
*Settings → Default phone app*. Without the role it still dials — Android just shows its own
call UI instead.

## Permissions

| Permission | Why |
| --- | --- |
| `CALL_PHONE` | Place calls, and choose the SIM |
| `READ_PHONE_STATE` | List the SIM slots so the keypad can show one button per SIM |
| `READ_CALL_LOG` | Build the Recents list |
| `READ_CONTACTS` | Show names while dialling, search the address book |

No internet permission is declared — nothing can leave the device.

## Not included

Deliberately out of scope, because each needs a separate platform integration: video calls,
call recording, voicemail, conference calling, and carrier services (the "Ulife" tab in some
OEM diallers). Conference/voicemail would come from extending `PhoneCallService` with a
multi-call listener.

## Install

Download **`Phone-v1.1.0.apk`** from
[Releases](https://github.com/mzazi89/phone/releases/latest), open it on the phone and allow
installs from unknown sources. Android 8.0 (API 26) or newer.

## Build

```bash
git clone https://github.com/mzazi89/phone.git
cd phone
./gradlew installDebug          # to a connected device
./gradlew assembleRelease       # → app/build/outputs/apk/release/app-release.apk
./gradlew test                  # T9 / formatting unit tests
```

JDK 17+ and Android SDK platform 34 / build-tools 34.0.0; point `ANDROID_HOME` or
`local.properties` at your SDK.

## How it works

| File | Responsibility |
| --- | --- |
| `PhoneActivity.kt` | Recents / Keypad / Contacts pages, permissions, SIM call buttons |
| `PhoneCallService.kt` | `InCallService`: owns the call, rejects blocked numbers, opens the call UI |
| `InCallActivity.kt` | In-call screen: answer, decline, hang up, mute, speaker, DTMF |
| `CallCenter.kt` | Bridge between the service (which owns audio) and the UI |
| `CallLogRepository.kt` | `CallLog.Calls` query → `CallEntry` |
| `SimManager.kt` | Phone accounts, SIM labels, `TelecomManager.placeCall` per SIM |
| `DialerRole.kt` | `ROLE_DIALER` check/request (RoleManager, TelecomManager below API 29) |
| `ContactsRepository.kt` | `ContactsContract` query grouped per person |
| `T9.kt` | Pure logic: digit mapping, normalisation, matching, formatting |

`T9` has no Android dependencies and is unit tested in `app/src/test`.

## CI

`.github/workflows/android.yml` runs the unit tests and builds a single release APK on every
push; a `v*` tag publishes it as a GitHub release.

## License

MIT — see [LICENSE](LICENSE).
