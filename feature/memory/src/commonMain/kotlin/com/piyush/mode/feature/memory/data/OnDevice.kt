package com.piyush.mode.feature.memory.data

/** A prompt written for the iPhone, naming the phone Mode actually runs on ("their Android phone"). */
internal fun String.onDevice(device: String): String = if (device == IPHONE) this else replace(IPHONE, device)

private const val IPHONE = "iPhone"
