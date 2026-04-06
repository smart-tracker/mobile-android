package com.example.smarttracker.data.location.model

/**
 * Среда выполнения GPS-провайдера, обнаруженная [RuntimeDetector] при старте.
 *
 * - [GMS]  — устройство Google с Play Services (большинство Android-смартфонов)
 * - [HMS]  — Huawei без GMS (P40, Mate 40 и новее)
 * - [AOSP] — устройства без GMS и HMS (кастомные прошивки, часть China-сборок, эмулятор AOSP)
 */
enum class LocationRuntime { GMS, HMS, AOSP }
