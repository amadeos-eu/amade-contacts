package eu.amadeos.apps.contacts.domain;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A birthday, with or without the year (vCard 4.0 writes one without the year as {@code --1114}).
 *
 * @param year 0 if unknown
 */
public record Birthday(int year, int month, int day) {
    private static final Pattern ISO = Pattern.compile("(\\d{4}|--|-)-?(\\d{2})-?(\\d{2}).*");
    private static final Pattern DOTTED = Pattern.compile("(\\d{1,2})\\.\\s*(\\d{1,2})\\.\\s*(\\d{4})?");
    private static final Pattern SLASHED = Pattern.compile("(\\d{1,2})/(\\d{1,2})/(\\d{2}|\\d{4})");

    public Birthday {
        MonthDay md = MonthDay.of(month, day);
        if (year < 0 || year > 9999 || (year != 0 && !md.isValidYear(year))) {
            throw new DateTimeException("No such day: " + year + "-" + month + "-" + day);
        }
    }

    public static Birthday of(LocalDate date) {
        return new Birthday(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
    }

    public boolean hasYear() {
        return year != 0;
    }

    /** The next birthday on or after {@code today}; 29 February is celebrated on 28 February in other years. */
    public LocalDate next(LocalDate today) {
        LocalDate date = on(today.getYear());
        return date.isBefore(today) ? on(today.getYear() + 1) : date;
    }

    /** Days from {@code today} to the next birthday, 0 if it is today. */
    public long daysUntil(LocalDate today) {
        return ChronoUnit.DAYS.between(today, next(today));
    }

    /** The age at the next birthday, if the year is known. */
    public Optional<Integer> ageAtNext(LocalDate today) {
        return hasYear() ? Optional.of(next(today).getYear() - year) : Optional.empty();
    }

    private LocalDate on(int y) {
        MonthDay md = MonthDay.of(month, day);
        return md.isValidYear(y) ? md.atYear(y) : LocalDate.of(y, 2, 28);
    }

    /** "14 November 1986" or "14 November" in the language of {@code locale}. */
    public String format(Locale locale) {
        LocalDate date = LocalDate.of(hasYear() ? year : 2000, month, day);
        if (hasYear()) return date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale));
        String pattern = locale.getLanguage().equals("de") ? "d. MMMM" : "d MMMM";
        return date.format(DateTimeFormatter.ofPattern(pattern, locale));
    }

    /** As typed in the editor: "14.11.1986", "14.11." or "1986-11-14". */
    public String toInput(Locale locale) {
        if (locale.getLanguage().equals("en")) {
            return hasYear() ? String.format("%04d-%02d-%02d", year, month, day) : String.format("--%02d-%02d", month, day);
        }
        String dayMonth = String.format("%02d.%02d.", day, month);
        return hasYear() ? dayMonth + year : dayMonth;
    }

    /**
     * Reads a date as programs write it: ISO {@code 1986-11-14}, {@code 19861114}, {@code --1114},
     * {@code --11-14}, German {@code 14.11.1986} or {@code 14.11.}, and with slashes {@code 11/14/1986}
     * (month first for US English, day first otherwise, and month first whenever the first number is no month).
     */
    public static Optional<Birthday> parse(String text, Locale locale) {
        String t = text == null ? "" : text.strip();
        if (t.isEmpty()) return Optional.empty();
        try {
            Matcher m = ISO.matcher(t);
            if (m.matches()) {
                int year = m.group(1).startsWith("-") ? 0 : Integer.parseInt(m.group(1));
                return Optional.of(of(year, Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))));
            }
            m = DOTTED.matcher(t);
            if (m.matches()) {
                int year = m.group(3) == null ? 0 : Integer.parseInt(m.group(3));
                return Optional.of(of(year, Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1))));
            }
            m = SLASHED.matcher(t);
            if (m.matches()) {
                int a = Integer.parseInt(m.group(1));
                int b = Integer.parseInt(m.group(2));
                int year = Integer.parseInt(m.group(3));
                if (year < 100) year += year > LocalDate.now().getYear() % 100 ? 1900 : 2000;
                boolean monthFirst = a > 12 ? false : b > 12 || monthFirst(locale);
                return Optional.of(monthFirst ? of(year, a, b) : of(year, b, a));
            }
        } catch (DateTimeException | NumberFormatException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    /** Apple and Outlook write 1604 for a birthday without the year. */
    private static Birthday of(int year, int month, int day) {
        return new Birthday(year == 1604 ? 0 : year, month, day);
    }

    /** Dates with slashes are month first in American English. */
    private static boolean monthFirst(Locale locale) {
        return locale.getLanguage().equals("en")
                && !java.util.Set.of("GB", "IE", "AU", "NZ", "IN", "ZA").contains(locale.getCountry());
    }
}
