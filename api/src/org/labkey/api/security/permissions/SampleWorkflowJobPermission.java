package org.labkey.api.security.permissions;

public class SampleWorkflowJobPermission extends AbstractPermission
{
    public SampleWorkflowJobPermission()
    {
        super("Sample Workflow Job", "Can read, create, and update sample workflow jobs, tasks, and attachments.");
    }
}
