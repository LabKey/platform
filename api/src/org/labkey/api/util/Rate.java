/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.api.util;

import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.TimeUnit;

/**
* User: adam
* Date: 6/11/13
* Time: 10:05 AM
*/
public class Rate
{
    private final double _rate;
    private final String _toString;

    public Rate(long count, TimeUnit unit)
    {
        this(count, 1, unit);
    }

    public Rate(long count, long duration, TimeUnit unit)
    {
        if (unit == TimeUnit.MILLISECONDS && 0 == (duration % 1000))
        {
            duration /= 1000;
            unit = TimeUnit.SECONDS;
        }

        _rate = (double)count / (double)unit.toMillis(duration);

        if (duration == 1)
            _toString = "" + count + "/" + StringUtils.stripEnd(unit.toString(), "S");
        else
            _toString = "" + count + "/(" + duration + " "  + unit.toString()+ ")";
    }

    // Count per millisecond
    public double getRate()
    {
        return _rate;
    }

    // Count per some other time unit
    public double getRate(TimeUnit unit)
    {
        return _rate * unit.toMillis(1);
    }

    @Override
    public String toString()
    {
        return _toString;
    }
}
