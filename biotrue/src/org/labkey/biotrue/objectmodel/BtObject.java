package org.labkey.biotrue.objectmodel;

import org.labkey.api.data.Container;
import org.labkey.api.view.ActionURL;
import org.labkey.biotrue.controllers.BtController;

abstract public class BtObject
{
    abstract public Container getContainer();
    abstract public ActionURL detailsURL();
    abstract public ActionURL urlFor(BtController.Action action);
    abstract public String getLabel();
}
