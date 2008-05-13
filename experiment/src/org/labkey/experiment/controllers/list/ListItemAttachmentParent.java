/*
 * Copyright (c) 2008 LabKey Corporation
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

package org.labkey.experiment.controllers.list;

import org.labkey.api.exp.list.ListItem;
import org.labkey.api.data.Container;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.Attachment;
import org.labkey.experiment.list.ListItm;

import java.util.Collection;

/**
 * User: adam
 * Date: Jan 31, 2008
 * Time: 6:46:59 PM
 */
public class ListItemAttachmentParent implements AttachmentParent
{
    private String _entityId;
    private Container _c;

    public ListItemAttachmentParent(ListItem item, Container c)
    {
        this(item.getEntityId(), c);
    }

    public ListItemAttachmentParent(ListItm itm, Container c)
    {
        this(itm.getEntityId(), c);
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

    // AttachmentServiceImpl uses this to retrieve the attachments of many parents with a single query.  Implementation
    // is not necessary in most cases.
    public void setAttachments(Collection<Attachment> attachments)
    {
    }
}
