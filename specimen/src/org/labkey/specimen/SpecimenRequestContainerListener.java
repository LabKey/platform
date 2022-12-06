package org.labkey.specimen;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.User;

import java.beans.PropertyChangeEvent;
import java.util.Collection;
import java.util.Collections;

public class SpecimenRequestContainerListener implements ContainerManager.ContainerListener
{
    @Override
    public void containerCreated(Container c, User user)
    {
        SpecimenRequestManager.get().clearCaches(c);
    }

    @Override
    public void containerDeleted(Container c, User user)
    {
        SpecimenRequestManager.get().clearCaches(c);
    }

    @Override
    public void containerMoved(Container c, Container oldParent, User user)
    {
        SpecimenRequestManager.get().clearCaches(c);
    }

    @NotNull
    @Override
    public Collection<String> canMove(Container c, Container newParent, User user)
    {
        return Collections.emptyList();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt)
    {
        SpecimenRequestManager.get().clearCaches((Container)evt.getSource());
    }
}
