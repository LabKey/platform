/*
 * Copyright (c) 2017 LabKey Corporation
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
package org.labkey.api.pipeline.trigger;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ImpersonatePermission;
import org.labkey.api.security.permissions.ImpersonatePrivilegedSiteRolesPermission;
import org.labkey.api.security.permissions.ReadPermission;

import java.nio.file.Path;
import java.util.Date;
import java.util.Map;

/**
 * Pipeline Trigger configuration.
 */
public interface PipelineTriggerConfig
{
    int getRowId();

    String getName();

    String getDescription();

    String getType();

    Date getLastChecked();

    boolean isEnabled();

    String getConfiguration();

    String getPipelineId();

    Container lookupContainer();

    PipelineTriggerType getPipelineTriggerType();

    void start();

    void stop();

    boolean matches(Path directory, Path entry, /*out*/ @Nullable Map<String, String> namedGroupSubstitutions);

    String getStatus();

    /**
     * GH Issue 1466: a trigger job runs with the run-as user's full roles, so the saving user must be entitled to
     * impersonate that user, and a privileged target additionally requires the privileged-impersonation permission.
     */
    static boolean canRunAs(Container container, User user, User runAsUser)
    {
        if (runAsUser.equals(user))
            return true;
        if (!runAsUser.isActive())
            return false;
        if (runAsUser.hasPrivilegedRole() && !user.hasRootPermission(ImpersonatePrivilegedSiteRolesPermission.class))
            return false;
        if (user.hasRootPermission(ImpersonatePermission.class))
            return true;

        Container project = container.getProject();
        return project != null && project.hasPermission(user, ImpersonatePermission.class) && project.hasPermission(runAsUser, ReadPermission.class);
    }
}