package org.labkey.api.query;

import java.util.Collection;

/**
* User: kevink
* Date: 4/17/13
*/
public interface CustomViewChangeListener
{
    void viewCreated(CustomView view);
    void viewChanged(CustomView view);
    void viewDeleted(CustomView view);
    Collection<String> viewDependents(CustomView view);
}
