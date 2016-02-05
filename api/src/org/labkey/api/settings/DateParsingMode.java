/*
 * Copyright (c) 2014-2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.settings;

import org.labkey.api.collections.CsvSet;
import org.labkey.api.util.DateUtil.MonthDayOption;

import java.util.Set;

/**
 * User: adam
 * Date: 1/10/14
 * Time: 3:25 PM
 */
public enum DateParsingMode
{
    US("U.S. date parsing (MDY)", MonthDayOption.MONTH_DAY, "31/1/2004, 13/1/2004, 31/12/2004"),
    NON_US("Non-U.S. date parsing (DMY)", MonthDayOption.DAY_MONTH, "1/31/2004, 1/13/2004, 12/31/2004");

    private final String _displayString;
    private final MonthDayOption _dayMonth;
    private final String _illegalFormats;

    DateParsingMode(String displayString, MonthDayOption dayMonth, String illegalFormats)
    {
        _dayMonth = dayMonth;
        _displayString = displayString;
        _illegalFormats = illegalFormats;
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

    public MonthDayOption getDayMonth()
    {
        return _dayMonth;
    }

    public Set<String> getIllegalFormats()
    {
        return new CsvSet(_illegalFormats);
    }
}
