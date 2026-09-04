/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.api.exp.api;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.notification.NotificationService;
import org.labkey.api.data.Container;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.logging.LogHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public enum SampleChangeNotify
{
    SampleDataChanged;

    private static final Logger LOG = LogHelper.getLogger(SampleChangeNotify.class, "Sample data change WebSocket notifications");

    public static void fireSampleDataChanged(@Nullable Container container)
    {
        if (container == null)
            return;

        try
        {
            Set<Class<? extends Permission>> read = Collections.singleton(ReadPermission.class);
            List<User> readers = SecurityManager.getUsersWithPermissions(container, read);
            List<Integer> userIds = new ArrayList<>(readers.size());
            for (User user : readers)
                userIds.add(user.getUserId());

            if (!userIds.isEmpty())
                NotificationService.get().sendServerEvent(userIds, SampleChangeNotify.SampleDataChanged);
        }
        catch (Exception e)
        {
            LOG.warn("Failed to send sample data changed notification for container " + container.getPath(), e);
        }
    }
}
