/*
 * Copyright (c) 2011-2014 LabKey Corporation
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
package org.labkey.study.specimen.report;

import org.apache.commons.lang3.StringUtils;

/**
 * User: adam
 * Date: 12/12/11
 * Time: 1:40 PM
 */

// Some specimen reports coalesce title columns vertically instead of repeating the same information in every row.  This
// is a problem in demo mode, where all PTIDs of the same length resolve to the same display value.  This class was added
// to distinguish "value" (the underlying value we use to coalesce) from "display value" (what we display to the user).
public class SpecimenReportTitle
{
    private final String _value;
    private final String _displayValue;

    // Simple case -- value and display value are the same
    public SpecimenReportTitle(String value)
    {
        this(value, value);
    }

    public SpecimenReportTitle(String value, String displayValue)
    {
        _value = StringUtils.trimToEmpty(value);
        _displayValue = StringUtils.trimToEmpty(displayValue);
    }

    public String getValue()
    {
        return _value;
    }

    public String getDisplayValue()
    {
        return _displayValue;
    }
}
