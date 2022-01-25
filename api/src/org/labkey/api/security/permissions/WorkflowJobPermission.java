package org.labkey.api.security.permissions;

public class WorkflowJobPermission extends AbstractPermission
{
    public WorkflowJobPermission()
    {
        super("Workflow Job", "Can read, create, update, and delete workflow jobs.");
    }
}
