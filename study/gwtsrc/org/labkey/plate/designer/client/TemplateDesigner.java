package org.labkey.plate.designer.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.user.client.ui.*;
import org.labkey.api.gwt.client.util.PropertyUtil;

/**
 * User: brittp
 * Date: Jan 30, 2007
 * Time: 1:59:01 PM
 */
public class TemplateDesigner implements EntryPoint
{
    public void onModuleLoad()
    {
        RootPanel panel = RootPanel.get("org.labkey.plate.designer.TemplateDesigner-Root");
        String templateName = PropertyUtil.getServerProperty("templateName");
        String assayTypeName = PropertyUtil.getServerProperty("assayTypeName");
        String templateTypeName = PropertyUtil.getServerProperty("templateTypeName");
        TemplateView view = new TemplateView(panel, templateName, assayTypeName, templateTypeName);
        view.showAsync();
    }
}
