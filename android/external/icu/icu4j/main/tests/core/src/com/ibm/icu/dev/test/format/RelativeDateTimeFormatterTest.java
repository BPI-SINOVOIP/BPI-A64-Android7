/*
 *******************************************************************************
 * Copyright (C) 2013-2016, International Business Machines Corporation and
 * others. All Rights Reserved.
 *******************************************************************************
 */
package com.ibm.icu.dev.test.format;

import java.util.Locale;

import com.ibm.icu.dev.test.TestFmwk;
import com.ibm.icu.text.DisplayContext;
import com.ibm.icu.text.NumberFormat;
import com.ibm.icu.text.RelativeDateTimeFormatter;
import com.ibm.icu.text.RelativeDateTimeFormatter.AbsoluteUnit;
import com.ibm.icu.text.RelativeDateTimeFormatter.Direction;
import com.ibm.icu.text.RelativeDateTimeFormatter.RelativeUnit;
import com.ibm.icu.util.ULocale;

public class RelativeDateTimeFormatterTest extends TestFmwk {
    
    public static void main(String[] args) throws Exception {
        new RelativeDateTimeFormatterTest().run(args);
    }
    
    public void TestRelativeDateWithQuantity() {
        Object[][] data = {
                {0.0, Direction.NEXT, RelativeUnit.SECONDS, "in 0 seconds"},
                {0.5, Direction.NEXT, RelativeUnit.SECONDS, "in 0.5 seconds"},
                {1.0, Direction.NEXT, RelativeUnit.SECONDS, "in 1 second"},
                {2.0, Direction.NEXT, RelativeUnit.SECONDS, "in 2 seconds"},
                {0.0, Direction.NEXT, RelativeUnit.MINUTES, "in 0 minutes"},
                {0.5, Direction.NEXT, RelativeUnit.MINUTES, "in 0.5 minutes"},
                {1.0, Direction.NEXT, RelativeUnit.MINUTES, "in 1 minute"},
                {2.0, Direction.NEXT, RelativeUnit.MINUTES, "in 2 minutes"},
                {0.0, Direction.NEXT, RelativeUnit.HOURS, "in 0 hours"},
                {0.5, Direction.NEXT, RelativeUnit.HOURS, "in 0.5 hours"},
                {1.0, Direction.NEXT, RelativeUnit.HOURS, "in 1 hour"},
                {2.0, Direction.NEXT, RelativeUnit.HOURS, "in 2 hours"},
                {0.0, Direction.NEXT, RelativeUnit.DAYS, "in 0 days"},
                {0.5, Direction.NEXT, RelativeUnit.DAYS, "in 0.5 days"},
                {1.0, Direction.NEXT, RelativeUnit.DAYS, "in 1 day"},
                {2.0, Direction.NEXT, RelativeUnit.DAYS, "in 2 days"},
                {0.0, Direction.NEXT, RelativeUnit.WEEKS, "in 0 weeks"},
                {0.5, Direction.NEXT, RelativeUnit.WEEKS, "in 0.5 weeks"},
                {1.0, Direction.NEXT, RelativeUnit.WEEKS, "in 1 week"},
                {2.0, Direction.NEXT, RelativeUnit.WEEKS, "in 2 weeks"},
                {0.0, Direction.NEXT, RelativeUnit.MONTHS, "in 0 months"},
                {0.5, Direction.NEXT, RelativeUnit.MONTHS, "in 0.5 months"},
                {1.0, Direction.NEXT, RelativeUnit.MONTHS, "in 1 month"},
                {2.0, Direction.NEXT, RelativeUnit.MONTHS, "in 2 months"},
                {0.0, Direction.NEXT, RelativeUnit.YEARS, "in 0 years"},
                {0.5, Direction.NEXT, RelativeUnit.YEARS, "in 0.5 years"},
                {1.0, Direction.NEXT, RelativeUnit.YEARS, "in 1 year"},
                {2.0, Direction.NEXT, RelativeUnit.YEARS, "in 2 years"},
                
                {0.0, Direction.LAST, RelativeUnit.SECONDS, "0 seconds ago"},
                {0.5, Direction.LAST, RelativeUnit.SECONDS, "0.5 seconds ago"},
                {1.0, Direction.LAST, RelativeUnit.SECONDS, "1 second ago"},
                {2.0, Direction.LAST, RelativeUnit.SECONDS, "2 seconds ago"},
                {0.0, Direction.LAST, RelativeUnit.MINUTES, "0 minutes ago"},
                {0.5, Direction.LAST, RelativeUnit.MINUTES, "0.5 minutes ago"},
                {1.0, Direction.LAST, RelativeUnit.MINUTES, "1 minute ago"},
                {2.0, Direction.LAST, RelativeUnit.MINUTES, "2 minutes ago"},
                {0.0, Direction.LAST, RelativeUnit.HOURS, "0 hours ago"},
                {0.5, Direction.LAST, RelativeUnit.HOURS, "0.5 hours ago"},
                {1.0, Direction.LAST, RelativeUnit.HOURS, "1 hour ago"},
                {2.0, Direction.LAST, RelativeUnit.HOURS, "2 hours ago"},
                {0.0, Direction.LAST, RelativeUnit.DAYS, "0 days ago"},
                {0.5, Direction.LAST, RelativeUnit.DAYS, "0.5 days ago"},
                {1.0, Direction.LAST, RelativeUnit.DAYS, "1 day ago"},
                {2.0, Direction.LAST, RelativeUnit.DAYS, "2 days ago"},
                {0.0, Direction.LAST, RelativeUnit.WEEKS, "0 weeks ago"},
                {0.5, Direction.LAST, RelativeUnit.WEEKS, "0.5 weeks ago"},
                {1.0, Direction.LAST, RelativeUnit.WEEKS, "1 week ago"},
                {2.0, Direction.LAST, RelativeUnit.WEEKS, "2 weeks ago"},
                {0.0, Direction.LAST, RelativeUnit.MONTHS, "0 months ago"},
                {0.5, Direction.LAST, RelativeUnit.MONTHS, "0.5 months ago"},
                {1.0, Direction.LAST, RelativeUnit.MONTHS, "1 month ago"},
                {2.0, Direction.LAST, RelativeUnit.MONTHS, "2 months ago"},
                {0.0, Direction.LAST, RelativeUnit.YEARS, "0 years ago"},
                {0.5, Direction.LAST, RelativeUnit.YEARS, "0.5 years ago"},
                {1.0, Direction.LAST, RelativeUnit.YEARS, "1 year ago"},
                {2.0, Direction.LAST, RelativeUnit.YEARS, "2 years ago"},      
        };
        RelativeDateTimeFormatter fmt = RelativeDateTimeFormatter.getInstance(new ULocale("en_US"));
        for (Object[] row : data) {
            String actual = fmt.format(
                    ((Double) row[0]).doubleValue(), (Direction) row[1], (RelativeUnit) row[2]);
            assertEquals("Relative date with quantity", row[3], actual);
        }
    }
    
    public void TestRelativeDateWithQuantityCaps() {
        Object[][] data = {
                {0.0, Direction.NEXT, RelativeUnit.SECONDS, "In 0 seconds"},
                {0.5, Direction.NEXT, RelativeUnit.SECONDS, "In 0.5 seconds"},
                
                {1.0, Direction.NEXT, RelativeUnit.SECONDS, "In 1 second"},
                {2.0, Direction.NEXT, RelativeUnit.SECONDS, "In 2 seconds"},
                {0.0, Direction.NEXT, RelativeUnit.MINUTES, "In 0 minutes"},
                {0.5, Direction.NEXT, RelativeUnit.MINUTES, "In 0.5 minutes"},
                {1.0, Direction.NEXT, RelativeUnit.MINUTES, "In 1 minute"},
                {2.0, Direction.NEXT, RelativeUnit.MINUTES, "In 2 minutes"},
                {0.0, Direction.NEXT, RelativeUnit.HOURS, "In 0 hours"},
                {0.5, Direction.NEXT, RelativeUnit.HOURS, "In 0.5 hours"},
                {1.0, Direction.NEXT, RelativeUnit.HOURS, "In 1 hour"},
                {2.0, Direction.NEXT, RelativeUnit.HOURS, "In 2 hours"},
                {0.0, Direction.NEXT, RelativeUnit.DAYS, "In 0 days"},
                {0.5, Direction.NEXT, RelativeUnit.DAYS, "In 0.5 days"},
                {1.0, Direction.NEXT, RelativeUnit.DAYS, "In 1 day"},
                {2.0, Direction.NEXT, RelativeUnit.DAYS, "In 2 days"},
                {0.0, Direction.NEXT, RelativeUnit.WEEKS, "In 0 weeks"},
                {0.5, Direction.NEXT, RelativeUnit.WEEKS, "In 0.5 weeks"},
                {1.0, Direction.NEXT, RelativeUnit.WEEKS, "In 1 week"},
                {2.0, Direction.NEXT, RelativeUnit.WEEKS, "In 2 weeks"},
                {0.0, Direction.NEXT, RelativeUnit.MONTHS, "In 0 months"},
                {0.5, Direction.NEXT, RelativeUnit.MONTHS, "In 0.5 months"},
                {1.0, Direction.NEXT, RelativeUnit.MONTHS, "In 1 month"},
                {2.0, Direction.NEXT, RelativeUnit.MONTHS, "In 2 months"},
                {0.0, Direction.NEXT, RelativeUnit.YEARS, "In 0 years"},
                {0.5, Direction.NEXT, RelativeUnit.YEARS, "In 0.5 years"},
                {1.0, Direction.NEXT, RelativeUnit.YEARS, "In 1 year"},
                {2.0, Direction.NEXT, RelativeUnit.YEARS, "In 2 years"},
                
                {0.0, Direction.LAST, RelativeUnit.SECONDS, "0 seconds ago"},
                {0.5, Direction.LAST, RelativeUnit.SECONDS, "0.5 seconds ago"},
                {1.0, Direction.LAST, RelativeUnit.SECONDS, "1 second ago"},
                {2.0, Direction.LAST, RelativeUnit.SECONDS, "2 seconds ago"},
                {0.0, Direction.LAST, RelativeUnit.MINUTES, "0 minutes ago"},
                {0.5, Direction.LAST, RelativeUnit.MINUTES, "0.5 minutes ago"},
                {1.0, Direction.LAST, RelativeUnit.MINUTES, "1 minute ago"},
                {2.0, Direction.LAST, RelativeUnit.MINUTES, "2 minutes ago"},
                {0.0, Direction.LAST, RelativeUnit.HOURS, "0 hours ago"},
                {0.5, Direction.LAST, RelativeUnit.HOURS, "0.5 hours ago"},
                {1.0, Direction.LAST, RelativeUnit.HOURS, "1 hour ago"},
                {2.0, Direction.LAST, RelativeUnit.HOURS, "2 hours ago"},
                {0.0, Direction.LAST, RelativeUnit.DAYS, "0 days ago"},
                {0.5, Direction.LAST, RelativeUnit.DAYS, "0.5 days ago"},
                {1.0, Direction.LAST, RelativeUnit.DAYS, "1 day ago"},
                {2.0, Direction.LAST, RelativeUnit.DAYS, "2 days ago"},
                {0.0, Direction.LAST, RelativeUnit.WEEKS, "0 weeks ago"},
                {0.5, Direction.LAST, RelativeUnit.WEEKS, "0.5 weeks ago"},
                {1.0, Direction.LAST, RelativeUnit.WEEKS, "1 week ago"},
                {2.0, Direction.LAST, RelativeUnit.WEEKS, "2 weeks ago"},
                {0.0, Direction.LAST, RelativeUnit.MONTHS, "0 months ago"},
                {0.5, Direction.LAST, RelativeUnit.MONTHS, "0.5 months ago"},
                {1.0, Direction.LAST, RelativeUnit.MONTHS, "1 month ago"},
                {2.0, Direction.LAST, RelativeUnit.MONTHS, "2 months ago"},
                {0.0, Direction.LAST, RelativeUnit.YEARS, "0 years ago"},
                {0.5, Direction.LAST, RelativeUnit.YEARS, "0.5 years ago"},
                {1.0, Direction.LAST, RelativeUnit.YEARS, "1 year ago"},
                {2.0, Direction.LAST, RelativeUnit.YEARS, "2 years ago"}, 
                  
        };
        RelativeDateTimeFormatter fmt = RelativeDateTimeFormatter.getInstance(
                new ULocale("en_US"),
                null,
                RelativeDateTimeFormatter.Style.LONG,
                DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE);
        for (Object[] row : data) {
            String actual = fmt.format(
                    ((Double) row[0]).doubleValue(), (Direction) row[1], (RelativeUnit) row[2]);
            assertEquals("Relative date with quantity", row[3], actual);
        }
    }
    
    public void TestRelativeDateWithQuantityShort() {
        Object[][] data = {
                {0.0, Direction.NEXT, RelativeUnit.SECONDS, "in 0 sec."},
                {0.5, Direction.NEXT, RelativeUnit.SECONDS, "in 0.5 sec."},
            
                {1.0, Direction.NEXT, RelativeUnit.SECONDS, "in 1 sec."},
                {2.0, Direction.NEXT, RelativeUnit.SECONDS, "in 2 sec."},
                {0.0, Direction.NEXT, RelativeUnit.MINUTES, "in 0 min."},
                {0.5, Direction.NEXT, RelativeUnit.MINUTES, "in 0.5 min."},
                {1.0, Direction.NEXT, RelativeUnit.MINUTES, "in 1 min."},
                {2.0, Direction.NEXT, RelativeUnit.MINUTES, "in 2 min."},
                {0.0, Direction.NEXT, RelativeUnit.HOURS, "in 0 hr."},
                {0.5, Direction.NEXT, RelativeUnit.HOURS, "in 0.5 hr."},
                {1.0, Direction.NEXT, RelativeUnit.HOURS, "in 1 hr."},
                {2.0, Direction.NEXT, RelativeUnit.HOURS, "in 2 hr."},
                {0.0, Direction.NEXT, RelativeUnit.DAYS, "in 0 days"},
                {0.5, Direction.NEXT, RelativeUnit.DAYS, "in 0.5 days"},
                {1.0, Direction.NEXT, RelativeUnit.DAYS, "in 1 day"},
                {2.0, Direction.NEXT, RelativeUnit.DAYS, "in 2 days"},
                {0.0, Direction.NEXT, RelativeUnit.WEEKS, "in 0 wk."},
                {0.5, Direction.NEXT, RelativeUnit.WEEKS, "in 0.5 wk."},
                {1.0, Direction.NEXT, RelativeUnit.WEEKS, "in 1 wk."},
                {2.0, Direction.NEXT, RelativeUnit.WEEKS, "in 2 wk."},
                {0.0, Direction.NEXT, RelativeUnit.MONTHS, "in 0 mo."},
                {0.5, Direction.NEXT, RelativeUnit.MONTHS, "in 0.5 mo."},
                {1.0, Direction.NEXT, RelativeUnit.MONTHS, "in 1 mo."},
                {2.0, Direction.NEXT, RelativeUnit.MONTHS, "in 2 mo."},
                {0.0, Direction.NEXT, RelativeUnit.YEARS, "in 0 yr."},
                {0.5, Direction.NEXT, RelativeUnit.YEARS, "in 0.5 yr."},
                {1.0, Direction.NEXT, RelativeUnit.YEARS, "in 1 yr."},
                {2.0, Direction.NEXT, RelativeUnit.YEARS, "in 2 yr."},
                
                {0.0, Direction.LAST, RelativeUnit.SECONDS, "0 sec. ago"},
                {0.5, Direction.LAST, RelativeUnit.SECONDS, "0.5 sec. ago"},
                {1.0, Direction.LAST, RelativeUnit.SECONDS, "1 sec. ago"},
                {2.0, Direction.LAST, RelativeUnit.SECONDS, "2 sec. ago"},
                {0.0, Direction.LAST, RelativeUnit.MINUTES, "0 min. ago"},
                {0.5, Direction.LAST, RelativeUnit.MINUTES, "0.5 min. ago"},
                {1.0, Direction.LAST, RelativeUnit.MINUTES, "1 min. ago"},
                {2.0, Direction.LAST, RelativeUnit.MINUTES, "2 min. ago"},
                {0.0, Direction.LAST, RelativeUnit.HOURS, "0 hr. ago"},
                {0.5, Direction.LAST, RelativeUnit.HOURS, "0.5 hr. ago"},
                {1.0, Direction.LAST, RelativeUnit.HOURS, "1 hr. ago"},
                {2.0, Direction.LAST, RelativeUnit.HOURS, "2 hr. ago"},
                {0.0, Direction.LAST, RelativeUnit.DAYS, "0 days ago"},
                {0.5, Direction.LAST, RelativeUnit.DAYS, "0.5 days ago"},
                {1.0, Direction.LAST, RelativeUnit.DAYS, "1 day ago"},
                {2.0, Direction.LAST, RelativeUnit.DAYS, "2 days ago"},
                {0.0, Direction.LAST, RelativeUnit.WEEKS, "0 wk. ago"},
                {0.5, Direction.LAST, RelativeUnit.WEEKS, "0.5 wk. ago"},
                {1.0, Direction.LAST, RelativeUnit.WEEKS, "1 wk. ago"},
                {2.0, Direction.LAST, RelativeUnit.WEEKS, "2 wk. ago"},
                {0.0, Direction.LAST, RelativeUnit.MONTHS, "0 mo. ago"},
                {0.5, Direction.LAST, RelativeUnit.MONTHS, "0.5 mo. ago"},
                {1.0, Direction.LAST, RelativeUnit.MONTHS, "1 mo. ago"},
                {2.0, Direction.LAST, RelativeUnit.MONTHS, "2 mo. ago"},
                {0.0, Direction.LAST, RelativeUnit.YEARS, "0 yr. ago"},
                {0.5, Direction.LAST, RelativeUnit.YEARS, "0.5 yr. ago"},
                {1.0, Direction.LAST, RelativeUnit.YEARS, "1 yr. ago"},
                {2.0, Direction.LAST, RelativeUnit.YEARS, "2 yr. ago"}, 
                    
        };
        RelativeDateTimeFormatter fmt = RelativeDateTimeFormatter.getInstance(
                new ULocale("en_US"),
                null,
                RelativeDateTimeFormatter.Style.SHORT,
                DisplayContext.CAPITALIZATION_NONE);
        for (Object[] row : data) {
            String actual = fmt.format(
                    ((Double) row[0]).doubleValue(), (Direction) row[1], (RelativeUnit) row[2]);
            assertEquals("Relative date with quantity", row[3], actual);
        }
    }
    
    public void TestRelativeDateWithQuantityNarrow() {
        Object[][] data = {
                {0.0, Direction.NEXT, RelativeUnit.SECONDS, "in 0 sec."},
                {0.5, Direction.NEXT, RelativeUnit.SECONDS, "in 0.5 sec."},
             
                {1.0, Direction.NEXT, RelativeUnit.SECONDS, "in 1 sec."},
                {2.0, Direction.NEXT, RelativeUnit.SECONDS, "in 2 sec."},
                {0.0, Direction.NEXT, RelativeUnit.MINUTES, "in 0 min."},
                {0.5, Direction.NEXT, RelativeUnit.MINUTES, "in 0.5 min."},
                {1.0, Direction.NEXT, RelativeUnit.MINUTES, "in 1 min."},
                {2.0, Direction.NEXT, RelativeUnit.MINUTES, "in 2 min."},
                {0.0, Direction.NEXT, RelativeUnit.HOURS, "in 0 hr."},
                {0.5, Direction.NEXT, RelativeUnit.HOURS, "in 0.5 hr."},
                {1.0, Direction.NEXT, RelativeUnit.HOURS, "in 1 hr."},
                {2.0, Direction.NEXT, RelativeUnit.HOURS, "in 2 hr."},
                {0.0, Direction.NEXT, RelativeUnit.DAYS, "in 0 days"},
                {0.5, Direction.NEXT, RelativeUnit.DAYS, "in 0.5 days"},
                {1.0, Direction.NEXT, RelativeUnit.DAYS, "in 1 day"},
                {2.0, Direction.NEXT, RelativeUnit.DAYS, "in 2 days"},
                {0.0, Direction.NEXT, RelativeUnit.WEEKS, "in 0 wk."},
                {0.5, Direction.NEXT, RelativeUnit.WEEKS, "in 0.5 wk."},
                {1.0, Direction.NEXT, RelativeUnit.WEEKS, "in 1 wk."},
                {2.0, Direction.NEXT, RelativeUnit.WEEKS, "in 2 wk."},
                {0.0, Direction.NEXT, RelativeUnit.MONTHS, "in 0 mo."},
                {0.5, Direction.NEXT, RelativeUnit.MONTHS, "in 0.5 mo."},
                {1.0, Direction.NEXT, RelativeUnit.MONTHS, "in 1 mo."},
                {2.0, Direction.NEXT, RelativeUnit.MONTHS, "in 2 mo."},
                {0.0, Direction.NEXT, RelativeUnit.YEARS, "in 0 yr."},
                {0.5, Direction.NEXT, RelativeUnit.YEARS, "in 0.5 yr."},
                {1.0, Direction.NEXT, RelativeUnit.YEARS, "in 1 yr."},
                {2.0, Direction.NEXT, RelativeUnit.YEARS, "in 2 yr."},
                
                {0.0, Direction.LAST, RelativeUnit.SECONDS, "0 sec. ago"},
                {0.5, Direction.LAST, RelativeUnit.SECONDS, "0.5 sec. ago"},
                {1.0, Direction.LAST, RelativeUnit.SECONDS, "1 sec. ago"},
                {2.0, Direction.LAST, RelativeUnit.SECONDS, "2 sec. ago"},
                {0.0, Direction.LAST, RelativeUnit.MINUTES, "0 min. ago"},
                {0.5, Direction.LAST, RelativeUnit.MINUTES, "0.5 min. ago"},
                {1.0, Direction.LAST, RelativeUnit.MINUTES, "1 min. ago"},
                {2.0, Direction.LAST, RelativeUnit.MINUTES, "2 min. ago"},
                {0.0, Direction.LAST, RelativeUnit.HOURS, "0 hr. ago"},
                {0.5, Direction.LAST, RelativeUnit.HOURS, "0.5 hr. ago"},
                {1.0, Direction.LAST, RelativeUnit.HOURS, "1 hr. ago"},
                {2.0, Direction.LAST, RelativeUnit.HOURS, "2 hr. ago"},
                {0.0, Direction.LAST, RelativeUnit.DAYS, "0 days ago"},
                {0.5, Direction.LAST, RelativeUnit.DAYS, "0.5 days ago"},
                {1.0, Direction.LAST, RelativeUnit.DAYS, "1 day ago"},
                {2.0, Direction.LAST, RelativeUnit.DAYS, "2 days ago"},
                {0.0, Direction.LAST, RelativeUnit.WEEKS, "0 wk. ago"},
                {0.5, Direction.LAST, RelativeUnit.WEEKS, "0.5 wk. ago"},
                {1.0, Direction.LAST, RelativeUnit.WEEKS, "1 wk. ago"},
                {2.0, Direction.LAST, RelativeUnit.WEEKS, "2 wk. ago"},
                {0.0, Direction.LAST, RelativeUnit.MONTHS, "0 mo. ago"},
                {0.5, Direction.LAST, RelativeUnit.MONTHS, "0.5 mo. ago"},
                {1.0, Direction.LAST, RelativeUnit.MONTHS, "1 mo. ago"},
                {2.0, Direction.LAST, RelativeUnit.MONTHS, "2 mo. ago"},
                {0.0, Direction.LAST, RelativeUnit.YEARS, "0 yr. ago"},
                {0.5, Direction.LAST, RelativeUnit.YEARS, "0.5 yr. ago"},
                {1.0, Direction.LAST, RelativeUnit.YEARS, "1 yr. ago"},
                {2.0, Direction.LAST, RelativeUnit.YEARS, "2 yr. ago"}, 
                   
        };
        RelativeDateTimeFormatter fmt = RelativeDateTimeFormatter.getInstance(
                new ULocale("en_US"),
                null,
                RelativeDateTimeFormatter.Style.NARROW,
                DisplayContext.CAPITALIZATION_NONE);
        for (Object[] row : data) {
            String actual = fmt.format(
                    ((Double) row[0]).doubleValue(), (Direction) row[1], (RelativeUnit) row[2]);
            assertEquals("Relative date with quantity", row[3], actual);
        }
    }
    
    
    
    public void TestRelativeDateWithQuantitySr() {
        Object[][] data = {
                {0.0, Direction.NEXT, RelativeUnit.MONTHS, "\u0437\u0430 0 \u043C\u0435\u0441\u0435\u0446\u0438"},
                {1.2, Direction.NEXT, RelativeUnit.MONTHS, "\u0437\u0430 1,2 \u043C\u0435\u0441\u0435\u0446\u0430"},
                {21.0, Direction.NEXT, RelativeUnit.MONTHS, "\u0437\u0430 21 \u043C\u0435\u0441\u0435\u0446"},      
        };
        RelativeDateTimeFormatter fmt = RelativeDateTimeFormatter.getInstance(new ULocale("sr"));
        for (Object[] row : data) {
            String actual = fmt.format(
                    ((Double) row[0]).doubleValue(), (Direction) row[1], (RelativeUnit) row[2]);
            assertEquals("Relative date with quantity", row[3], actual);
        }
    }
    
    public void TestRelativeDateWithQuantitySrFallback() {
        Object[][] data = {
                {0.0, Direction.NEXT, RelativeUnit.MONTHS, "\u0437\u0430 0 \u043C."},
                {1.2, Direction.NEXT, RelativeUnit.MONTHS, "\u0437\u0430 1,2 \u043C."},
                {21.0, Direction.NEXT, RelativeUnit.MONTHS, "\u0437\u0430 21 \u043C."},      
        };
        RelativeDateTimeFormatter fmt = RelativeDateTimeFormatter.getInstance(
                new ULocale("sr"),
                null,
                RelativeDateTimeFormatter.Style.NARROW,
                DisplayContext.CAPITALIZATION_NONE);
        for (Object[] row : data) {
            String actual = fmt.format(
                    ((Double) row[0]).doubleValue(), (Direction) row[1], (RelativeUnit) row[2]);
            assertEquals("Relative date with quantity fallback", row[3], actual);
        }
    }
    
    public void TestRelativeDateWithoutQuantity() {
        Object[][] data = {
                {Direction.NEXT_2, AbsoluteUnit.DAY, null},
                
                {Direction.NEXT, AbsoluteUnit.DAY, "tomorrow"},
                {Direction.NEXT, AbsoluteUnit.WEEK, "next week"},
                {Direction.NEXT, AbsoluteUnit.MONTH, "next month"},
                {Direction.NEXT, AbsoluteUnit.YEAR, "next year"},
                {Direction.NEXT, AbsoluteUnit.MONDAY, "next Monday"},
                {Direction.NEXT, AbsoluteUnit.TUESDAY, "next Tuesday"},
                {Direction.NEXT, AbsoluteUnit.WEDNESDAY, "next Wednesday"},
                {Direction.NEXT, AbsoluteUnit.THURSDAY, "next Thursday"},
                {Direction.NEXT, AbsoluteUnit.FRIDAY, "next Friday"},
                {Direction.NEXT, AbsoluteUnit.SATURDAY, "next Saturday"},
                {Direction.NEXT, AbsoluteUnit.SUNDAY, "next Sunday"},
                
                {Direction.LAST_2, AbsoluteUnit.DAY, null},
                
                {Direction.LAST, AbsoluteUnit.DAY, "yesterday"},
                {Direction.LAST, AbsoluteUnit.WEEK, "last week"},
                {Direction.LAST, AbsoluteUnit.MONTH, "last month"},
                {Direction.LAST, AbsoluteUnit.YEAR, "last year"},
                {Direction.LAST, AbsoluteUnit.MONDAY, "last Monday"},
                {Direction.LAST, AbsoluteUnit.TUESDAY, "last Tuesday"},
                {Direction.LAST, AbsoluteUnit.WEDNESDAY, "last Wednesday"},
                {Direction.LAST, AbsoluteUnit.THURSDAY, "last Thursday"},
                {Direction.LAST, AbsoluteUnit.FRIDAY, "last Friday"},
                {Direction.LAST, AbsoluteUnit.SATURDAY, "last Saturday"},
                {Direction.LAST, AbsoluteUnit.SUNDAY, "last Sunday"},
                 
                {Direction.THIS, AbsoluteUnit.DAY, "today"},
                {Direction.THIS, AbsoluteUnit.WEEK, "this week"},
                {Direction.THIS, AbsoluteUnit.MONTH, "this month"},
                {Direction.THIS, AbsoluteUnit.YEAR, "this year"},
                {Direction.THIS, AbsoluteUnit.MONDAY, "this Monday"},
                {Direction.THIS, AbsoluteUnit.TUESDAY, "this Tuesday"},
                {Direction.THIS, AbsoluteUnit.WEDNESDAY, "this Wednesday"},
                {Direction.THIS, AbsoluteUnit.THURSDAY, "this Thursday"},
                {Direction.THIS, AbsoluteUnit.FRIDAY, "this Friday"},
                {Direction.THIS, AbsoluteUnit.SATURDAY, "this Saturday"},
                {Direction.THIS, AbsoluteUnit.SUNDAY, "this Sunday"},
                
                {Direction.PLAIN, AbsoluteUnit.DAY, "day"},
                {Direction.PLAIN, AbsoluteUnit.WEEK, "week"},
                {Direction.PLAIN, AbsoluteUnit.MONTH, "month"},
                {Direction.PLAIN, AbsoluteUnit.YEAR, "year"},
                {Direction.PLAIN, AbsoluteUnit.MONDAY, "Monday"},
                {Direction.PLAIN, AbsoluteUnit.TUESDAY, "Tuesday"},
                {Direction.PLAIN, AbsoluteUnit.WEDNESDAY, "Wednesday"},
                {Direction.PLAIN, AbsoluteUnit.THURSDAY, "Thursday"},
                {Direction.PLAIN, AbsoluteUnit.FRIDAY, "Friday"},
                {Direction.PLAIN, AbsoluteUnit.SATURDAY, "Saturday"},
                {Direction.PLAIN, AbsoluteUnit.SUNDAY, "Sunday"},
                
                {Direction.PLAIN, AbsoluteUnit.NOW, "now"},
        };
        RelativeDateTimeFormatter fmt = RelativeDateTimeFormatter.getInstance(new ULocale("en_US"));
        for (Object[] row : data) {
            String actual = fmt.format((Direction) row[0], (AbsoluteUnit) row[1]);
            assertEquals("Relative date without quantity", row[2], actual);
        }
    }
    
    public void TestRelativeDateWithoutQuantityCaps() {
        Object[][] data = {
                {Direction.NEXT_2, AbsoluteUnit.DAY, null},
                
                {Direction.NEXT, AbsoluteUnit.DAY, "Tomorrow"},
                {Direction.NEXT, AbsoluteUnit.WEEK, "Next week"},               
                {Direction.NEXT, AbsoluteUnit.MONTH, "Next month"},
                {Direction.NEXT, AbsoluteUnit.YEAR, "Next year"},
                
                {Direction.NEXT, AbsoluteUnit.MONDAY, "Next Monday"},
                {Direction.NEXT, AbsoluteUnit.TUESDAY, "Next Tuesday"},
                {Direction.NEXT, AbsoluteUnit.WEDNESDAY, "Next Wednesday"},
                {Direction.NEXT, AbsoluteUnit.THURSDAY, "Next Thursday"},
                {Direction.NEXT, AbsoluteUnit.FRIDAY, "Next Friday"},
                {Direction.NEXT, AbsoluteUnit.SATURDAY, "Next Saturday"},
                {Direction.NEXT, AbsoluteUnit.SUNDAY, "Next Sunday"},
                
                {Direction.LAST_2, AbsoluteUnit.DAY, null},
                
                {Direction.LAST, AbsoluteUnit.DAY, "Yesterday"},
                {Direction.LAST, AbsoluteUnit.WEEK, "Last week"},
                {Direction.LAST, AbsoluteUnit.MONTH, "Last month"},
                {Direction.LAST, AbsoluteUnit.YEAR, "Last year"},
                {Direction.LAST, AbsoluteUnit.MONDAY, "Last Monday"},
                {Direction.LAST, AbsoluteUnit.TUESDAY, "Last Tuesday"},
                {Direction.LAST, AbsoluteUnit.WEDNESDAY, "Last Wednesday"},
                {Direction.LAST, AbsoluteUnit.THURSDAY, "Last Thursday"},
                {Direction.LAST, AbsoluteUnit.FRIDAY, "Last Friday"},
                {Direction.LAST, AbsoluteUnit.SATURDAY, "Last Saturday"},
                {Direction.LAST, AbsoluteUnit.SUNDAY, "Last Sunday"},
                 
                {Direction.THIS, AbsoluteUnit.DAY, "Today"},
                {Direction.THIS, AbsoluteUnit.WEEK, "This week"},
                {Direction.THIS, AbsoluteUnit.MONTH, "This month"},
                {Direction.THIS, AbsoluteUnit.YEAR, "This year"},
                {Direction.THIS, AbsoluteUnit.MONDAY, "This Monday"},
                {Direction.THIS, AbsoluteUnit.TUESDAY, "This Tuesday"},
                {Direction.THIS, AbsoluteUnit.WEDNESDAY, "This Wednesday"},
                {Direction.THIS, AbsoluteUnit.THURSDAY, "This Thursday"},
                {Direction.THIS, AbsoluteUnit.FRIDAY, "This Friday"},
                {Direction.THIS, AbsoluteUnit.SATURDAY, "This Saturday"},
                {Direction.THIS, AbsoluteUnit.SUNDAY, "This Sunday"},
                
                {Direction.PLAIN, AbsoluteUnit.DAY, "Day"},
                {Direction.PLAIN, AbsoluteUnit.WEEK, "Week"},
                {Direction.PLAIN, AbsoluteUnit.MONTH, "Month"},
                {Direction.PLAIN, AbsoluteUnit.YEAR, "Year"},
                {Direction.PLAIN, AbsoluteUnit.MONDAY, "Monday"},
                {Direction.PLAIN, AbsoluteUnit.TUESDAY, "Tuesday"},
                {Direction.PLAIN, AbsoluteUnit.WEDNESDAY, "Wednesday"},
                {Direction.PLAIN, AbsoluteUnit.THURSDAY, "Thursday"},
                {Direction.PLAIN, AbsoluteUnit.FRIDAY, "Friday"},
                {Direction.PLAIN, AbsoluteUnit.SATURDAY, "Saturday"},
                {Direction.PLAIN, AbsoluteUnit.SUNDAY, "Sunday"},
                
                {Direction.PLAIN, AbsoluteUnit.NOW, "Now"},
               
        };
        RelativeDateTimeFormatter fmt = RelativeDateTimeFormatter.getInstance(
                new ULocale("en_US"),
                null,
                RelativeDateTimeFormatter.Style.LONG,
                DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE);
        for (Object[] row : data) {
            String actual = fmt.format((Direction) row[0], (AbsoluteUnit) row[1]);
            assertEquals("Relative date without quantity caps", row[2], actual);
        }
    }
    
    public void TestRelativeDateWithoutQuantityShort() {
        Object[][] data = {
                {Direction.NEXT_2, AbsoluteUnit.DAY, null},
                
                {Direction.NEXT, AbsoluteUnit.DAY, "tomorrow"},
                {Direction.NEXT, AbsoluteUnit.WEEK, "next wk."},
               
                {Direction.NEXT, AbsoluteUnit.MONTH, "next mo."},
                {Direction.NEXT, AbsoluteUnit.YEAR, "next yr."},
               
                {Direction.NEXT, AbsoluteUnit.MONDAY, "next Mon."},
                
                {Direction.NEXT, AbsoluteUnit.TUESDAY, "next Tue."},
                {Direction.NEXT, AbsoluteUnit.WEDNESDAY, "next Wed."},
                {Direction.NEXT, AbsoluteUnit.THURSDAY, "next Thu."},
                {Direction.NEXT, AbsoluteUnit.FRIDAY, "next Fri."},
                {Direction.NEXT, AbsoluteUnit.SATURDAY, "next Sat."},
                {Direction.NEXT, AbsoluteUnit.SUNDAY, "next Sun."},
                
                {Direction.LAST_2, AbsoluteUnit.DAY, null},
                
                {Direction.LAST, AbsoluteUnit.DAY, "yesterday"},
                {Direction.LAST, AbsoluteUnit.WEEK, "last wk."},
                {Direction.LAST, AbsoluteUnit.MONTH, "last mo."},
                {Direction.LAST, AbsoluteUnit.YEAR, "last yr."},
                {Direction.LAST, AbsoluteUnit.MONDAY, "last Mon."},
                {Direction.LAST, AbsoluteUnit.TUESDAY, "last Tue."},
                {Direction.LAST, AbsoluteUnit.WEDNESDAY, "last Wed."},
                {Direction.LAST, AbsoluteUnit.THURSDAY, "last Thu."},
                
                {Direction.LAST, AbsoluteUnit.FRIDAY, "last Fri."},
                
                {Direction.LAST, AbsoluteUnit.SATURDAY, "last Sat."},
                {Direction.LAST, AbsoluteUnit.SUNDAY, "last Sun."},
                 
                {Direction.THIS, AbsoluteUnit.DAY, "today"},
                {Direction.THIS, AbsoluteUnit.WEEK, "this wk."},
                {Direction.THIS, AbsoluteUnit.MONTH, "this mo."},
                {Direction.THIS, AbsoluteUnit.YEAR, "this yr."},
                {Direction.THIS, AbsoluteUnit.MONDAY, "this Mon."},
                {Direction.THIS, AbsoluteUnit.TUESDAY, "this Tue."},
                {Direction.THIS, AbsoluteUnit.WEDNESDAY, "this Wed."},
                {Direction.THIS, AbsoluteUnit.THURSDAY, "this Thu."},
                {Direction.THIS, AbsoluteUnit.FRIDAY, "this Fri."},
                {Direction.THIS, AbsoluteUnit.SATURDAY, "this Sat."},
                {Direction.THIS, AbsoluteUnit.SUNDAY, "this Sun."},
                
                {Direction.PLAIN, AbsoluteUnit.DAY, "day"},
                {Direction.PLAIN, AbsoluteUnit.WEEK, "wk."},
                {Direction.PLAIN, AbsoluteUnit.MONTH, "mo."},
                {Direction.PLAIN, AbsoluteUnit.YEAR, "yr."},
                {Direction.PLAIN, AbsoluteUnit.MONDAY, "Mo"},
                {Direction.PLAIN, AbsoluteUnit.TUESDAY, "Tu"},
                {Direction.PLAIN, AbsoluteUnit.WEDNESDAY, "We"},
                {Direction.PLAIN, AbsoluteUnit.THURSDAY, "Th"},
                {Direction.PLAIN, AbsoluteUnit.FRIDAY, "Fr"},
                {Direction.PLAIN, AbsoluteUnit.SATURDAY, "Sa"},
                {Direction.PLAIN, AbsoluteUnit.SUNDAY, "Su"},
                
                {Direction.PLAIN, AbsoluteUnit.NOW, "now"},
                
        };
        RelativeDateTimeFormatter fmt = RelativeDateTimeFormatter.getInstance(
                new ULocale("en_US"),
                null,
                RelativeDateTimeFormatter.Style.SHORT,
                DisplayContext.CAPITALIZATION_NONE);
        for (Object[] row : data) {
            String actual = fmt.format((Direction) row[0], (AbsoluteUnit) row[1]);
            assertEquals("Relative date without quantity short", row[2], actual);
        }
    }
    
    public void TestRelativeDateWithoutQuantityNarrow() {
        Object[][] data = {
                {Direction.NEXT_2, AbsoluteUnit.DAY, null},
                
                {Direction.NEXT, AbsoluteUnit.DAY, "tomorrow"},
                {Direction.NEXT, AbsoluteUnit.WEEK, "next wk."},
               
                {Direction.NEXT, AbsoluteUnit.MONTH, "next mo."},
                {Direction.NEXT, AbsoluteUnit.YEAR, "next yr."},
                
                {Direction.NEXT, AbsoluteUnit.MONDAY, "next M"},
                
                {Direction.NEXT, AbsoluteUnit.TUESDAY, "next Tu"},
                {Direction.NEXT, AbsoluteUnit.WEDNESDAY, "next W"},
                {Direction.NEXT, AbsoluteUnit.THURSDAY, "next Th"},
                {Direction.NEXT, AbsoluteUnit.FRIDAY, "next F"},
                {Direction.NEXT, AbsoluteUnit.SATURDAY, "next Sa"},
                {Direction.NEXT, AbsoluteUnit.SUNDAY, "next Su"},
                
                {Direction.LAST_2, AbsoluteUnit.DAY, null},
                
                {Direction.LAST, AbsoluteUnit.DAY, "yesterday"},
                {Direction.LAST, AbsoluteUnit.WEEK, "last wk."},
                {Direction.LAST, AbsoluteUnit.MONTH, "last mo."},
                {Direction.LAST, AbsoluteUnit.YEAR, "last yr."},
                {Direction.LAST, AbsoluteUnit.MONDAY, "last M"},
                {Direction.LAST, AbsoluteUnit.TUESDAY, "last Tu"},
                {Direction.LAST, AbsoluteUnit.WEDNESDAY, "last W"},
                {Direction.LAST, AbsoluteUnit.THURSDAY, "last Th"},
                {Direction.LAST, AbsoluteUnit.FRIDAY, "last F"},
                {Direction.LAST, AbsoluteUnit.SATURDAY, "last Sa"},
                {Direction.LAST, AbsoluteUnit.SUNDAY, "last Su"},
                 
                {Direction.THIS, AbsoluteUnit.DAY, "today"},
                {Direction.THIS, AbsoluteUnit.WEEK, "this wk."},
                {Direction.THIS, AbsoluteUnit.MONTH, "this mo."},
                {Direction.THIS, AbsoluteUnit.YEAR, "this yr."},
                {Direction.THIS, AbsoluteUnit.MONDAY, "this M"},
                {Direction.THIS, AbsoluteUnit.TUESDAY, "this Tu"},
                {Direction.THIS, AbsoluteUnit.WEDNESDAY, "this W"},
                {Direction.THIS, AbsoluteUnit.THURSDAY, "this Th"},
                
                {Direction.THIS, AbsoluteUnit.FRIDAY, "this F"},
                
                {Direction.THIS, AbsoluteUnit.SATURDAY, "this Sa"},
                {Direction.THIS, AbsoluteUnit.SUNDAY, "this Su"},
                
                {Direction.PLAIN, AbsoluteUnit.DAY, "day"},
                {Direction.PLAIN, AbsoluteUnit.WEEK, "wk."},
                {Direction.PLAIN, AbsoluteUnit.MONTH, "mo."},
                {Direction.PLAIN, AbsoluteUnit.YEAR, "yr."},
                {Direction.PLAIN, AbsoluteUnit.MONDAY, "M"},
                {Direction.PLAIN, AbsoluteUnit.TUESDAY, "T"},
                {Direction.PLAIN, AbsoluteUnit.WEDNESDAY, "W"},
                {Direction.PLAIN, AbsoluteUnit.THURSDAY, "T"},
                {Direction.PLAIN, AbsoluteUnit.FRIDAY, "F"},
                {Direction.PLAIN, AbsoluteUnit.SATURDAY, "S"},
                {Direction.PLAIN, AbsoluteUnit.SUNDAY, "S"},
                
                {Direction.PLAIN, AbsoluteUnit.NOW, "now"},
                
        };
        RelativeDateTimeFormatter fmt = RelativeDateTimeFormatter.getInstance(
                new ULocale("en_US"),
                null,
                RelativeDateTimeFormatter.Style.NARROW,
                DisplayContext.CAPITALIZATION_NONE);
        for (Object[] row : data) {
            String actual = fmt.format((Direction) row[0], (AbsoluteUnit) row[1]);
            assertEquals("Relative date without quantity narrow", row[2], actual);
        }
    }
    
    public void TestTwoBeforeTwoAfter() {
        Object[][] data = {
                {Direction.NEXT_2, AbsoluteUnit.DAY, "pasado ma\u00F1ana"},
                {Direction.LAST_2, AbsoluteUnit.DAY, "anteayer"},
        };
        RelativeDateTimeFormatter fmt = RelativeDateTimeFormatter.getInstance(new ULocale("es"));
        for (Object[] row : data) {
            String actual = fmt.format((Direction) row[0], (AbsoluteUnit) row[1]);
            assertEquals("Two before two after", row[2], actual);
        }
    }
    
    public void TestFormatWithQuantityIllegalArgument() {
        RelativeDateTimeFormatter fmt = RelativeDateTimeFormatter.getInstance(new ULocale("en_US"));
        try {
            fmt.format(1.0, Direction.PLAIN, RelativeUnit.DAYS);
            fail("Expected IllegalArgumentException.");
        } catch (IllegalArgumentException e) {
            // Expected
        }
        try {
            fmt.format(1.0, Direction.THIS, RelativeUnit.DAYS);
            fail("Expected IllegalArgumentException."); 
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }
    
    public void TestFormatWithoutQuantityIllegalArgument() {
        RelativeDateTimeFormatter fmt = RelativeDateTimeFormatter.getInstance(new ULocale("en_US"));
        try {
            fmt.format(Direction.LAST, AbsoluteUnit.NOW);
            fail("Expected IllegalArgumentException.");
        } catch (IllegalArgumentException e) {
            // Expected
        }
        try {
            fmt.format(Direction.NEXT, AbsoluteUnit.NOW);
            fail("Expected IllegalArgumentException.");
        } catch (IllegalArgumentException e) {
            // Expected
        }
        try {
            fmt.format(Direction.THIS, AbsoluteUnit.NOW);
            fail("Expected IllegalArgumentException.");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }
    
    public void TestCustomNumberFormat() {
        ULocale loc = new ULocale("en_US");
        NumberFormat nf = NumberFormat.getInstance(loc);
        nf.setMinimumFractionDigits(1);
        nf.setMaximumFractionDigits(1);
        RelativeDateTimeFormatter fmt = RelativeDateTimeFormatter.getInstance(loc, nf);
        
        // Change nf after the fact to prove that we made a defensive copy
        nf.setMinimumFractionDigits(3);
        nf.setMaximumFractionDigits(3);
        
        // Change getNumberFormat to prove we made defensive copy going out.
        fmt.getNumberFormat().setMinimumFractionDigits(5);
        assertEquals(
                "TestCustomNumberformat", 1, fmt.getNumberFormat().getMinimumFractionDigits());
        
        Object[][] data = {
            {0.0, Direction.NEXT, RelativeUnit.SECONDS, "in 0.0 seconds"},
            {0.5, Direction.NEXT, RelativeUnit.SECONDS, "in 0.5 seconds"},
            {1.0, Direction.NEXT, RelativeUnit.SECONDS, "in 1.0 seconds"},
            {2.0, Direction.NEXT, RelativeUnit.SECONDS, "in 2.0 seconds"},
        };
        for (Object[] row : data) {
            String actual = fmt.format(
                    ((Double) row[0]).doubleValue(), (Direction) row[1], (RelativeUnit) row[2]);
            assertEquals("Relative date with quantity special NumberFormat", row[3], actual);
        }
    }
    
    public void TestCombineDateAndTime() {
        RelativeDateTimeFormatter fmt = RelativeDateTimeFormatter.getInstance(new ULocale("en_US"));
        assertEquals("TestcombineDateAndTime", "yesterday, 3:50", fmt.combineDateAndTime("yesterday", "3:50"));
    }

    public void TestJavaLocale() {
        Locale loc = Locale.US;
        double amount = 12.3456d;

        RelativeDateTimeFormatter fmt = RelativeDateTimeFormatter.getInstance(loc);
        String s = fmt.format(amount, Direction.LAST, RelativeUnit.SECONDS);
        assertEquals("Java Locale.US", "12.346 seconds ago", s);

        // Modified instance
        NumberFormat nf = fmt.getNumberFormat();
        nf.setMaximumFractionDigits(1);
        fmt = RelativeDateTimeFormatter.getInstance(loc, nf);

        s = fmt.format(amount, Direction.LAST, RelativeUnit.SECONDS);
        assertEquals("Java Locale.US", "12.3 seconds ago", s);
    }
    public void TestGetters() {
        RelativeDateTimeFormatter fmt = RelativeDateTimeFormatter.getInstance(
                new ULocale("en_US"),
                null,
                RelativeDateTimeFormatter.Style.SHORT,
                DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE);
        assertEquals("", RelativeDateTimeFormatter.Style.SHORT, fmt.getFormatStyle());
        assertEquals(
                "",
                DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE,
                fmt.getCapitalizationContext());
    }
    
    public void TestBadDisplayContext() {
        try {
            RelativeDateTimeFormatter.getInstance(
                    new ULocale("en_US"),
                    null,
                    RelativeDateTimeFormatter.Style.LONG,
                    DisplayContext.STANDARD_NAMES);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    public void TestSidewaysDataLoading() {
        RelativeDateTimeFormatter fmt = RelativeDateTimeFormatter.getInstance(
                new ULocale("en_GB"),
                null,
                RelativeDateTimeFormatter.Style.NARROW,
                DisplayContext.CAPITALIZATION_NONE);
        String s = fmt.format(3.0, Direction.NEXT, RelativeUnit.MONTHS);
        assertEquals("narrow: in 3 months", "in 3 mo", s);
        String t = fmt.format(1.0, Direction.LAST, RelativeUnit.QUARTERS);
        assertEquals("narrow: 1 qtr ago", "1 qtr ago", t);
        // Check for fallback to SHORT
        String u = fmt.format(3.0, Direction.LAST, RelativeUnit.QUARTERS);
        assertEquals("narrow: 3 qtr ago", "3 qtr ago", u);
        // Do not expect fall back to SHORT
        String v = fmt.format(1.0, Direction.LAST, RelativeUnit.QUARTERS);
        assertEquals("narrow: 1 qtr ago", "1 qtr ago", v);
        String w = fmt.format(6.0, Direction.NEXT, RelativeUnit.QUARTERS);
        assertEquals("narrow: in 6 qtr", "in 6 qtr", w);
    }
}
