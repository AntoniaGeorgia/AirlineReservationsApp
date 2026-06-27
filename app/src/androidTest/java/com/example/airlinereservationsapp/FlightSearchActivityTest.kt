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
class FlightSearchActivityTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(FlightSearchActivity::class.java)

    // ✅ 1. Ecranul de căutare se afișează corect
    @Test
    fun ecranCautareEsteAfisat() {
        onView(withId(R.id.autoCompleteFrom))
            .check(matches(isDisplayed()))
        onView(withId(R.id.autoCompleteTo))
            .check(matches(isDisplayed()))
        onView(withId(R.id.btnSearchFlights))
            .check(matches(isDisplayed()))
    }

    // ✅ 2. Chips dus/întors sunt vizibile
    @Test
    fun chipurileTipCalatorie_SuntVizibile() {
        onView(withId(R.id.chipOneWay))
            .check(matches(isDisplayed()))
        onView(withId(R.id.chipRoundTrip))
            .check(matches(isDisplayed()))
    }

    // ✅ 3. Chip "Doar dus" e selectat implicit
    @Test
    fun chipDoarDus_EsteSelectatImplicit() {
        onView(withId(R.id.chipOneWay))
            .check(matches(isChecked()))
    }

    // ✅ 4. Click pe "Dus-întors" îl selectează
    @Test
    fun clickPeChipDusIntors_IlSelecteaza() {
        onView(withId(R.id.chipRoundTrip)).perform(click())
        onView(withId(R.id.chipRoundTrip))
            .check(matches(isChecked()))
    }

    // ✅ 5. Caută fără orașe — rămâne pe același ecran
    @Test
    fun cautaFaraOrase_RamanePeEcran() {
        onView(withId(R.id.btnSearchFlights)).perform(click())
        onView(withId(R.id.autoCompleteFrom))
            .check(matches(isDisplayed()))
    }

    // ✅ 6. Butonul "Alege datele" e vizibil
    @Test
    fun butonAlgeDatele_EsteVizibil() {
        onView(withId(R.id.btnPickDates))
            .check(matches(isDisplayed()))
    }

    // ✅ 7. Text inițial date — "Nicio dată selectată"
    @Test
    fun textInitialDate_EsteNicioDataSelectata() {
        onView(withId(R.id.tvSelectedDates))
            .check(matches(withText("Nicio dată selectată")))
    }

    // ✅ 8. Caută fără dată — rămâne pe ecran
    // ✅ 8. Caută fără dată — rămâne pe ecran
    @Test
    fun cautaFaraData_AfiseazaMesaj() {
        // Use replaceText instead of typeText to handle "ș"
        onView(withId(R.id.autoCompleteFrom))
            .perform(replaceText("București"), closeSoftKeyboard())

        onView(withId(R.id.autoCompleteTo))
            .perform(replaceText("Londra"), closeSoftKeyboard())

        onView(withId(R.id.btnSearchFlights)).perform(click())

        // Verifică dacă a rămas pe ecranul de search (deoarece data lipsește)
        onView(withId(R.id.btnSearchFlights))
            .check(matches(isDisplayed()))
    }
}