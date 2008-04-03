package org.labkey.experiment.property.client;

import com.google.gwt.user.client.rpc.AsyncCallback;
import org.labkey.api.gwt.client.model.GWTDomain;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Apr 26, 2007
 * Time: 1:36:23 PM
 */
public interface PropertyServiceAsync // extends PropertiesEditorServiceAsync
{

    void updateDomainDescriptor(GWTDomain orig, GWTDomain dd, AsyncCallback async);

    void getDomainDescriptor(String typeURI, AsyncCallback async);

    // PropertiesEditor.LookupService
    void getContainers(AsyncCallback async);

    void getSchemas(String containerId, AsyncCallback async);

    void getTablesForLookup(String containerId, String schemaName, AsyncCallback async);
}
