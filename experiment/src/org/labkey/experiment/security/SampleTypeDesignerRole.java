package org.labkey.experiment.security;

import org.labkey.api.security.permissions.DesignSampleTypePermission;
import org.labkey.api.security.roles.AbstractRole;
import org.labkey.experiment.ExperimentModule;

public class SampleTypeDesignerRole extends AbstractRole
{
    public SampleTypeDesignerRole()
    {
        super("Sample Type Designer", "Sample type designers can create and design new sample types or change existing ones.", ExperimentModule.class, DesignSampleTypePermission.class);

        excludeGuests();
    }
}
