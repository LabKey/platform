package org.labkey.api.ldk.test;

import org.junit.Assert;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.User;
import org.labkey.api.util.TestContext;

public class AbstractIntegrationTest extends Assert
{
    protected static void doInitialSetUp(String projectName)
    {
        //pre-clean
        doCleanup(projectName);

        Container project = ContainerManager.getForPath(projectName);
        if (project == null)
        {
            ContainerManager.createContainer(ContainerManager.getRoot(), projectName);
        }
    }

    protected static void doCleanup(String projectName)
    {
        Container project = ContainerManager.getForPath(projectName);
        if (project != null)
        {
            ContainerManager.delete(project, getUser());
        }
    }

    protected static User getUser()
    {
        return TestContext.get().getUser();
    }
}
