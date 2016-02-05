/*
 * Copyright (c) 2015-2016 LabKey Corporation
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
package org.labkey.api.attachments;

import org.labkey.api.data.Container;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.view.ViewContext;

/**
 * Created by xingyang on 12/8/15.
 */
public class AttachmentParentImpl implements AttachmentParent
{
    private final String _entityId;
    private final Container _c;

    public AttachmentParentImpl(String entityId, Container c)
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

    @Override
    public String getDownloadURL(ViewContext context, String name)
    {
        return null;
    }

    @Override
    public SecurityPolicy getSecurityPolicy()
    {
        return null;
    }
}
