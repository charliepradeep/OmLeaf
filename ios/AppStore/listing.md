# OmLeaf: App Store Connect checklist and copy

## Pricing
**Free.** No ads, no in-app purchases. (A free app can later add In-App Purchases, but cannot become a paid download.)

## App information
| Field | Value |
|---|---|
| Name (max 30) | OmLeaf: Pranayama & AUM Timer |
| Subtitle (max 30) | Breath, sound and stillness |
| Bundle ID | com.charrlie.omleaf |
| Category | Health & Fitness |
| Age rating | 4+ |
| Privacy policy URL | host `android/OmLeafStore/privacy-policy.html` (for example GitHub Pages) |
| Support email | charrliedeveloper@gmail.com |
| Keywords (max 100) | pranayama,breathing,om,aum,chanting,meditation,yoga,timer,breathwork,nadi shodhana,kapalabhati |

**Promotional text:** A calm, private timer for pranayama and AUM chanting.

**Description:** use the Play Store description in `android/OmLeafStore/listing.md`.

## App Privacy
Data not collected. The app has no network code and stores everything on the device.

## Export compliance
`ITSAppUsesNonExemptEncryption` is already set to false in Info.plist.

## Review notes
No login is needed. The app plays generated tones; turn the volume up and tap any practice, then Begin.

## Still to do
- Apple Developer enrollment and a team ID (set `DEVELOPMENT_TEAM` in `project.yml`).
- Screenshots: 6.9-inch iPhone and 13-inch iPad.
