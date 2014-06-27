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
package org.labkey.api.message.digest;

import org.labkey.api.query.NotificationInfoProvider;

public abstract class ReportAndDatasetChangeDigestProvider implements MessageDigest.Provider
{
    static private ReportAndDatasetChangeDigestProvider _instance;

    public abstract void addNotificationInfoProvider(NotificationInfoProvider provider);

    static public ReportAndDatasetChangeDigestProvider get()
    {
        if (null == _instance)
            throw new IllegalStateException("Service has not been set.");
        return _instance;
    }
    public static void set(ReportAndDatasetChangeDigestProvider serviceImpl)
    {
        if (null != _instance)
            throw new IllegalStateException("Service has already been set.");
        _instance = serviceImpl;
    }

}
