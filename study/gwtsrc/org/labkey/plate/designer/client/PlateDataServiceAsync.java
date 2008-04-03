package org.labkey.plate.designer.client;

import com.google.gwt.user.client.rpc.AsyncCallback;
import org.labkey.plate.designer.client.model.GWTPlate;

/**
 * User: brittp
 * Date: Jan 31, 2007
 * Time: 2:37:25 PM
 */
public interface PlateDataServiceAsync
{

    void getTemplateDefinition(String templateName, String assayTypeName, String templateTypeName, AsyncCallback async);

    void saveChanges(GWTPlate plate, boolean replaceIfExisting, AsyncCallback async);
}
