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
