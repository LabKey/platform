package org.labkey.api.view;

import org.labkey.api.data.Container;
import org.labkey.api.security.ACL;

import javax.servlet.ServletException;

/**
 * User: jeckels
 * Date: Nov 22, 2005
 */
public abstract class AbstractCustomizeWebPartView<ModelBean> extends GroovyView<ModelBean>
{
    public AbstractCustomizeWebPartView(String templateName)
    {
        super(templateName);
    }

    @Override
    public void prepareWebPart(ModelBean model) throws ServletException
    {
        super.prepareWebPart(model);
        Container c = getViewContext().getContainer(ACL.PERM_UPDATE);
        addObject("postURL", ActionURL.toPathString("Project", "customizeWebPart", c.getPath()));
        Portal.WebPart webPart = (Portal.WebPart) getViewContext().get("webPart");
        if (null != webPart)
            setTitle("Customize " + webPart.getName() + " Web Part");
    }
}
