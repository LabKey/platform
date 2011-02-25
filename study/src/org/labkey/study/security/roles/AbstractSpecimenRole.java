package org.labkey.study.security.roles;

import org.labkey.api.data.Container;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.AbstractRole;
import org.labkey.api.study.StudyService;
import org.labkey.study.StudyModule;

/**
 * User: jeckels
 * Date: Feb 25, 2011
 */
public class AbstractSpecimenRole extends AbstractRole
{
    protected AbstractSpecimenRole(String name, String description, Class<? extends Permission>... perms)
    {
        super(name, description, StudyModule.class, perms);
    }

    @Override
    public boolean isApplicable(SecurityPolicy policy, SecurableResource resource)
    {
        return resource instanceof Container && branchContainsStudy((Container)resource);
    }

    private boolean branchContainsStudy(Container container)
    {
        if (null != StudyService.get().getStudy(container))
            return true;

        for (Container child : container.getChildren())
        {
            if (branchContainsStudy(child))
                return true;
        }

        return false;
    }
}
