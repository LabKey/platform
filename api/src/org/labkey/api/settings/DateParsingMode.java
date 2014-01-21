/*
 * Copyright (c) 2014 LabKey Corporation
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
