/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.api.query.snapshot;

import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.QueryForm;
import org.labkey.api.data.DisplayColumn;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/*
 * User: Karl Lum
 * Date: Jul 8, 2008
 * Time: 1:57:12 PM
 */

public class QuerySnapshotService
{
    static private Map<String, I> _providers = new HashMap<String, I>();
    public static final String TYPE = "Query Snapshot";

    static public synchronized I get(String schema)
    {
        // todo: add the default provider
        return _providers.get(schema);
    }

    static public synchronized void registerProvider(String schema, I provider)
    {
        if (_providers.containsKey(schema))
            throw new IllegalStateException("A snapshot provider for schema :" + schema + " has already been registered");

        _providers.put(schema, provider);
    }

    public interface I
    {
        public String getName();
        public String getDescription();

        public ActionURL createSnapshot(QuerySnapshotForm form) throws Exception;

        public ActionURL updateSnapshot(QuerySnapshotForm form) throws Exception;

        public ActionURL updateSnapshotDefinition(ViewContext context, QuerySnapshotDefinition def) throws Exception;
        
        public ActionURL getCustomizeURL(ViewContext context);

        public List<DisplayColumn> getDisplayColumns(QueryForm queryForm) throws Exception;
    }
}