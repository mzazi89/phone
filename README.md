# Phone

A small, fast phone app for Android: dial, find a contact by typing a name, and call —
without an account, an internet connection, or a single tracker.

```
0712  →  Mary Wanjiku   0712 345 678
6279  →  Mary Wanjiku   0712 345 678     (T9: m-a-r-y)
```

## Features

**Dialling**
- Big T9 keypad with the usual letter groups, haptics, and long-press `0` for `+`
- Smart dial: type digits and matching contacts appear instantly, by number *or* by
  name converted to keypad digits
- Long-press backspace to clear the whole number
- `tel:` links from other apps open pre-filled

**Contacts**
- Reads the device address book through `ContactsContract` (no cloud, no sync)
- Search by name, by number, or by T9 digits
- Contacts with several numbers prompt for the one to call
- Initials avatars, no photo upload anywhere

**Calls**
- One tap to call via `ACTION_CALL` (`CALL_PHONE` permission)
- If the permission is denied or the device has no telephony, it falls back to
  `ACTION_DIAL` so the number still opens in the system dialler
- Local recents list of calls placed from this app

**Honest limits**
- This is not a replacement for the system dialer. It does not register as an
  `InCallService`, so it cannot show the in-call screen, handle incoming calls, or read
  the system call log — those require being the user's *default phone app*.
- Recents only contains calls started from this app.

## Permissions

| Permission | Why | When |
| --- | --- | --- |
| `CALL_PHONE` | Place a call directly | Asked the first time you tap the green button; denial falls back to the system dialler |
| `READ_CONTACTS` | Show names and search contacts | Asked from the Contacts tab; the app works as a plain keypad without it |

No internet permission is declared, so nothing can leave the device.

## Install

Download **`Phone-v1.0.0.apk`** from
[Releases](https://github.com/mzazi89/phone/releases/latest) and open it on the phone
(allow installs from unknown sources). Requires Android 8.0 (API 26) or newer.

## Build

```bash
git clone https://github.com/mzazi89/phone.git
cd phone
./gradlew installDebug          # to a connected device
./gradlew assembleRelease       # → app/build/outputs/apk/release/app-release.apk
./gradlew test                  # T9 / formatting unit tests
```

JDK 17+ and Android SDK platform 34 / build-tools 34.0.0 are required; point
`ANDROID_HOME` or `local.properties` at your SDK.

## How it works

| File | Responsibility |
| --- | --- |
| `PhoneActivity.kt` | Screens (keypad / contacts / recents), keypad construction, permission flows, call intents |
| `T9.kt` | Pure logic: digit mapping, number normalisation, matching, formatting, avatar initials |
| `ContactsRepository.kt` | `ContactsContract` query, grouped into one `Contact` per person |
| `RecentsStore.kt` | Local call list in `SharedPreferences` |
| `ContactAdapter.kt`, `RecentAdapter.kt` | RecyclerView adapters |

`T9` has no Android dependencies and is covered by unit tests in `app/src/test`.

## CI

`.github/workflows/android.yml` runs the unit tests and builds a single release APK on
every push; pushing a `v*` tag publishes it as a GitHub release.

## License

MIT — see [LICENSE](LICENSE).
