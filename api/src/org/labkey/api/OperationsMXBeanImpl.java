/*
 * Copyright (c) 2023-2026 LabKey Corporation
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
package org.labkey.api;

import org.apache.logging.log4j.Logger;
import org.labkey.api.mbean.OperationsMXBean;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.HeartBeat;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.template.WarningService;
import org.labkey.api.view.template.Warnings;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

class OperationsMXBeanImpl implements OperationsMXBean
{
    private static final Logger LOG = LogHelper.getLogger(OperationsMXBeanImpl.class, "Startup warnings");

    /**
     * Keep track of the warnings we've seen so that we log them exactly once
     */
    Set<HtmlString> _loggedWarnings = new HashSet<>();

    @Override
    public Long getMinutesSinceMostRecentUserActivity()
    {
        return UserManager.getMinutesSinceMostRecentUserActivity();
    }

    @Override
    public int getUserCountInLastTenMinutes()
    {
        return UserManager.getRecentUsers(HeartBeat.currentTimeMillis() - TimeUnit.MINUTES.toMillis(10)).size();
    }

    @Override
    public int getUserCountInLastHour()
    {
        return UserManager.getRecentUsers(HeartBeat.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)).size();
    }

    @Override
    public int getActiveUserSessionCount()
    {
        return UserManager.getActiveUserSessionCount();
    }

    @Override
    public int getSiteWarningCount()
    {
        Warnings warnings = WarningService.get().getWarnings(null);
        // We could adjust warnings to include both an HTML and plain text version of the message, but for our
        // initial purposes, an automated flattening should be sufficient
        for (HtmlString message : warnings.getMessages())
        {
            if (_loggedWarnings.add(message))
            {
                LOG.error(message.toText());
            }
        }

        return warnings.getMessages().size();
    }
}
