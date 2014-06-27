/*
 * Copyright (c) 2011-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.message.digest;

import org.apache.commons.lang3.time.DateUtils;

import java.util.Calendar;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

/**
 * User: klum
 * Date: Jan 13, 2011
 * Time: 2:14:17 PM
 */
public class DailyMessageDigest extends MessageDigest
{
    private static final DailyMessageDigest _instance = new DailyMessageDigest();

    public static DailyMessageDigest getInstance()
    {
        return _instance;
    }

    // Use only for testing
    public DailyMessageDigest(){}

    @Override
    protected Timer createTimer(TimerTask task)
    {
        Timer timer = new Timer(getName(), true);
        timer.scheduleAtFixedRate(task, getMidnight(new Date(), 1, 5), DateUtils.MILLIS_PER_DAY);  // 12:05AM tomorrow morning

        return timer;
    }

    @Override
    public String getName()
    {
        return "DailyDigest";
    }

    @Override
    protected Date getStartRange(Date current, Date last)
    {
        if (null == last)
            return getMidnight(current, -1, 0);  // If nothing is set, start yesterday morning at midnight
        else
            return last;
    }

    @Override
    protected Date getEndRange(Date current, Date last)
    {
        return getMidnight(current, 0, 0);  // Until midnight this morning
    }

    // Calculate midnight of date entered
    private Date getMidnight(Date date, int addDays, int addMinutes)
    {
        Calendar current = Calendar.getInstance();

        current.setTime(date);
        current.add(Calendar.DATE, addDays);
        current.set(Calendar.HOUR_OF_DAY, 0);  // Midnight
        current.set(Calendar.MINUTE, addMinutes);
        current.set(Calendar.SECOND, 0);
        current.set(Calendar.MILLISECOND, 0);

        return current.getTime();
    }
}
