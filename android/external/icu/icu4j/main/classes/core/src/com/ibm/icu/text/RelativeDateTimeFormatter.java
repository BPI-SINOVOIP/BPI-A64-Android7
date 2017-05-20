/*
 *******************************************************************************
 * Copyright (C) 2013-2016, International Business Machines Corporation and
 * others. All Rights Reserved.
 *******************************************************************************
 */
package com.ibm.icu.text;

import java.util.EnumMap;
import java.util.Locale;

import com.ibm.icu.impl.CalendarData;
import com.ibm.icu.impl.DontCareFieldPosition;
import com.ibm.icu.impl.ICUCache;
import com.ibm.icu.impl.ICUResourceBundle;
import com.ibm.icu.impl.SimpleCache;
import com.ibm.icu.impl.SimplePatternFormatter;
import com.ibm.icu.impl.StandardPlural;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.util.Calendar;
import com.ibm.icu.util.ICUException;
import com.ibm.icu.util.ULocale;
import com.ibm.icu.impl.UResource;
import com.ibm.icu.util.UResourceBundle;


/**
 * Formats simple relative dates. There are two types of relative dates that
 * it handles:
 * <ul>
 *   <li>relative dates with a quantity e.g "in 5 days"</li>
 *   <li>relative dates without a quantity e.g "next Tuesday"</li>
 * </ul>
 * <p>
 * This API is very basic and is intended to be a building block for more
 * fancy APIs. The caller tells it exactly what to display in a locale
 * independent way. While this class automatically provides the correct plural
 * forms, the grammatical form is otherwise as neutral as possible. It is the
 * caller's responsibility to handle cut-off logic such as deciding between
 * displaying "in 7 days" or "in 1 week." This API supports relative dates
 * involving one single unit. This API does not support relative dates
 * involving compound units.
 * e.g "in 5 days and 4 hours" nor does it support parsing.
 * This class is both immutable and thread-safe.
 * <p>
 * Here are some examples of use:
 * <blockquote>
 * <pre>
 * RelativeDateTimeFormatter fmt = RelativeDateTimeFormatter.getInstance();
 * fmt.format(1, Direction.NEXT, RelativeUnit.DAYS); // "in 1 day"
 * fmt.format(3, Direction.NEXT, RelativeUnit.DAYS); // "in 3 days"
 * fmt.format(3.2, Direction.LAST, RelativeUnit.YEARS); // "3.2 years ago"
 * 
 * fmt.format(Direction.LAST, AbsoluteUnit.SUNDAY); // "last Sunday"
 * fmt.format(Direction.THIS, AbsoluteUnit.SUNDAY); // "this Sunday"
 * fmt.format(Direction.NEXT, AbsoluteUnit.SUNDAY); // "next Sunday"
 * fmt.format(Direction.PLAIN, AbsoluteUnit.SUNDAY); // "Sunday"
 * 
 * fmt.format(Direction.LAST, AbsoluteUnit.DAY); // "yesterday"
 * fmt.format(Direction.THIS, AbsoluteUnit.DAY); // "today"
 * fmt.format(Direction.NEXT, AbsoluteUnit.DAY); // "tomorrow"
 * 
 * fmt.format(Direction.PLAIN, AbsoluteUnit.NOW); // "now"
 * </pre>
 * </blockquote>
 * <p>
 * In the future, we may add more forms, such as abbreviated/short forms
 * (3 secs ago), and relative day periods ("yesterday afternoon"), etc.
 * 
 * @stable ICU 53
 */
public final class RelativeDateTimeFormatter {
    
    /**
     * The formatting style
     * @stable ICU 54
     *
     */
    public static enum Style {
        
        /**
         * Everything spelled out.
         * @stable ICU 54
         */
        LONG,
        
        /**
         * Abbreviations used when possible.
         * @stable ICU 54
         */
        SHORT,
        
        /**
         * Use single letters when possible.
         * @stable ICU 54
         */
        NARROW;

        private static final int INDEX_COUNT = 3;  // NARROW.ordinal() + 1
    }

    /**
     * Represents the unit for formatting a relative date. e.g "in 5 days"
     * or "in 3 months"
     * @stable ICU 53
     */
    public static enum RelativeUnit {
        
        /**
         * Seconds
         * @stable ICU 53
         */
        SECONDS,
        
        /**
         * Minutes
         * @stable ICU 53
         */
        MINUTES,
        
       /**
        * Hours
        * @stable ICU 53
        */
        HOURS,
        
        /**
         * Days
         * @stable ICU 53
         */
        DAYS,
        
        /**
         * Weeks
         * @stable ICU 53
         */
        WEEKS,
        
        /**
         * Months
         * @stable ICU 53
         */
        MONTHS,
        
        /**
         * Years
         * @stable ICU 53
         */
        YEARS,

        /**
         * Quarters
         * @internal TODO: propose for addition in ICU 57
         * @deprecated This API is ICU internal only.
         */
        @Deprecated
        QUARTERS,
    }
    
    /**
     * Represents an absolute unit.
     * @stable ICU 53
     */
    public static enum AbsoluteUnit {
        
       /**
        * Sunday
        * @stable ICU 53
        */
        SUNDAY,
        
        /**
         * Monday
         * @stable ICU 53
         */
        MONDAY,
        
        /**
         * Tuesday
         * @stable ICU 53
         */
        TUESDAY,
        
        /**
         * Wednesday
         * @stable ICU 53
         */
        WEDNESDAY,
        
        /**
         * Thursday
         * @stable ICU 53
         */
        THURSDAY,
        
        /**
         * Friday
         * @stable ICU 53
         */
        FRIDAY,
        
        /**
         * Saturday
         * @stable ICU 53
         */
        SATURDAY,
        
        /**
         * Day
         * @stable ICU 53
         */
        DAY,
        
        /**
         * Week
         * @stable ICU 53
         */
        WEEK,
        
        /**
         * Month
         * @stable ICU 53
         */
        MONTH,
        
        /**
         * Year
         * @stable ICU 53
         */
        YEAR,
        
        /**
         * Now
         * @stable ICU 53
         */
        NOW,

        /**
         * Quarter
         * @internal TODO: propose for addition in ICU 57
         * @deprecated This API is ICU internal only.
         */
        @Deprecated
        QUARTER,
      }

      /**
       * Represents a direction for an absolute unit e.g "Next Tuesday"
       * or "Last Tuesday"
       * @stable ICU 53
       */
      public static enum Direction {
          /**
           * Two before. Not fully supported in every locale
           * @stable ICU 53
           */
          LAST_2,

          /**
           * Last
           * @stable ICU 53
           */  
          LAST,

          /**
           * This
           * @stable ICU 53
           */
          THIS,

          /**
           * Next
           * @stable ICU 53
           */
          NEXT,

          /**
           * Two after. Not fully supported in every locale
           * @stable ICU 53
           */
          NEXT_2,

          /**
           * Plain, which means the absence of a qualifier
           * @stable ICU 53
           */
          PLAIN;
      }

    /**
     * Returns a RelativeDateTimeFormatter for the default locale.
     * @stable ICU 53
     */
    public static RelativeDateTimeFormatter getInstance() {
        return getInstance(ULocale.getDefault(), null, Style.LONG, DisplayContext.CAPITALIZATION_NONE);
    }

    /**
     * Returns a RelativeDateTimeFormatter for a particular locale.
     * 
     * @param locale the locale.
     * @return An instance of RelativeDateTimeFormatter.
     * @stable ICU 53
     */
    public static RelativeDateTimeFormatter getInstance(ULocale locale) {
        return getInstance(locale, null, Style.LONG, DisplayContext.CAPITALIZATION_NONE);
    }

    /**
     * Returns a RelativeDateTimeFormatter for a particular {@link java.util.Locale}.
     * 
     * @param locale the {@link java.util.Locale}.
     * @return An instance of RelativeDateTimeFormatter.
     * @stable ICU 54
     */
    public static RelativeDateTimeFormatter getInstance(Locale locale) {
        return getInstance(ULocale.forLocale(locale));
    }

    /**
     * Returns a RelativeDateTimeFormatter for a particular locale that uses a particular
     * NumberFormat object.
     * 
     * @param locale the locale
     * @param nf the number format object. It is defensively copied to ensure thread-safety
     * and immutability of this class. 
     * @return An instance of RelativeDateTimeFormatter.
     * @stable ICU 53
     */
    public static RelativeDateTimeFormatter getInstance(ULocale locale, NumberFormat nf) {
        return getInstance(locale, nf, Style.LONG, DisplayContext.CAPITALIZATION_NONE);
    }
 
    /**
     * Returns a RelativeDateTimeFormatter for a particular locale that uses a particular
     * NumberFormat object, style, and capitalization context
     * 
     * @param locale the locale
     * @param nf the number format object. It is defensively copied to ensure thread-safety
     * and immutability of this class. May be null.
     * @param style the style.
     * @param capitalizationContext the capitalization context.
     * @stable ICU 54
     */
    public static RelativeDateTimeFormatter getInstance(
            ULocale locale,
            NumberFormat nf,
            Style style,
            DisplayContext capitalizationContext) {
        RelativeDateTimeFormatterData data = cache.get(locale);
        if (nf == null) {
            nf = NumberFormat.getInstance(locale);
        } else {
            nf = (NumberFormat) nf.clone();
        }
        return new RelativeDateTimeFormatter(
                data.qualitativeUnitMap,
                data.relUnitPatternMap,
                new MessageFormat(data.dateTimePattern),
                PluralRules.forLocale(locale),
                nf,
                style,
                capitalizationContext,
                capitalizationContext == DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE ?
                    BreakIterator.getSentenceInstance(locale) : null,
                locale);
    }

    /**
     * Returns a RelativeDateTimeFormatter for a particular {@link java.util.Locale} that uses a
     * particular NumberFormat object.
     * 
     * @param locale the {@link java.util.Locale}
     * @param nf the number format object. It is defensively copied to ensure thread-safety
     * and immutability of this class. 
     * @return An instance of RelativeDateTimeFormatter.
     * @stable ICU 54
     */
    public static RelativeDateTimeFormatter getInstance(Locale locale, NumberFormat nf) {
        return getInstance(ULocale.forLocale(locale), nf);
    }

    /**
     * Formats a relative date with a quantity such as "in 5 days" or
     * "3 months ago"
     * @param quantity The numerical amount e.g 5. This value is formatted
     * according to this object's {@link NumberFormat} object.
     * @param direction NEXT means a future relative date; LAST means a past
     * relative date.
     * @param unit the unit e.g day? month? year?
     * @return the formatted string
     * @throws IllegalArgumentException if direction is something other than
     * NEXT or LAST.
     * @stable ICU 53
     */
    public String format(double quantity, Direction direction, RelativeUnit unit) {
        if (direction != Direction.LAST && direction != Direction.NEXT) {
            throw new IllegalArgumentException("direction must be NEXT or LAST");
        }
        String result;
        int pastFutureIndex = (direction == Direction.NEXT ? 1 : 0);

        // This class is thread-safe, yet numberFormat is not. To ensure thread-safety of this
        // class we must guarantee that only one thread at a time uses our numberFormat.
        synchronized (numberFormat) {
            StringBuffer formatStr = new StringBuffer();
            DontCareFieldPosition fieldPosition = DontCareFieldPosition.INSTANCE;
            StandardPlural pluralForm = QuantityFormatter.selectPlural(quantity,
                    numberFormat, pluralRules, formatStr, fieldPosition);

            String formatter = getRelativeUnitPluralPattern(style, unit, pastFutureIndex, pluralForm);
            result = SimplePatternFormatter.formatCompiledPattern(formatter, formatStr);
        }
        return adjustForContext(result);

    }

    private int[] styleToDateFormatSymbolsWidth = {
                DateFormatSymbols.WIDE, DateFormatSymbols.SHORT, DateFormatSymbols.NARROW
    };

    /**
     * Formats a relative date without a quantity.
     * @param direction NEXT, LAST, THIS, etc.
     * @param unit e.g SATURDAY, DAY, MONTH
     * @return the formatted string. If direction has a value that is documented as not being
     *  fully supported in every locale (for example NEXT_2 or LAST_2) then this function may
     *  return null to signal that no formatted string is available.
     * @throws IllegalArgumentException if the direction is incompatible with
     * unit this can occur with NOW which can only take PLAIN.
     * @stable ICU 53
     */
    public String format(Direction direction, AbsoluteUnit unit) {
        if (unit == AbsoluteUnit.NOW && direction != Direction.PLAIN) {
            throw new IllegalArgumentException("NOW can only accept direction PLAIN.");
        }
        String result;
        // Get plain day of week names from DateFormatSymbols.
        if ((direction == Direction.PLAIN) &&  (AbsoluteUnit.SUNDAY.ordinal() <= unit.ordinal() &&
                unit.ordinal() <= AbsoluteUnit.SATURDAY.ordinal())) {
            // Convert from AbsoluteUnit days to Calendar class indexing.
            int dateSymbolsDayOrdinal = (unit.ordinal() - AbsoluteUnit.SUNDAY.ordinal()) + Calendar.SUNDAY;
            String[] dayNames =
                    dateFormatSymbols.getWeekdays(DateFormatSymbols.STANDALONE,
                    styleToDateFormatSymbolsWidth[style.ordinal()]);
            result = dayNames[dateSymbolsDayOrdinal];
        } else {
            // Not PLAIN, or not a weekday.
            result = getAbsoluteUnitString(style, unit, direction);
        }
        return result != null ? adjustForContext(result) : null;
    }

    /**
     * Gets the string value from qualitativeUnitMap with fallback based on style.
     * @param style
     * @param unit
     * @param direction
     * @return
     */
    private String getAbsoluteUnitString(Style style, AbsoluteUnit unit, Direction direction) {
        EnumMap<AbsoluteUnit, EnumMap<Direction, String>> unitMap;
        EnumMap<Direction, String> dirMap;

        do {
            unitMap = qualitativeUnitMap.get(style);
            if (unitMap != null) {
                dirMap = unitMap.get(unit);
                if (dirMap != null) {
                    String result = dirMap.get(direction);
                    if (result != null) {
                        return result;
                    }
                }

            }

            // Consider other styles from alias fallback.
            // Data loading guaranteed no endless loops.
        } while ((style = fallbackCache[style.ordinal()]) != null);
        return null;
    }

    /**
     * Combines a relative date string and a time string in this object's
     * locale. This is done with the same date-time separator used for the
     * default calendar in this locale.
     * @param relativeDateString the relative date e.g 'yesterday'
     * @param timeString the time e.g '3:45'
     * @return the date and time concatenated according to the default
     * calendar in this locale e.g 'yesterday, 3:45'
     * @stable ICU 53
     */
    public String combineDateAndTime(String relativeDateString, String timeString) {
        return this.combinedDateAndTime.format(
            new Object[]{timeString, relativeDateString}, new StringBuffer(), null).toString();
    }

    /**
     * Returns a copy of the NumberFormat this object is using.
     * @return A copy of the NumberFormat.
     * @stable ICU 53
     */
    public NumberFormat getNumberFormat() {
        // This class is thread-safe, yet numberFormat is not. To ensure thread-safety of this
        // class we must guarantee that only one thread at a time uses our numberFormat.
        synchronized (numberFormat) {
            return (NumberFormat) numberFormat.clone();
        }
    }

    /**
     * Return capitalization context.
     *
     * @stable ICU 54
     */
    public DisplayContext getCapitalizationContext() {
        return capitalizationContext;
    }

    /**
     * Return style
     *
     * @stable ICU 54
     */
    public Style getFormatStyle() {
        return style;
    }

    private String adjustForContext(String originalFormattedString) {
        if (breakIterator == null || originalFormattedString.length() == 0 
                || !UCharacter.isLowerCase(UCharacter.codePointAt(originalFormattedString, 0))) {
            return originalFormattedString;
        }
        synchronized (breakIterator) {
            return UCharacter.toTitleCase(
                    locale,
                    originalFormattedString,
                    breakIterator,
                    UCharacter.TITLECASE_NO_LOWERCASE | UCharacter.TITLECASE_NO_BREAK_ADJUSTMENT);
        }
    }

    private RelativeDateTimeFormatter(
            EnumMap<Style, EnumMap<AbsoluteUnit, EnumMap<Direction, String>>> qualitativeUnitMap,
            EnumMap<Style, EnumMap<RelativeUnit, String[][]>> patternMap, 
            MessageFormat combinedDateAndTime,
            PluralRules pluralRules,
            NumberFormat numberFormat,
            Style style,
            DisplayContext capitalizationContext,
            BreakIterator breakIterator,
            ULocale locale) {
        this.qualitativeUnitMap = qualitativeUnitMap;
        this.patternMap = patternMap;
        this.combinedDateAndTime = combinedDateAndTime;
        this.pluralRules = pluralRules;
        this.numberFormat = numberFormat;
        this.style = style;
        if (capitalizationContext.type() != DisplayContext.Type.CAPITALIZATION) {
            throw new IllegalArgumentException(capitalizationContext.toString());
        }
        this.capitalizationContext = capitalizationContext;
        this.breakIterator = breakIterator;
        this.locale = locale;
        this.dateFormatSymbols = new DateFormatSymbols(locale);
    }

    private String getRelativeUnitPluralPattern(
            Style style, RelativeUnit unit, int pastFutureIndex, StandardPlural pluralForm) {
        if (pluralForm != StandardPlural.OTHER) {
            String formatter = getRelativeUnitPattern(style, unit, pastFutureIndex, pluralForm);
            if (formatter != null) {
                return formatter;
            }
        }
        return getRelativeUnitPattern(style, unit, pastFutureIndex, StandardPlural.OTHER);
    }

    private String getRelativeUnitPattern(
            Style style, RelativeUnit unit, int pastFutureIndex, StandardPlural pluralForm) {
        int pluralIndex = pluralForm.ordinal();
        do {
            EnumMap<RelativeUnit, String[][]> unitMap = patternMap.get(style);
            if (unitMap != null) {
                String[][] spfCompiledPatterns = unitMap.get(unit);
                if (spfCompiledPatterns != null) {
                    if (spfCompiledPatterns[pastFutureIndex][pluralIndex] != null) {
                        return spfCompiledPatterns[pastFutureIndex][pluralIndex];
                    }
                }

            }

            // Consider other styles from alias fallback.
            // Data loading guaranteed no endless loops.
        } while ((style = fallbackCache[style.ordinal()]) != null);
        return null;
    }

    private final EnumMap<Style, EnumMap<AbsoluteUnit, EnumMap<Direction, String>>> qualitativeUnitMap;
    private final EnumMap<Style, EnumMap<RelativeUnit, String[][]>> patternMap;

    private final MessageFormat combinedDateAndTime;
    private final PluralRules pluralRules;
    private final NumberFormat numberFormat;

    private final Style style;
    private final DisplayContext capitalizationContext;
    private final BreakIterator breakIterator;
    private final ULocale locale;

    private final DateFormatSymbols dateFormatSymbols;

    private static final Style fallbackCache[] = new Style[Style.INDEX_COUNT];

    private static class RelativeDateTimeFormatterData {
        public RelativeDateTimeFormatterData(
                EnumMap<Style, EnumMap<AbsoluteUnit, EnumMap<Direction, String>>> qualitativeUnitMap,
                EnumMap<Style, EnumMap<RelativeUnit, String[][]>> relUnitPatternMap,
                String dateTimePattern) {
            this.qualitativeUnitMap = qualitativeUnitMap;
            this.relUnitPatternMap = relUnitPatternMap;

            this.dateTimePattern = dateTimePattern;
        }

        public final EnumMap<Style, EnumMap<AbsoluteUnit, EnumMap<Direction, String>>> qualitativeUnitMap;
        EnumMap<Style, EnumMap<RelativeUnit, String[][]>> relUnitPatternMap;
        public final String dateTimePattern;  // Example: "{1}, {0}"
    }

    private static class Cache {
        private final ICUCache<String, RelativeDateTimeFormatterData> cache =
            new SimpleCache<String, RelativeDateTimeFormatterData>();

        public RelativeDateTimeFormatterData get(ULocale locale) {
            String key = locale.toString();
            RelativeDateTimeFormatterData result = cache.get(key);
            if (result == null) {
                result = new Loader(locale).load();
                cache.put(key, result);
            }
            return result;
        }
    }

    private static Direction keyToDirection(UResource.Key key) {
        if (key.contentEquals("-2")) {
            return Direction.LAST_2;
        }
        if (key.contentEquals("-1")) {
            return Direction.LAST;
        }
        if (key.contentEquals("0")) {
            return Direction.THIS;
        }
        if (key.contentEquals("1")) {
            return Direction.NEXT;
        }
        if (key.contentEquals("2")) {
            return Direction.NEXT_2;
        }
        return null;
    }

    /**
     * Sink for enumerating all of the relative data time formatter names.
     * Contains inner sink classes, each one corresponding to a type of resource table.
     * The outer sink handles the top-level 'fields'.
     *
     * More specific bundles (en_GB) are enumerated before their parents (en_001, en, root):
     * Only store a value if it is still missing, that is, it has not been overridden.
     *
     * C++: Each inner sink class has a reference to the main outer sink.
     * Java: Use non-static inner classes instead.
     */
    private static final class RelDateTimeFmtDataSink extends UResource.TableSink {
        // For white list of units to handle in RelativeDateTimeFormatter.
        private static enum DateTimeUnit {
            SECOND(RelativeUnit.SECONDS, null),
            MINUTE(RelativeUnit.MINUTES, null),
            HOUR(RelativeUnit.HOURS, null),
            DAY(RelativeUnit.DAYS, AbsoluteUnit.DAY),
            WEEK(RelativeUnit.WEEKS, AbsoluteUnit.WEEK),
            MONTH(RelativeUnit.MONTHS, AbsoluteUnit.MONTH),
            QUARTER(RelativeUnit.QUARTERS, AbsoluteUnit.QUARTER),
            YEAR(RelativeUnit.YEARS, AbsoluteUnit.YEAR),
            SUNDAY(null, AbsoluteUnit.SUNDAY),
            MONDAY(null, AbsoluteUnit.MONDAY),
            TUESDAY(null, AbsoluteUnit.TUESDAY),
            WEDNESDAY(null, AbsoluteUnit.WEDNESDAY),
            THURSDAY(null, AbsoluteUnit.THURSDAY),
            FRIDAY(null, AbsoluteUnit.FRIDAY),
            SATURDAY(null, AbsoluteUnit.SATURDAY);

            RelativeUnit relUnit;
            AbsoluteUnit absUnit;

            DateTimeUnit(RelativeUnit relUnit, AbsoluteUnit absUnit) {
                this.relUnit = relUnit;
                this.absUnit = absUnit;
            }

            private static final DateTimeUnit orNullFromString(CharSequence keyword) {
                // Quick check from string to enum.
                switch (keyword.length()) {
                case 3:
                    if ("day".contentEquals(keyword)) {
                        return DAY;
                    } else if ("sun".contentEquals(keyword)) {
                        return SUNDAY;
                    } else if ("mon".contentEquals(keyword)) {
                        return MONDAY;
                    } else if ("tue".contentEquals(keyword)) {
                        return TUESDAY;
                    } else if ("wed".contentEquals(keyword)) {
                        return WEDNESDAY;
                    } else if ("thu".contentEquals(keyword)) {
                        return THURSDAY;
                    }    else if ("fri".contentEquals(keyword)) {
                        return FRIDAY;
                    } else if ("sat".contentEquals(keyword)) {
                        return SATURDAY;
                    }
                    break;
                case 4:
                    if ("hour".contentEquals(keyword)) {
                        return HOUR;
                    } else if ("week".contentEquals(keyword)) {
                        return WEEK;
                    } else if ("year".contentEquals(keyword)) {
                        return YEAR;
                    }
                    break;
                case 5:
                    if ("month".contentEquals(keyword)) {
                        return MONTH;
                    }
                    break;
                case 6:
                    if ("minute".contentEquals(keyword)) {
                        return MINUTE;
                    }else if ("second".contentEquals(keyword)) {
                        return SECOND;
                    }
                    break;
                case 7:
                    if ("quarter".contentEquals(keyword)) {
                        return QUARTER;  // TODO: Check @provisional
                    }
                    break;
                default:
                    break;
                }
                return null;
            }
        }

        EnumMap<Style, EnumMap<AbsoluteUnit, EnumMap<Direction, String>>> qualitativeUnitMap =
                new EnumMap<Style, EnumMap<AbsoluteUnit, EnumMap<Direction, String>>>(Style.class);
        EnumMap<Style, EnumMap<RelativeUnit, String[][]>> styleRelUnitPatterns =
                new EnumMap<Style, EnumMap<RelativeUnit, String[][]>>(Style.class);

        private ULocale ulocale = null;

        StringBuilder sb = new StringBuilder();

        public RelDateTimeFmtDataSink(ULocale locale) {
            ulocale = locale;
        }

        // Values keep between levels of parsing the CLDR data.
        int pastFutureIndex;
        Style style;                        // {LONG, SHORT, NARROW} Derived from unit key string.
        DateTimeUnit unit;                  // From the unit key string, with the style (e.g., "-short") separated out.

        private Style styleFromKey(UResource.Key key) {
            if (key.endsWith("-short")) {
                return Style.SHORT;
            } else if (key.endsWith("-narrow")) {
                return Style.NARROW;
            } else {
                return Style.LONG;
            }
        }

        private Style styleFromAlias(UResource.Value value) {
                String s = value.getAliasString();
                if (s.endsWith("-short")) {
                    return Style.SHORT;
                } else if (s.endsWith("-narrow")) {
                    return Style.NARROW;
                } else {
                    return Style.LONG;
                }
        }

        private static int styleSuffixLength(Style style) {
            switch (style) {
            case SHORT: return 6;
            case NARROW: return 7;
            default: return 0;
            }
        }

        @Override
        public void put(UResource.Key key, UResource.Value value) {
            // Parse and store aliases.
            if (value.getType() != ICUResourceBundle.ALIAS) { return; }

            Style sourceStyle = styleFromKey(key);
            int limit = key.length() - styleSuffixLength(sourceStyle);
            DateTimeUnit unit = DateTimeUnit.orNullFromString(key.substring(0, limit));
            if (unit != null) {
                // Record the fallback chain for the values.
                // At formatting time, limit to 2 levels of fallback.
                Style targetStyle = styleFromAlias(value);
                if (sourceStyle == targetStyle) {
                    throw new ICUException("Invalid style fallback from " + sourceStyle + " to itself");
                }

                // Check for inconsistent fallbacks.
                if (fallbackCache[sourceStyle.ordinal()] == null) {
                    fallbackCache[sourceStyle.ordinal()] = targetStyle;
                } else if (fallbackCache[sourceStyle.ordinal()] != targetStyle) {
                    throw new ICUException(
                            "Inconsistent style fallback for style " + sourceStyle + " to " + targetStyle);
                }
            } 
        }

        @Override
        public UResource.TableSink getOrCreateTableSink(UResource.Key key, int initialSize) {
            // Get base unit and style from the key value.
            style = styleFromKey(key);
            int limit = key.length() - styleSuffixLength(style);
            String unitString = key.substring(0, limit);

            // Process only if unitString is in the white list.
            unit = DateTimeUnit.orNullFromString(unitString);
            if (unit == null) {
                return null;
            }
            return unitSink;  // Continue parsing this path.
        }

        // Sinks for additional levels under /fields/*/relative/ and /fields/*/relativeTime/
 
        // Sets values under relativeTime paths, e.g., "hour/relativeTime/future/one"
        class RelativeTimeDetailSink extends UResource.TableSink {
            @Override
            public void put(UResource.Key key, UResource.Value value) {
                /* Make two lists of simplePatternFmtList, one for past and one for future.
                 *  Set a SimplePatternFormatter for the <style, relative unit, plurality>
                 *
                 * Fill in values for the particular plural given, e.g., ONE, FEW, OTHER, etc.
                 */
                EnumMap<RelativeUnit, String[][]> unitPatterns  =
                        styleRelUnitPatterns.get(style);
                if (unitPatterns == null) {
                    unitPatterns = new EnumMap<RelativeUnit, String[][]>(RelativeUnit.class);
                    styleRelUnitPatterns.put(style, unitPatterns);
                }
                String[][] patterns = unitPatterns.get(unit.relUnit);
                if (patterns == null) {
                    patterns = new String[2][StandardPlural.COUNT];
                    unitPatterns.put(unit.relUnit, patterns);
                }
                int pluralIndex = StandardPlural.indexFromString(key.toString());
                if (patterns[pastFutureIndex][pluralIndex] == null) {
                    patterns[pastFutureIndex][pluralIndex] = 
                            SimplePatternFormatter.compileToStringMinMaxPlaceholders(value.getString(),
                                    sb, 0, 1); 
                }
            }
        }
        RelativeTimeDetailSink relativeTimeDetailSink = new RelativeTimeDetailSink();

        // Handles "relativeTime" entries, e.g., under "day", "hour", "minute", "minute-short", etc.
        class RelativeTimeSink extends UResource.TableSink {
            @Override
            public UResource.TableSink getOrCreateTableSink(UResource.Key key, int initialSize) {
                if (key.contentEquals("past")) {
                    pastFutureIndex = 0;
                } else if (key.contentEquals("future")) {
                    pastFutureIndex = 1;
                } else {
                    return null;
                }
                if (unit.relUnit == null) {
                    return null;
                }
                return relativeTimeDetailSink;
            }
        }
        RelativeTimeSink relativeTimeSink = new RelativeTimeSink();
 
        // Handles "relative" entries, e.g., under "day", "day-short", "fri", "fri-narrow", "fri-short", etc.
        class RelativeSink extends UResource.TableSink {
            @Override
            public void put(UResource.Key key, UResource.Value value) {

                EnumMap<AbsoluteUnit, EnumMap<Direction, String>> absMap = qualitativeUnitMap.get(style);

                if (unit.relUnit == RelativeUnit.SECONDS) {
                    if (key.contentEquals("0")) {
                        // Handle Zero seconds for "now".
                        EnumMap<Direction, String> unitStrings = absMap.get(AbsoluteUnit.NOW);
                        if (unitStrings == null) {
                            unitStrings = new EnumMap<Direction, String>(Direction.class);
                            absMap.put(AbsoluteUnit.NOW, unitStrings);
                        }
                        if (unitStrings.get(Direction.PLAIN) == null) {
                            unitStrings.put(Direction.PLAIN, value.getString());
                        }
                        return;
                    }
                }
                Direction keyDirection = keyToDirection(key);
                if (keyDirection == null) {
                    return;
                }
                AbsoluteUnit absUnit = unit.absUnit;
                if (absUnit == null) {
                    return;
                }

                if (absMap == null) {
                    absMap = new EnumMap<AbsoluteUnit, EnumMap<Direction, String>>(AbsoluteUnit.class);
                    qualitativeUnitMap.put(style, absMap);
                }
                EnumMap<Direction, String> dirMap = absMap.get(absUnit);
                if (dirMap == null) {
                    dirMap = new EnumMap<Direction, String>(Direction.class);
                    absMap.put(absUnit, dirMap);
                }
                if (dirMap.get(keyDirection) == null) {
                    // Do not override values already entered.
                    dirMap.put(keyDirection, value.getString());
                }
            }
        }
        RelativeSink relativeSink = new RelativeSink();

        // Handles entries under units, recognizing "relative" and "relativeTime" entries.
        class UnitSink extends UResource.TableSink {
            @Override
            public void put(UResource.Key key, UResource.Value value) {
                if (key.contentEquals("dn")) {
                    // Handle Display Name for PLAIN direction for some units.
                    AbsoluteUnit absUnit = unit.absUnit;
                    if (absUnit == null) {
                        return;  // Not interesting.
                    }
                    EnumMap<AbsoluteUnit, EnumMap<Direction, String>> unitMap =
                            qualitativeUnitMap.get(style);
                    if (unitMap == null) {
                        unitMap = new EnumMap<AbsoluteUnit, EnumMap<Direction, String>>(AbsoluteUnit.class);
                        qualitativeUnitMap.put(style, unitMap);
                    }
                    EnumMap<Direction,String> dirMap = unitMap.get(absUnit);
                    if (dirMap == null) {
                        dirMap = new EnumMap<Direction,String>(Direction.class);
                        unitMap.put(absUnit, dirMap);
                    }
                    if (dirMap.get(Direction.PLAIN) == null) {
                        String displayName = value.toString();
                        // TODO(Travis Keep): This is a hack to get around CLDR bug 6818.
                        if (ulocale.getLanguage().equals("en")) {
                            displayName = displayName.toLowerCase(Locale.ROOT);
                        }
                        dirMap.put(Direction.PLAIN, displayName);
                    }
                }
            }

            @Override
            public UResource.TableSink getOrCreateTableSink(UResource.Key key, int initialSize) {
                if (key.contentEquals("relative")) {
                    return relativeSink;
                } else if (key.contentEquals("relativeTime")) {
                    return relativeTimeSink;
                }
                return null;
            }
        }
        UnitSink unitSink = new UnitSink();
    }

    private static class Loader {
        private final ULocale ulocale;

        public Loader(ULocale ulocale) {
            this.ulocale = ulocale;
        }

        public RelativeDateTimeFormatterData load() {
            // Sink for traversing data.
            RelDateTimeFmtDataSink sink = new RelDateTimeFmtDataSink(ulocale);
            ICUResourceBundle r = (ICUResourceBundle)UResourceBundle.
                    getBundleInstance(ICUResourceBundle.ICU_BASE_NAME, ulocale);

            // Use sink mechanism to traverse data structure.
            r.getAllTableItemsWithFallback("fields", sink);

            // Check fallbacks array for loops or too many levels.
            for (Style testStyle : Style.values()) {
                Style newStyle1 = fallbackCache[testStyle.ordinal()];
                // Data loading guaranteed newStyle1 != testStyle.
                if (newStyle1 != null) {
                    Style newStyle2 = fallbackCache[newStyle1.ordinal()];
                    if (newStyle2 != null) {
                        // No fallback should take more than 2 steps.
                        if (fallbackCache[newStyle2.ordinal()] != null) {
                            throw new IllegalStateException("Style fallback too deep");
                        }
                    }
                }
            }

            // TODO: Replace this use of CalendarData.
            CalendarData calData = new CalendarData(
                    ulocale, r.getStringWithFallback("calendar/default"));

            return new RelativeDateTimeFormatterData(
                    sink.qualitativeUnitMap, sink.styleRelUnitPatterns,
                    calData.getDateTimePattern());
        }
    }

    private static final Cache cache = new Cache();
}
