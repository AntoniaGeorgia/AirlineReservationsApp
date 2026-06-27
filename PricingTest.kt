package com.example

import com.example.plugins.calculateDynamicPrice
import kotlin.test.Test
import kotlin.test.assertEquals

class PricingTest {

    @Test
    fun pretDeBazaCandAvionulEGol() {
        val result = calculateDynamicPrice(200.0, 180, 0)
        assertEquals(200.0, result, 0.01)
    }

    @Test
    fun pretDeBazaSub50Procente() {
        val result = calculateDynamicPrice(200.0, 180, 89)
        assertEquals(200.0, result, 0.01)
    }

    @Test
    fun pretCresteWith20LaExact50Procente() {
        val result = calculateDynamicPrice(200.0, 180, 90)
        assertEquals(240.0, result, 0.01)
    }

    @Test
    fun pretCresteWith20Intre50Si80Procente() {
        val result = calculateDynamicPrice(300.0, 180, 120)
        assertEquals(360.0, result, 0.01)
    }

    @Test
    fun pretCresteWith50LaExact80Procente() {
        val result = calculateDynamicPrice(200.0, 180, 144)
        assertEquals(300.0, result, 0.01)
    }

    @Test
    fun pretCresteWith50CandAvionulEPlin() {
        val result = calculateDynamicPrice(200.0, 180, 180)
        assertEquals(300.0, result, 0.01)
    }

    @Test
    fun returneazaPretDeBazaCandTotalSeatsEZero() {
        val result = calculateDynamicPrice(200.0, 0, 0)
        assertEquals(200.0, result, 0.01)
    }
}