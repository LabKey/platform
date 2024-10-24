/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

/**
 * User: adam
 * Date: Jan 3, 2007
 * Time: 3:41:22 PM
 */
public class AttachmentForm implements BaseDownloadAction.InlineDownloader
{
    private String _entityId = null;
    private String _name = null;

    private boolean _inline = true;

    public String getEntityId()
    {
        return _entityId;
    }

    public void setEntityId(String entityId)
    {
        _entityId = entityId;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    @Override
    public boolean isInline()
    {
        return _inline;
    }

    public void setInline(boolean inline)
    {
        _inline = inline;
    }
}
