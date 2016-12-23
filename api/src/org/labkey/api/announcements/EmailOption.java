/*
 * Copyright (c) 2016 LabKey Corporation
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
package org.labkey.api.announcements;

import java.util.HashMap;
import java.util.Map;

/**
 * Wrapper over a bitmask that controls the subscription type for notification messages related to announcement
 * or message board updates.
 * User: arauch
 * Date: Sep 24, 2005
 */
public enum EmailOption
{
    NOT_SET(-1),
    MESSAGES_NONE(0),
    MESSAGES_ALL(1),
    MESSAGES_MINE(2), // Only threads I've posted to or where I'm on the member list
    MESSAGES_NO_DAILY_DIGEST(3),
    MESSAGES_DAILY_DIGEST(4),
    //NONE_DAILY_DIGEST(256)  Note that AnnouncementsController.getEmailOption() will never allow this combination, and sets EmailOption to 0 if this occurs in the UI
    MESSAGES_ALL_DAILY_DIGEST(257),  // combines MESSAGES_ALL and MESSAGES_DAILY_DIGEST
    MESSAGES_MINE_DAILY_DIGEST(258),  // combines MESSAGES_MINE and MESSAGES_DAILY_DIGEST
    FILES_NONE(512),
    FILES_INDIVIDUAL(513),
    FILES_DAILY_DIGEST(514);

    private final int value;

    public static final Map<Integer, EmailOption> intToEmailOptionMap = new HashMap<>();

    static
    {
        for (EmailOption option : EmailOption.values())
        {
            intToEmailOptionMap.put(option.value, option);
        }
    }

    EmailOption(int value)
    {
        this.value = value;
    }

    public int getValue()
    {
        return this.value;
    }

    public static Boolean isValid(int intValue)
    {
        return intToEmailOptionMap.containsKey(intValue);
    }
}

