package org.labkey.api.action;

import org.labkey.api.view.ViewContext;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: May 21, 2007
 * Time: 3:45:43 PM
 */
public interface HasViewContext
{
    void setViewContext(ViewContext context);
    ViewContext getViewContext();
}
