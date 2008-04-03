package org.labkey.plate.designer.client;

import com.google.gwt.user.client.rpc.RemoteService;
import com.google.gwt.user.client.rpc.SerializableException;
import org.labkey.plate.designer.client.model.GWTPlate;

/**
 * User: brittp
* Date: Jan 31, 2007
* Time: 2:37:12 PM
*/
public interface PlateDataService extends RemoteService
{
    GWTPlate getTemplateDefinition(String templateName, String assayTypeName, String templateTypeName) throws SerializableException;

    void saveChanges(GWTPlate plate, boolean replaceIfExisting) throws SerializableException;

}
