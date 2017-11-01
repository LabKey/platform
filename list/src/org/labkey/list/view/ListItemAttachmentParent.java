/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

package org.labkey.list.view;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.data.Container;
import org.labkey.api.exp.list.ListItem;

/**
 * User: adam
 * Date: Jan 31, 2008
 * Time: 6:46:59 PM
 */
public class ListItemAttachmentParent implements AttachmentParent
{
    private final String _entityId;
    private final Container _c;

    public ListItemAttachmentParent(ListItem item, Container c)
    {
        this(item.getEntityId(), c);
    }

    public ListItemAttachmentParent(String entityId, Container c)
    {
        _entityId = entityId;
        _c = c;
    }

    public String getEntityId()
    {
        return _entityId;
    }

    public String getContainerId()
    {
        return _c.getId();
    }

    @NotNull
    @Override
    public AttachmentType getAttachmentType()
    {
        return ListItemType.get();
    }
}
