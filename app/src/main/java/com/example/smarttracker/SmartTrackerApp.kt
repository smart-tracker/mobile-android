package com.example.smarttracker

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application класс. Аннотация @HiltAndroidApp запускает
 * кодогенерацию Hilt и инициализирует граф зависимостей.
 */
@HiltAndroidApp
class SmartTrackerApp : Application()
