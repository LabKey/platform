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
package org.labkey.api.security;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ContainerManager.ContainerParent;

// Used for attaching SSO authentication logos to the root container
public class AuthenticationLogoAttachmentParent extends ContainerParent
{
    private AuthenticationLogoAttachmentParent(Container c)
    {
        super(c);
    }

    public static AuthenticationLogoAttachmentParent get()
    {
        Container root = ContainerManager.getRoot();

        if (null == root)
            return null;
        else
            return new AuthenticationLogoAttachmentParent(root);
    }

    @Override
    public @NotNull AttachmentType getAttachmentType()
    {
        return AuthenticationLogoType.get();
    }
}
