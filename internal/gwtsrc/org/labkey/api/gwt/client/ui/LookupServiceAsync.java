package org.labkey.api.gwt.client.ui;

import com.google.gwt.user.client.rpc.AsyncCallback;

/**
 * I couldn't seems to get subclassing interfaces to work,
 * so just have the controller/designer wrap its service for
 * me
 */
public interface LookupServiceAsync
{
    public void /*Map*/ getContainers(AsyncCallback async);
    public void /*List*/ getSchemas(String containerId, AsyncCallback async);
    public void /*Map*/ getTablesForLookup(String containerId, String schemaName, AsyncCallback async);
}
