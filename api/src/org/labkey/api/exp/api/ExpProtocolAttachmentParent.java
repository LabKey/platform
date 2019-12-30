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
package org.labkey.api.exp.api;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentType;

public class ExpProtocolAttachmentParent implements AttachmentParent
{
    private final ExpProtocol _protocol;

    public ExpProtocolAttachmentParent(ExpProtocol protocol)
    {
        _protocol = protocol;
    }

    @Override
    public String getEntityId()
    {
        return _protocol.getEntityId();
    }

    @Override
    public String getContainerId()
    {
        return _protocol.getContainer().getId();
    }

    @Override
    public @NotNull AttachmentType getAttachmentType()
    {
        return ExpRunAttachmentType.get();
    }
}
