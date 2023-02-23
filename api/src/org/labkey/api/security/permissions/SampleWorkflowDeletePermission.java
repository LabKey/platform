package org.labkey.api.security.permissions;

public class SampleWorkflowDeletePermission extends AbstractPermission
{
    public SampleWorkflowDeletePermission()
    {
        super("Sample Workflow Delete", "Can delete sample workflow jobs, tasks, and attachments.");
    }
}
