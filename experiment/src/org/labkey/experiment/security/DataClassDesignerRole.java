package org.labkey.experiment.security;

import org.labkey.api.security.permissions.DesignDataClassPermission;
import org.labkey.api.security.roles.AbstractRole;
import org.labkey.experiment.ExperimentModule;

public class DataClassDesignerRole extends AbstractRole
{
    public DataClassDesignerRole()
    {
        super("Data Class Designer", "Data class designers an create and design new data classes or change existing ones", ExperimentModule.class, DesignDataClassPermission.class);

        excludeGuests();
    }
}
