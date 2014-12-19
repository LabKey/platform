package org.labkey.api.security.permissions;

import org.labkey.api.security.permissions.AbstractPermission;

/**
 * Created by Marty on 12/18/2014.
 */
public class QualityAssurancePermission extends AbstractPermission
{
    public QualityAssurancePermission()
    {
        super("Quality Assurance", "May perform quality assurance operations.");
    }
}