package org.labkey.api.security.roles;

import org.labkey.api.security.permissions.QualityAssurancePermission;

/**
 * Created by Marty on 12/18/2014.
 */
public class QualityAssuranceRole extends AbstractArgosRole
{
    public QualityAssuranceRole()
    {
        super("Quality Improvement/Quality Assurance Selector", "May select the Quality Improvement/Quality " +
                        "Assurance role in the Argos application.",
                QualityAssurancePermission.class);
    }
}