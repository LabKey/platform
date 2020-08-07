/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.api.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.settings.ConceptURIProperties;

import java.util.Objects;

public class PdLookupForeignKey
{
    static public ForeignKey create(QuerySchema sourceSchema, @NotNull PropertyDescriptor pd)
    {
        return builder(sourceSchema,null, pd).build();
    }

    static public ForeignKey create(QuerySchema sourceSchema, ContainerFilter cf, @NotNull PropertyDescriptor pd)
    {
        return builder(sourceSchema, cf, pd).build();
    }

    static public QueryForeignKey.Builder builder(QuerySchema sourceSchema, ContainerFilter cf, @NotNull PropertyDescriptor pd)
    {
        Container targetContainer = pd.getLookupContainer() == null ? null : ContainerManager.getForId(pd.getLookupContainer());
        String lookupSchemaName = pd.getLookupSchema();
        String lookupQuery = pd.getLookupQuery();

        // check for conceptURI if the lookup container/schema/query are not already specified
        if (pd.getConceptURI() != null && targetContainer == null && lookupSchemaName == null && lookupQuery == null)
        {
            Lookup lookup = ConceptURIProperties.getLookup(pd.getContainer(), pd.getConceptURI());
            if (lookup != null)
            {
                targetContainer = lookup.getContainer();
                lookupSchemaName = lookup.getSchemaName();
                lookupQuery = lookup.getQueryName();
            }
        }

        // SAMPLE LOOKUP via String special case
        // TODO: move to QueryForeignKey, requires knowing column type of parent column
        String keyColumnName = null;
        boolean isLabKeyScope = null != sourceSchema && (null == sourceSchema.getDbSchema() || sourceSchema.getDbSchema().getScope().isLabKeyScope());
        boolean isSampleSchema = isLabKeyScope && ExpSchema.SCHEMA_NAME.equalsIgnoreCase(lookupSchemaName);
        if (isSampleSchema && pd.getJdbcType().isText())
        {
            keyColumnName = "Name";
        }

        // The container that owns the property descriptor might be different than the current container.
        // If we can't find the targeted table in the current container, then look in the definition container.
        // This is useful for finding lists and other single-container tables
        //
        // NOTE: I would rather we made this explicit in the definition of the FK, but this is where we are for now for backward compatibility
        if (null == targetContainer && !sourceSchema.getContainer().equals(pd.getContainer()))
        {
            QuerySchema targetSchema = sourceSchema.getSchema(lookupSchemaName);
            if (null == targetSchema || !targetSchema.getTableNames().contains(lookupQuery))
                targetContainer = pd.getContainer();
        }

        var builder = QueryForeignKey.from(sourceSchema, cf)
                .schema(lookupSchemaName, targetContainer)
                .to(lookupQuery, keyColumnName, null).container(targetContainer);
        return builder;
    }
}
