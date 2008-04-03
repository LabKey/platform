package org.labkey.plate.designer.client;

import org.labkey.plate.designer.client.model.GWTWellGroup;

/**
 * User: brittp
 * Date: Feb 9, 2007
 * Time: 12:28:27 PM
 */
public abstract class GroupChangeListenerAdapter implements GroupChangeListener
{
    public void activeGroupChanged(GWTWellGroup previouslyActive, GWTWellGroup currentlyActive)
    {
    }

    public void groupAdded(GWTWellGroup group)
    {
    }

    public void groupRemoved(GWTWellGroup group)
    {
    }

    public void activeGroupTypeChanged(String type)
    {
    }
}
