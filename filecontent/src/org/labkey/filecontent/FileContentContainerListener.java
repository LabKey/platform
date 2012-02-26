package org.labkey.filecontent;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.User;

import java.beans.PropertyChangeEvent;

/**
 * User: adam
 * Date: 2/25/12
 * Time: 8:44 PM
 */
public class FileContentContainerListener implements ContainerManager.ContainerListener
{
    @Override
    public void containerCreated(Container c, User user)
    {
    }

    @Override
    public void containerDeleted(Container c, User user)
    {
        FileRootManager.get().deleteFileRoot(c);
    }

    @Override
    public void containerMoved(Container c, Container oldParent, User user)
    {
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt)
    {
    }
}
