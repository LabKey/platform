/*
 * Copyright (c) 2010-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.gwt.client.ui;

import com.google.gwt.user.client.rpc.AsyncCallback;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
* User: matthewb
* Date: Apr 27, 2010
* Time: 9:28:41 AM
*/
public class CachingLookupService implements LookupServiceAsync
{
    private final LookupServiceAsync _impl;

    public CachingLookupService(LookupServiceAsync i)
    {
        _impl = i;
    }


    private List<String> _containers = null;

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


    private final Map<String, List<String>> schemas = new HashMap<String, List<String>>();

    public void getSchemas(final String containerId, final String defaultLookupSchemaName, final AsyncCallback<List<String>> async)
    {
        List<String> result = schemas.get(containerId + "||" + defaultLookupSchemaName);
        if (null != result)
        {
            async.onSuccess(result);
            return;
        }
        _impl.getSchemas(containerId, defaultLookupSchemaName, new AsyncCallback<List<String>>()
        {
            public void onFailure(Throwable caught)
            {
                async.onFailure(caught);
            }

            public void onSuccess(List<String> result)
            {
                schemas.put(containerId + "||" + defaultLookupSchemaName, result);
                async.onSuccess(result);
            }
        });
    }


    private final Map<String, List<LookupService.LookupTable>> tables = new HashMap<String, List<LookupService.LookupTable>>();

    public void getTablesForLookup(final String containerId, final String schemaName, final AsyncCallback<List<LookupService.LookupTable>> async)
    {
        List<LookupService.LookupTable> result = tables.get(containerId + "||" + schemaName);
        if (null != result)
        {
            async.onSuccess(result);
            return;
        }
        _impl.getTablesForLookup(containerId, schemaName, new AsyncCallback<List<LookupService.LookupTable>>()
        {
            public void onFailure(Throwable caught)
            {
                async.onFailure(caught);
            }

            public void onSuccess(List<LookupService.LookupTable> result)
            {
                tables.put(containerId + "||" + schemaName, result);
                async.onSuccess(result);
            }
        });
    }


    public List<LookupService.LookupTable> getTablesForLookupCached(final String containerId, final String schemaName)
    {
        List<LookupService.LookupTable> result = tables.get(containerId + "||" + schemaName);
        return result;
    }
}
