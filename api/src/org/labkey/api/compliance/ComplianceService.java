/*
 * Copyright (c) 2017-2018 LabKey Corporation
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
package org.labkey.api.compliance;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.ByteArrayAttachmentFile;
import org.labkey.api.data.Activity;
import org.labkey.api.data.Container;
import org.labkey.api.data.PHI;
import org.labkey.api.query.QueryAction;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * Created by davebradlee on 7/27/17.
 *
 * ComplianceService: ALL METHODS MUST CHECK IF ComplianceModule is enabled in container (if appropriate); callers don't check
 *
 */
public interface ComplianceService
{
    static @NotNull ComplianceService get()
    {
        // Return default service if module not registered
        ComplianceService service = ServiceRegistry.get().getService(ComplianceService.class);
        if (null == service)
            service = new DefaultComplianceService();
        return service;
    }

    static void setInstance(ComplianceService instance)
    {
        ServiceRegistry.get().registerService(ComplianceService.class, instance);
    }

    String getModuleName();
    ActionURL urlFor(Container container, QueryAction action, ActionURL queryBasedUrl);
    boolean hasElecSignPermission(@NotNull Container container, @NotNull User user);
    boolean hasViewSignedSnapshotsPermission(@NotNull Container container, @NotNull User user);
    default @NotNull PHI getMaxAllowedPhi(@NotNull Container container, @NotNull User user)
    {
        return PHI.Restricted;
    }
    default Activity getCurrentActivity(ViewContext viewContext)
    {
        return null;
    }

    /**
     * Get the compliance folder settings.
     * @throws org.labkey.api.view.UnauthorizedException if the user doesn't have sufficient permissions.
     */
    ComplianceFolderSettings getFolderSettings(@NotNull Container container, @NotNull User user) throws UnauthorizedException;

    /**
     * Update the compliance folder settings.
     * @throws org.labkey.api.view.UnauthorizedException if the user doesn't have sufficient permissions.
     */
    void setFolderSettings(@NotNull Container container, @NotNull User user, @NotNull ComplianceFolderSettings settings);

    /**
     * CRD operations for signed snapshots
     */
    Integer insertSignedSnapshot(Container container, User user, SignedSnapshot snapshot, ByteArrayAttachmentFile file) throws IOException;
    SignedSnapshot getSignedSnapshot(Container container, int snapshotId);
    Collection<SignedSnapshot> getSignedSnapshots(Container container, String ownerEntityId);
    Map<String, Object> deleteSignedSnapshot(Container container, User user, int snapshotId);

    Pair<AttachmentParent, String> downloadSnapshot(Container container, User user, @NotNull SignedSnapshot snapshot);

    class DefaultComplianceService implements ComplianceService
    {
        @Override
        public String getModuleName()
        {
            return ComplianceService.class.getName();
        }
        @Override
        public ActionURL urlFor(Container container, QueryAction action, ActionURL queryBasedUrl)
        {
            return null;
        }
        @Override
        public boolean hasElecSignPermission(@NotNull Container container, @NotNull User user)
        {
            return false;
        }
        @Override
        public boolean hasViewSignedSnapshotsPermission(@NotNull Container container, @NotNull User user)
        {
            return false;
        }
        @Override
        @NotNull public PHI getMaxAllowedPhi(@NotNull Container container, @NotNull User user)
        {
            return PHI.Restricted;
        }
        @Override
        public Activity getCurrentActivity(ViewContext viewContext)
        {
            return null;
        }

        @Override
        public ComplianceFolderSettings getFolderSettings(@NotNull Container container, @NotNull User user) throws UnauthorizedException
        {
            return null;
        }

        @Override
        public void setFolderSettings(@NotNull Container container, @NotNull User user, @NotNull ComplianceFolderSettings settings)
        {
        }

        @Override
        public Integer insertSignedSnapshot(Container container, User user, SignedSnapshot snapshot, ByteArrayAttachmentFile file) throws IOException
        {
            return null;
        }

        @Override
        public SignedSnapshot getSignedSnapshot(Container container, int snapshotId)
        {
            return null;
        }

        @Override
        public Collection<SignedSnapshot> getSignedSnapshots(Container container, String ownerEntityId)
        {
            return Collections.emptyList();
        }

        @Override
        public Map<String, Object> deleteSignedSnapshot(Container container, User user, int snapshotId)
        {
            return null;
        }

        @Override
        public Pair<AttachmentParent, String> downloadSnapshot(Container container, User user, SignedSnapshot snapshot)
        {
            return null;
        }
    }
}
