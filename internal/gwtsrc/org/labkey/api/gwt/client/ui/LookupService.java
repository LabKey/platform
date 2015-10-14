/*
 * Copyright (c) 2008-2015 LabKey Corporation
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

import com.google.gwt.user.client.rpc.IsSerializable;
import com.google.gwt.user.client.rpc.RemoteService;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;

import java.io.Serializable;
import java.util.List;

/**
 * User: jeckels
 * Date: Nov 13, 2008
 */
public interface LookupService extends RemoteService
{
    /**
     * @return list of container paths
     */
    List<String> getContainers();

    /**
     * @return list of schema names in the same scope as defaultLookupSchemaName
     */
    List<String> getSchemas(String containerId, String defaultLookupSchemaName);

    /**
     * @param containerId container
     * @param schemaName name of schema for query module
     * @return list of table name to pk column name, same table may appear more than once.
     */
    List<LookupTable> getTablesForLookup(String containerId, String schemaName);

    public static class LookupTable implements Comparable<LookupTable>, Serializable, IsSerializable
    {
        public String table;
        GWTPropertyDescriptor key;

        public LookupTable()
        {
            /* no-arg constructor required for IsSerializable to work */
        }

        public LookupTable(String t, GWTPropertyDescriptor pd)
        {
            this.table = t;
            this.key = pd;
        }

        public int compareTo(LookupTable o)
        {
            int c = String.CASE_INSENSITIVE_ORDER.compare(table, o.table);
            return 0!=c ? c : String.CASE_INSENSITIVE_ORDER.compare(this.key.getName(), o.key.getName());
        }
    }
}