package org.labkey.api.view;

import org.labkey.api.module.Module;
import org.labkey.api.data.Container;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Oct 16, 2008
 * Time: 10:21:49 AM
 */
public interface WebPartFactory
{
    String LOCATION_RIGHT = "right";

    String getName();

    String getDefaultLocation();

    WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception;

    HttpView getEditView(Portal.WebPart webPart);

    Portal.WebPart createWebPart();

    boolean isEditable();

    boolean showCustomizeOnInsert();

    Module getModule();

    void setModule(Module module);

    List<String> getLegacyNames();

    boolean isAvailable(Container c, String location);
}
