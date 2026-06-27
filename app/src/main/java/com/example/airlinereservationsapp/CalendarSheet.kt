package com.example.airlinereservationsapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

class CalendarSheet : BottomSheetDialogFragment() {

    var onDatesSelected: ((departureDate: String, returnDate: String?) -> Unit)? = null
    private var fromCode: String = ""
    private var toCode: String = ""
    private var isRoundTrip: Boolean = false
    private var selectedStart: String? = null
    private var selectedEnd: String? = null
    private var currentYear = 2026
    private var currentMonth = 7
    private lateinit var rvCalendar: RecyclerView
    private lateinit var tvMonthYear: TextView
    private lateinit var btnContinue: Button
    private lateinit var btnReset: Button
    private lateinit var tvSelectionInfo: TextView
    private var calendarAdapter: CalendarAdapter? = null

    companion object {
        fun newInstance(from: String, to: String, roundTrip: Boolean = false): CalendarSheet {
            return CalendarSheet().apply {
                fromCode = from
                toCode = to
                isRoundTrip = roundTrip
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_calendar, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvCalendar = view.findViewById(R.id.rvCalendar)
        tvMonthYear = view.findViewById(R.id.tvMonthYear)
        btnContinue = view.findViewById(R.id.btnContinue)
        btnReset = view.findViewById(R.id.btnReset)
        tvSelectionInfo = view.findViewById(R.id.tvSelectionInfo)

        view.findViewById<View>(R.id.btnOneway).visibility = View.GONE
        view.findViewById<View>(R.id.btnRoundtrip).visibility = View.GONE

        rvCalendar.layoutManager = GridLayoutManager(requireContext(), 7)
        calendarAdapter = CalendarAdapter(emptyList(), null, null) { handleDayClick(it) }
        rvCalendar.adapter = calendarAdapter

        updateSelectionInfo()
        loadCalendar()

        view.findViewById<View>(R.id.btnPrevMonth).setOnClickListener {
            if (currentMonth == 1) { currentMonth = 12; currentYear-- } else currentMonth--
            loadCalendar()
        }

        view.findViewById<View>(R.id.btnNextMonth).setOnClickListener {
            if (currentMonth == 12) { currentMonth = 1; currentYear++ } else currentMonth++
            loadCalendar()
        }

        btnReset.setOnClickListener {
            selectedStart = null
            selectedEnd = null
            calendarAdapter?.updateSelection(null, null)
            updateSelectionInfo()
        }

        btnContinue.setOnClickListener {
            if (selectedStart == null) {
                Toast.makeText(requireContext(), "Alege data de plecare!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (isRoundTrip && selectedEnd == null) {
                Toast.makeText(requireContext(), "Alege și data de întoarcere!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            onDatesSelected?.invoke(selectedStart!!, if (isRoundTrip) selectedEnd else null)
            dismiss()
        }
    }

    private fun handleDayClick(date: String) {
        if (!isRoundTrip) {
            selectedStart = date
            selectedEnd = null
        } else {
            when {
                selectedStart == null || (selectedStart != null && selectedEnd != null) -> {
                    selectedStart = date
                    selectedEnd = null
                }
                date > selectedStart!! -> selectedEnd = date
                else -> { selectedStart = date; selectedEnd = null }
            }
        }
        calendarAdapter?.updateSelection(selectedStart, selectedEnd)
        updateSelectionInfo()
    }

    private fun updateSelectionInfo() {
        tvSelectionInfo.text = when {
            selectedStart == null -> if (isRoundTrip) "Selectează data de plecare" else "Selectează data dorită"
            isRoundTrip && selectedEnd == null -> "Plecare: $selectedStart — acum alege întoarcerea"
            isRoundTrip -> "✈ $selectedStart  ↩ $selectedEnd"
            else -> "✈ Plecare: $selectedStart"
        }
    }

    private fun loadCalendar() {
        tvMonthYear.text = "${getMonthName(currentMonth)} $currentYear"
        lifecycleScope.launch {
            val prices = try {
                val monthQuery = String.format("%04d-%02d", currentYear, currentMonth)
                val response = RetrofitInstance.api.getCalendarPrices(fromCode, toCode, monthQuery)
                if (response.isSuccessful) response.body() ?: emptyMap() else emptyMap()
            } catch (e: Exception) { emptyMap() }

            val days = generateDays(currentYear, currentMonth, prices)
            calendarAdapter = CalendarAdapter(days, selectedStart, selectedEnd) { handleDayClick(it) }
            rvCalendar.adapter = calendarAdapter
        }
    }

    private fun generateDays(year: Int, month: Int, prices: Map<String, Double>): List<CalendarDay> {
        val days = mutableListOf<CalendarDay>()
        val yearMonth = YearMonth.of(year, month)
        val today = LocalDate.now().toString()
        repeat(yearMonth.atDay(1).dayOfWeek.value - 1) {
            days.add(CalendarDay(0, "", null, false))
        }
        for (d in 1..yearMonth.lengthOfMonth()) {
            val dateStr = String.format("%04d-%02d-%02d", year, month, d)
            val isPast = dateStr < today
            days.add(CalendarDay(d, dateStr, if (isPast) null else prices[dateStr], !isPast, dateStr == today))
        }
        return days
    }

    private fun getMonthName(month: Int) = listOf(
        "Ianuarie", "Februarie", "Martie", "Aprilie", "Mai", "Iunie",
        "Iulie", "August", "Septembrie", "Octombrie", "Noiembrie", "Decembrie"
    )[month - 1]
}