package org.labkey.study.dataset.client;

import com.google.gwt.user.client.rpc.AsyncCallback;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.study.dataset.client.model.GWTDataset;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Apr 26, 2007
 * Time: 1:36:23 PM
 */
public interface DatasetServiceAsync // extends PropertiesEditorServiceAsync
{
    void getDataset(int id, AsyncCallback async);

    void getDomainDescriptor(String typeURI, String domainContainerId, AsyncCallback async);

    void getDomainDescriptor(String typeURI, AsyncCallback async);

    void updateDatasetDefinition(GWTDataset ds, GWTDomain orig, GWTDomain dd, AsyncCallback async);

    void getContainers(AsyncCallback async);

    void getSchemas(String containerId, AsyncCallback async);

    void getTablesForLookup(String containerId, String schemaName, AsyncCallback async);

    void updateDatasetDefinition(GWTDataset ds, GWTDomain orig, String schema, AsyncCallback async);
}
