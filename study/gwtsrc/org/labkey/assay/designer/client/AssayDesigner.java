package org.labkey.assay.designer.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.user.client.ui.RootPanel;
import org.labkey.api.gwt.client.util.PropertyUtil;
import org.labkey.api.gwt.client.assay.AssayDesignerMainPanel;

/**
 * User: brittp
 * Date: Jun 20, 2007
 * Time: 2:23:06 PM
 */
public class AssayDesigner implements EntryPoint
{
    public void onModuleLoad()
    {
        RootPanel panel = RootPanel.get("org.labkey.assay.designer.AssayDesigner-Root");
        if (panel != null)
        {
            String protocolIdStr = PropertyUtil.getServerProperty("protocolId");
            String providerName = PropertyUtil.getServerProperty("providerName");
            String copyStr = PropertyUtil.getServerProperty("copy");
            boolean copyAssay = copyStr != null && Boolean.TRUE.toString().equals(copyStr);
            AssayDesignerMainPanel view = new AssayDesignerMainPanel(panel, providerName, protocolIdStr != null ? new Integer(Integer.parseInt(protocolIdStr)) : null, copyAssay);
            view.showAsync();
        }
    }
}
