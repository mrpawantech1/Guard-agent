# Guard Agent

Personal anti-theft & remote control app for Android.

## Features

- Live location tracking
- Remote device lock
- Loud alarm
- SIM change alert
- Camera photo (on-demand)
- Audio recording (on-demand)
- Screen recording (on-demand)
- Battery & network info
- Contacts, SMS, Call logs
- Gallery access
- Keylogger (manual enable)
- Notification reader
- Geofence alerts

## Setup

1. **Firebase Setup**
   - Create project at console.firebase.google.com
   - Add Android app with package `com.guard.agent`
   - Download `google-services.json`
   - Enable Realtime Database, Storage, Cloud Messaging

2. **GitHub Secret**
   - Repo → Settings → Secrets → Actions
   - Add secret: `GOOGLE_SERVICES_JSON`
   - Paste content of google-services.json

3. **Build APK**
   - Push to `main` branch
   - GitHub Actions will auto-build
   - Download from Actions → Artifacts

4. **Install & Setup**
   - Install APK on phone
   - Grant all permissions (6 pop-ups)
   - Enable Device Admin
   - Battery optimization → Don't optimize
   - Autostart ON
   - Accessibility ON
   - Notification access ON
   - Hide icon & start

5. **Control via Web Panel**
   - Deploy web-panel to Netlify/Vercel
   - Login with device key
   - Control from anywhere

## Commands

| Command | Action |
|---------|--------|
| LOCATION | Send location |
| SNAP | Camera photo |
| REC | Audio record 60s |
| SCREENREC | Screen record 30s |
| LOCK | Lock device |
| ALARM | Loud alarm |
| WIPE | Factory reset |

## Legal

For personal use only. Do not use on others' devices.
