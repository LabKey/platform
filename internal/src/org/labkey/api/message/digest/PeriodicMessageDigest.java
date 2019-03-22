/*
 * Copyright (c) 2011-2016 LabKey Corporation
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

import org.quartz.DateBuilder;
import org.quartz.DateBuilder.IntervalUnit;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;

/**
 * User: klum
 * Date: Jan 13, 2011
 * Time: 4:58:53 PM
 */
public class PeriodicMessageDigest extends MessageDigest
{
    private final int _seconds;
    private final String _name;

    public PeriodicMessageDigest(String name, int seconds)
    {
        _name = name;
        _seconds = seconds;
    }

    @Override
    public String getName()
    {
        return _name;
    }

    @Override
    protected Trigger getTrigger()
    {
        return TriggerBuilder.newTrigger()
            .withSchedule(SimpleScheduleBuilder.repeatSecondlyForever(_seconds))
            .startAt(DateBuilder.futureDate(_seconds, IntervalUnit.SECOND))
            .build();
    }
}
