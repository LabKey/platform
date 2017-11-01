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
package org.labkey.api.attachments;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager.ContainerParent;

public class LookAndFeelResourceAttachmentParent extends ContainerParent
{
    public LookAndFeelResourceAttachmentParent(Container c)
    {
        super(c);
    }

    @Override
    public @NotNull AttachmentType getAttachmentType()
    {
        return LookAndFeelResourceType.get();
    }
}
