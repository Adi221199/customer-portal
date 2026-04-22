package com.scai.customer_portal.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Inclusive local-date ranges for analytics and chat query windows.
 */
public final class ReportTimeWindows {

	public record DateRange(String label, LocalDate start, LocalDate end) {
	}

	private ReportTimeWindows() {
	}

	public static DateRange rangeFor(SummaryPeriod p, LocalDate today) {
		return switch (p) {
			case LAST_1_DAY -> {
				LocalDate s = today.minusDays(1);
				yield new DateRange("Last ~1 day (yesterday and today, inclusive)", s, today);
			}
			case THIS_WEEK -> {
				LocalDate start = today.with(DayOfWeek.MONDAY);
				yield new DateRange("This calendar week (Mon–today)", start, today);
			}
			case LAST_7_DAYS -> {
				LocalDate s = today.minusDays(6);
				yield new DateRange("Last 7 days (including today)", s, today);
			}
			case THIS_MONTH -> {
				LocalDate s = today.withDayOfMonth(1);
				yield new DateRange("This month (1st – today)", s, today);
			}
			case LAST_30_DAYS -> {
				LocalDate s = today.minusDays(29);
				yield new DateRange("Last 30 days (including today)", s, today);
			}
			case LAST_MONTH -> {
				YearMonth ym = YearMonth.from(today).minusMonths(1);
				LocalDate s = ym.atDay(1);
				LocalDate e = ym.atEndOfMonth();
				yield new DateRange("Previous calendar month", s, e);
			}
		};
	}
}
