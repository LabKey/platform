package org.labkey.list.security;

import org.labkey.api.lists.permissions.DesignListPermission;
import org.labkey.api.security.Group;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.roles.AbstractRole;
import org.labkey.list.ListModule;

/**
 * User: tgaluhn
 * Date: 2/8/2017
 */
public class ListDesignerRole extends AbstractRole
{
    public ListDesignerRole()
    {
        super("List Designer", "List designers can manage and design LabKey Lists",
                ListModule.class,
                DesignListPermission.class);

        addExcludedPrincipal(SecurityManager.getGroup(Group.groupGuests));
    }
}
