package org.labkey.plate.designer.client;

import org.labkey.plate.designer.client.model.GWTWellGroup;

/**
 * User: jeckels
 * Date: Apr 19, 2007
 */
public class WellGroupPropertyPanel extends PropertyPanel implements GroupChangeListener
{
    public WellGroupPropertyPanel(TemplateView view)
    {
        super(view);
        view.addGroupListener(this);
        activeGroupChanged(null, null);
    }

    public void activeGroupChanged(GWTWellGroup previouslyActive, GWTWellGroup currentlyActive)
    {
        if (previouslyActive != null)
        {
            previouslyActive.setProperties(getProperties());
        }
        if (currentlyActive == null)
        {
            redraw("No well group selected.");
        }
        else
        {
            redraw(currentlyActive.getProperties());
        }
    }

    public void activeGroupTypeChanged(String type)
    {
    }

    public void groupAdded(GWTWellGroup group)
    {
    }

    public void groupRemoved(GWTWellGroup group)
    {
    }

}
