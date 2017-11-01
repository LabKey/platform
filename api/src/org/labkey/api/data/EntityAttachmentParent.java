/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

package org.labkey.api.data;

import org.labkey.api.attachments.AttachmentParent;

/**
 * User: adam
 * Date: Mar 31, 2007
 * Time: 9:05:32 PM
 */

// Convenience base class for Entities that need to be an AttachmentParent. This should remain an abstract class.
public abstract class EntityAttachmentParent implements AttachmentParent
{
    private final String _containerId;
    private final String _entityId;

    // Don't use this constructor, unless absolutely necessary. Make sure permissions have been checked on both
    // ContainerId and EntityId (that it's in the current container and represents the correct object type).
    protected EntityAttachmentParent(String containerId, String entityId)
    {
        _containerId = containerId;
        _entityId = entityId;
    }

    protected EntityAttachmentParent(Entity entity)
    {
        this(entity.getContainerId(), entity.getEntityId());
    }

    @Override
    public String getContainerId()
    {
        return _containerId;
    }

    @Override
    public String getEntityId()
    {
        return _entityId;
    }
}
