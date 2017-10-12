package org.labkey.api.webdav.permissions;

import org.labkey.api.security.permissions.AbstractPermission;

/**
 * Created by davebradlee on 10/10/17.
 */
public class SeeFilePathsPermission extends AbstractPermission
{
    public SeeFilePathsPermission()
    {
        super("See Absolute File Paths", "Users may see absolute file paths");
    }
}
