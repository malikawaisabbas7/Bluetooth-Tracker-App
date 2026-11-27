# Bluetooth Tracker Privacy Policy

**Effective date** November 27, 2026

This policy explains how **Awais Abbas** handles information in the Bluetooth Tracker Android app.

## Nearby Bluetooth devices and phone location

Bluetooth Tracker scans for nearby Bluetooth Low Energy devices. It records information from their advertisements, such as Bluetooth addresses, advertised names, manufacturer information and signal strength. It also records detection times, estimated proximity and whether a device is bonded to your phone. The app uses these details to show nearby devices and identify patterns of repeated sightings.

When you grant location permission and a location fix is available, the app can associate a sighting with your phone’s location at the time. The app uses those coordinates to display sightings on a map and check whether a supported tracker was detected in multiple places. These coordinates describe where your phone was during detection.

If you turn on Background Tracking, the app periodically scans and can record sightings and phone locations while the app is out of view. Android runs this feature as a foreground service. If notifications are allowed, the app can notify you about repeated detections of supported trackers.

The app stores Bluetooth scan records and sighting coordinates on your device. It does not upload your scan history to a developer-operated server. The app does not require you to create a Bluetooth Tracker account.

## Services used by the app

- **Google Play services.** The app uses Google’s fused location provider to obtain your phone’s location when you grant permission. Google Play handles subscription checkout and provides purchase status to the app. The app keeps the verification result in memory for a short period. Payment card details are handled by Google Play. For subscription checks, the app sends the Google Play purchase token to our verification service over HTTPS. The service uses the token to check the app, product and subscription status with Google Play and acknowledge eligible purchases. The verifier does not store purchase tokens or log request bodies. Basic request metadata, such as request time and status, may be processed to operate and protect the service. [Google Privacy Policy](https://policies.google.com/privacy)
- **OpenStreetMap.** When a map is displayed, the app requests tiles from OpenStreetMap. These requests include your IP address, information identifying the app, and tile coordinates that indicate the map area being displayed. If you choose to open a sighting in an external map, its coordinates are passed to the map app or website you open. [OpenStreetMap Foundation Privacy Policy](https://osmfoundation.org/wiki/Privacy_Policy)
- **Customer support.** The public GitHub issue tracker is available for general support. The app does not send your information when it opens GitHub. Anything you choose to post there is public. Leave out personal details, Bluetooth addresses, purchase tokens, and location history. [GitHub Privacy Statement](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement)

## Your controls, retention and deletion

You can turn off Background Tracking in the app and change Bluetooth, location and notification permissions in Android Settings. Turning off tracking or revoking permissions leaves existing history on the device.

Bluetooth sightings and settings are stored locally without a scheduled deletion period. You can remove them by clearing Bluetooth Tracker’s storage in Android Settings or uninstalling the app. Clearing app data does not cancel a Google Play subscription. Manage or cancel subscriptions in Google Play.

For general privacy questions, use the [privacy and support contact](https://github.com/malikawaisabbas7/Bluetooth-Tracker-App/issues/new). The form is public. Leave out private information. You can edit or request removal of information posted through GitHub.

## Changes to this policy

We will post updates to this page and revise the effective date.
