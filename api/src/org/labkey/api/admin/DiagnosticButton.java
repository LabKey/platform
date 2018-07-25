package org.labkey.api.admin;

import org.labkey.api.data.Container;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.view.ActionURL;

public class DiagnosticButton
{
    private ActionURL _linkUrl;
    private Class<? extends Permission> _permission;

    public DiagnosticButton(ActionURL linkUrl)
    {
        this(linkUrl, AdminPermission.class); //default to Admin permission if not specified
    }

    public DiagnosticButton(ActionURL linkUrl, Class<? extends Permission> permission)
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
