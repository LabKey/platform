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
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.attachments.ByteArrayAttachmentFile;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.Pair;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;

/**
 * SigningService: ALL METHODS MUST CHECK IF ComplianceModule is enabled in container (if appropriate); callers don't check
 */
public interface SigningService
{
    static @NotNull SigningService get()
    {
        // Return default service if module not registered
        SigningService service = ServiceRegistry.get().getService(SigningService.class);
        if (null == service)
            service = new DefaultSigningService();
        return service;
    }

    static void setInstance(SigningService instance)
    {
        ServiceRegistry.get().registerService(SigningService.class, instance);
    }

    /**
     * Register a supplier for an attachment parent for attachment types that should use an attachment parent that differs
     * from the default one provided (for example: saving the attachment to the file system instead of the DB).
     * The attachment type will need to be specified on the SignedSnapshot.
     */
    void registerAttachmentParentSupplier(AttachmentType type, Function<SignedSnapshot, AttachmentParent> supplier);

    /**
     * CRD operations for signed snapshots
     */
    Integer insertSignedSnapshot(Container container, User user, SignedSnapshot snapshot, ByteArrayAttachmentFile file) throws IOException;
    SignedSnapshot getSignedSnapshot(Container container, int snapshotId);
    Collection<SignedSnapshot> getSignedSnapshots(Container container, String ownerEntityId);
    Map<String, Object> deleteSignedSnapshot(Container container, User user, int snapshotId);

    Pair<AttachmentParent, String> downloadSnapshot(Container container, User user, @NotNull SignedSnapshot snapshot);

    class DefaultSigningService implements SigningService
    {
        @Override
        public Integer insertSignedSnapshot(Container container, User user, SignedSnapshot snapshot, ByteArrayAttachmentFile file)
        {
            return null;
        }

        @Override
        public void registerAttachmentParentSupplier(AttachmentType type, Function<SignedSnapshot, AttachmentParent> supplier)
        {
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
        public Pair<AttachmentParent, String> downloadSnapshot(Container container, User user, @NotNull SignedSnapshot snapshot)
        {
            return null;
        }
    }
}
