package org.labkey.api.security.permissions;

/**
 * Created with IntelliJ IDEA.
 * User: davebradlee
 * Date: 10/9/13
 * Time: 1:22 PM
 * To change this template use File | Settings | File Templates.
 */
public class EnableRestrictedModules extends AbstractPermission
{
    public EnableRestrictedModules()
    {
        super("Enable Restricted Modules", "Allows restricted modules to be activated");
    }
}
