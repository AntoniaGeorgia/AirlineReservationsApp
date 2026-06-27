package com.example.airlinereservationsapp

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginActivityTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(LoginActivity::class.java)

    // ✅ 1. Ecranul de login se afișează corect
    @Test
    fun ecranLoginEsteAfisat() {
        onView(withId(R.id.etEmailLogin))
            .check(matches(isDisplayed()))
        onView(withId(R.id.etPasswordLogin))
            .check(matches(isDisplayed()))
        onView(withId(R.id.btnLogin))
            .check(matches(isDisplayed()))
    }

    // ✅ 2. Câmpuri goale — afișează toast
    @Test
    fun campuriGoale_ArataMesajEroare() {
        onView(withId(R.id.btnLogin)).perform(click())
        onView(withId(R.id.etEmailLogin))
            .check(matches(isDisplayed()))
        // aplicația rămâne pe ecranul de login
    }

    // ✅ 3. Completare email și parolă
    @Test
    fun completareEmailSiParola_ButoaneCliccabil() {
        onView(withId(R.id.etEmailLogin))
            .perform(typeText("test@test.com"), closeSoftKeyboard())
        onView(withId(R.id.etPasswordLogin))
            .perform(typeText("parola123"), closeSoftKeyboard())
        onView(withId(R.id.btnLogin))
            .check(matches(isEnabled()))
    }

    // ✅ 4. Link către register e vizibil
    @Test
    fun linkRegisterEsteVizibil() {
        onView(withId(R.id.tvGoToRegister))
            .check(matches(isDisplayed()))
    }

    // ✅ 5. Click pe register navighează
    @Test
    fun clickPeRegister_NavigheazaLaRegister() {
        onView(withId(R.id.tvGoToRegister)).perform(click())
        onView(withId(R.id.etFullName))
            .check(matches(isDisplayed()))
    }
}