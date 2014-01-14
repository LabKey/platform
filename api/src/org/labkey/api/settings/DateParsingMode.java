package org.labkey.api.settings;

import org.labkey.api.util.DateUtil;

/**
 * User: adam
 * Date: 1/10/14
 * Time: 3:25 PM
 */
public enum DateParsingMode
{
    US("U.S. date parsing (MDY)", DateUtil.MonthDayOption.MONTH_DAY),
    NON_US("Non-U.S. date parsing (DMY)", DateUtil.MonthDayOption.DAY_MONTH);

    private final String _displayString;
    private final DateUtil.MonthDayOption _dayMonth;

    DateParsingMode(String displayString, DateUtil.MonthDayOption dayMonth)
    {
        _dayMonth = dayMonth;
        _displayString = displayString;
    }

    public String getDisplayString()
    {
        return _displayString;
    }

    public static DateParsingMode fromString(String name)
    {
        if ("NON_US".equals(name))
            return NON_US;
        else
            return US;
    }

    public DateUtil.MonthDayOption getDayMonth()
    {
        return _dayMonth;
    }
}
