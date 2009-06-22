package org.labkey.api.security.permissions;

/**
 * Created by IntelliJ IDEA.
 * User: dave
 * Date: Jun 22, 2009
 * Time: 3:10:06 PM
 */
public class EditSharedViewPermission extends AbstractPermission
{
    public EditSharedViewPermission()
    {
        super("Edit Shared Query Views", "Allows users to create and edit shared query custom views.");
    }
}
