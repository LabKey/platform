package org.labkey.api.security.permissions;

public class SampleWorkflowJobPermission extends AbstractPermission
{
    public SampleWorkflowJobPermission()
    {
        super("Sample Workflow Job", "Can read, create, update, and delete sample workflow jobs.");
    }
}
