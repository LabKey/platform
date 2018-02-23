package org.labkey.api.security.roles;

import org.labkey.api.security.permissions.CanUseSendMessageApiPermission;

public class CanUseSendMessageApi extends AbstractRootContainerRole
{
    public CanUseSendMessageApi()
    {
        super("Use SendMessage API", "Allows users to use the send message API.  This API can be used to author code which sends emails to users and potentially non-users of the system.",
                CanUseSendMessageApiPermission.class);
    }
}
