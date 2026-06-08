package com.example.catechismapp

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

class CustomTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application {
        return super.newApplication(cl, TestCatechismApp::class.java.name, context)
    }
}

class TestCatechismApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Test environment: no background database seeding to prevent DataStore/Room races
    }
}
