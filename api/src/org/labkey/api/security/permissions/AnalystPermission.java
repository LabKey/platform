package org.labkey.api.security.permissions;

public class AnalystPermission extends AbstractPermission
{
    public AnalystPermission()
    {
        //TODO: review description
        super("Analyst", "Can write code within a secured context. Code shared by this user can be run upon acceptance by end-user.");
    }
}