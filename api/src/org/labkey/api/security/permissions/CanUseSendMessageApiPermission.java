package org.labkey.api.security.permissions;

public class CanUseSendMessageApiPermission extends AbstractPermission
{
    public CanUseSendMessageApiPermission()
    {
        super("Use SendMessage API", "Allows users to use the send message API to create email messages.");
    }
}
