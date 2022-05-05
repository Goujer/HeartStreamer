package com.goujer.heartstreamer

//Intent Actions
const val UPDATE_HR_ACTION = "UPDATE_HR"
const val KILL_ACTION = "KILL_HR_SERVICE"

//Capabilities
const val PHONE_CAPABILITY = "heart_streamer_phone"
const val PHONE_KILL_CAPABILITY = "heart_streamer_phone_kill"
const val WEAR_CAPABILITY = "heart_streamer_wear"
const val WEAR_KILL_CAPABILITY = "heart_streamer_wear_kill"

//Paths
const val PHONE_PATH = "/$PHONE_CAPABILITY"
const val PHONE_KILL_PATH = "/$PHONE_KILL_CAPABILITY"
const val WEAR_PATH = "/$WEAR_CAPABILITY"
const val WEAR_KILL_PATH = "/$WEAR_KILL_CAPABILITY"