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
package org.labkey.api.mbean;

public interface OperationsMXBean
{
    /** @return number of minutes since an authenticated user last interacted with the server. null if nobody's logged in since the server started */
    Long getMinutesSinceMostRecentUserActivity();

    /** @return number of distinct authenticated users in the last 10 minutes */
    int getUserCountInLastTenMinutes();
    /** @return number of distinct authenticated users in the last hour */
    int getUserCountInLastHour();

    /** @return number of active, authenticated user HTTP sessions that are currently active */
    int getActiveUserSessionCount();

    /** @return number of site-wide warnings/errors that a site admin would see if they logged in */
    int getSiteWarningCount();
}
