package com.example.airlinereservationsapp

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.containsString
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaymentActivityTest {

    private fun launchWithBookingId(bookingId: Int): ActivityScenario<PaymentActivity> {
        val intent = Intent(
            InstrumentationRegistry.getInstrumentation().targetContext,
            PaymentActivity::class.java
        ).apply {
            putExtra("BOOKING_ID", bookingId)
            putExtra("FINAL_PRICE", 299.99)
        }
        return ActivityScenario.launch(intent)
    }

    @Test
    fun ecranPlata_AfiseazaPretulCorect() {
        launchWithBookingId(1).use {
            onView(withId(R.id.tvFinalPrice))
                .check(matches(withText(containsString("299"))))
        }
    }

    @Test
    fun campuriCard_SuntVizibile() {
        launchWithBookingId(1).use {
            onView(withId(R.id.etCardNumber)).check(matches(isDisplayed()))
            onView(withId(R.id.etExpiry)).check(matches(isDisplayed()))
            onView(withId(R.id.etCVV)).check(matches(isDisplayed()))
            onView(withId(R.id.etCardHolder)).check(matches(isDisplayed()))
            onView(withId(R.id.btnPayNow)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun cardNumarPreaScurt_AfiseazaEroare() {
        launchWithBookingId(1).use {
            onView(withId(R.id.etCardNumber)).perform(replaceText("1234"), closeSoftKeyboard())
            onView(withId(R.id.btnPayNow)).perform(click())
            onView(withId(R.id.etCardNumber))
                .check(matches(hasErrorText("Număr de card incomplet (16 cifre)")))
        }
    }

    @Test
    fun expiryFormatInvalid_AfiseazaEroare() {
        launchWithBookingId(1).use {
            onView(withId(R.id.etCardNumber)).perform(replaceText("1234567890123456"))
            onView(withId(R.id.etExpiry)).perform(replaceText("1234"), closeSoftKeyboard())
            onView(withId(R.id.btnPayNow)).perform(click())
            onView(withId(R.id.etExpiry)).check(matches(hasErrorText("Format invalid (MM/YY)")))
        }
    }

    @Test
    fun cvvPreaScurt_AfiseazaEroare() {
        launchWithBookingId(1).use {
            onView(withId(R.id.etCardNumber)).perform(replaceText("1234567890123456"))
            onView(withId(R.id.etExpiry)).perform(replaceText("12/26"))
            onView(withId(R.id.etCVV)).perform(replaceText("12"), closeSoftKeyboard())
            onView(withId(R.id.btnPayNow)).perform(click())
            onView(withId(R.id.etCVV)).check(matches(hasErrorText("CVV invalid")))
        }
    }

    @Test
    fun bookingIdInvalid_ActivitySeInchide() {
        val intent = Intent(
            InstrumentationRegistry.getInstrumentation().targetContext,
            PaymentActivity::class.java
        ).apply {
            putExtra("BOOKING_ID", -1)
            putExtra("FINAL_PRICE", 0.0)
        }
        ActivityScenario.launch<PaymentActivity>(intent).use { scenario ->
            // Verificăm dacă Activity-ul a ajuns în starea Destroyed (s-a închis singur)
            assert(scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED)
        }
    }
}