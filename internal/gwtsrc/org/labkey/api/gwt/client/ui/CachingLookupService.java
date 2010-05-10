package org.labkey.api.gwt.client.ui;

import com.google.gwt.user.client.rpc.AsyncCallback;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
* Created by IntelliJ IDEA.
* User: matthewb
* Date: Apr 27, 2010
* Time: 9:28:41 AM
* To change this template use File | Settings | File Templates.
*/
class CachingLookupService implements LookupServiceAsync
{
    final LookupServiceAsync _impl;

    CachingLookupService(LookupServiceAsync i)
    {
        _impl = i;
    }


    List<String> _containers = null;

    public void getContainers(final AsyncCallback<List<String>> async)
    {
        if (null != _containers)
        {
            async.onSuccess(_containers);
            return;
        }
        _impl.getContainers(new AsyncCallback<List<String>>()
        {
            public void onFailure(Throwable caught)
            {
                async.onFailure(caught);
            }

            public void onSuccess(List<String> result)
            {
                _containers = result;
                async.onSuccess(result);
            }
        });
    }


    Map<String,List<String>> schemas = new HashMap<String,List<String>>();

    public void getSchemas(final String containerId, final AsyncCallback<List<String>> async)
    {
        List<String> result = schemas.get(containerId);
        if (null != result)
        {
            async.onSuccess(result);
            return;
        }
        _impl.getSchemas(containerId, new AsyncCallback<List<String>>()
        {
            public void onFailure(Throwable caught)
            {
                async.onFailure(caught);
            }

            public void onSuccess(List<String> result)
            {
                schemas.put(containerId, result);
                async.onSuccess(result);
            }
        });
    }


    Map<String,Map<String, GWTPropertyDescriptor>> tables = new HashMap<String,Map<String, GWTPropertyDescriptor>>();

    public void getTablesForLookup(final String containerId, final String schemaName, final AsyncCallback<Map<String, GWTPropertyDescriptor>> async)
    {
        Map<String, GWTPropertyDescriptor> result = tables.get(containerId + "||" + schemaName);
        if (null != result)
        {
            async.onSuccess(result);
            return;
        }
        _impl.getTablesForLookup(containerId, schemaName, new AsyncCallback<Map<String,GWTPropertyDescriptor> >()
        {
            public void onFailure(Throwable caught)
            {
                async.onFailure(caught);
            }

            public void onSuccess(Map<String,GWTPropertyDescriptor> result)
            {
                tables.put(containerId + "||" + schemaName, result);
                async.onSuccess(result);
            }
        });
    }


    public Map<String, GWTPropertyDescriptor> getTablesForLookupCached(final String containerId, final String schemaName)
    {                                                                
        Map<String, GWTPropertyDescriptor> result = tables.get(containerId + "||" + schemaName);
        return result;
    }
}
