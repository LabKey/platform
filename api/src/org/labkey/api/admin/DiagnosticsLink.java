package org.labkey.api.admin;

import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.view.ActionURL;

public class DiagnosticsLink
{
    private ActionURL _linkUrl;
    private Class<? extends Permission> _permission;

    public DiagnosticsLink(ActionURL linkUrl)
    {
        this(linkUrl, AdminPermission.class); //default to Admin permission if not specified
    }

    public DiagnosticsLink(ActionURL linkUrl, Class<? extends Permission> permission)
    {
        _linkUrl = linkUrl;
        _permission = permission;
    }

    public ActionURL getLinkUrl()
    {
        return _linkUrl;
    }

    public Class<? extends Permission> getPermission()
    {
        return _permission;
    }
}
