package org.labkey.api.security.permissions;

public class QCAnalystPermission extends AbstractSitePermission
{
    public QCAnalystPermission()
    {
        super("QC Analyst", "Can perform QC related tasks, but not manage QC configurations.");
    }
}
