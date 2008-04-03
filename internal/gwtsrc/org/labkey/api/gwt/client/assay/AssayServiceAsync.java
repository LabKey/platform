package org.labkey.api.gwt.client.assay;

import com.google.gwt.user.client.rpc.AsyncCallback;
import org.labkey.api.gwt.client.assay.model.GWTProtocol;
import org.labkey.api.gwt.client.model.GWTDomain;

/**
 * User: brittp
 * Date: June 20, 2007
 * Time: 2:37:25 PM
 */
public interface AssayServiceAsync
{
    void getAssayDefinition(int rowId, boolean copy, AsyncCallback async);

    void saveChanges(GWTProtocol plate, boolean replaceIfExisting, AsyncCallback async);

    void updateDomainDescriptor(GWTDomain orig, GWTDomain update, AsyncCallback async);

    // PropertiesEditor.LookupService
    void getContainers(AsyncCallback async);

    void getSchemas(String containerId, AsyncCallback async);

    void getTablesForLookup(String containerId, String schemaName, AsyncCallback async);

    void getAssayTemplate(String providerName, AsyncCallback asyncCallback);
}
