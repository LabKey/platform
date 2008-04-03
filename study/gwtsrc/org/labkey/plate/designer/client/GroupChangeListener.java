package org.labkey.plate.designer.client;

import org.labkey.plate.designer.client.model.GWTWellGroup;

/**
 * User: brittp
 * Date: Feb 7, 2007
 * Time: 4:17:33 PM
 */
public interface GroupChangeListener
{
    void groupAdded(GWTWellGroup group);

    void groupRemoved(GWTWellGroup group);

    void activeGroupChanged(GWTWellGroup previouslyActive, GWTWellGroup currentlyActive);

    void activeGroupTypeChanged(String type);
}
