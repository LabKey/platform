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

package org.labkey.xarassay;

import org.labkey.api.data.*;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MsAssaySchema extends UserSchema
{
    private static final String DATA_ROW_TABLE_NAME = "XarAssayDataRow";
    private final ExpProtocol _protocol;

    public MsAssaySchema(User user, Container container)
    {
        this(user, container, null);
    }

    public MsAssaySchema(User user, Container container, ExpProtocol protocol)
    {
        super("XarAssay", user, container, ExperimentService.get().getSchema());
        _protocol = protocol;
    }

    public Set<String> getTableNames()
    {
        return new HashSet<String>(Arrays.asList(DATA_ROW_TABLE_NAME));
    }

    public TableInfo createTable(String name, String alias)
    {
        for (ExpProtocol protocol : AssayService.get().getAssayProtocols(getContainer()))
        {
            AssayProvider provider = AssayService.get().getProvider(protocol);
            if (provider != null && provider instanceof XarAssayProvider)
            {
                if (DATA_ROW_TABLE_NAME.equalsIgnoreCase(name))
                {
                    return getDataRowTable(this, protocol, alias);
                }
            }
        }
        return super.getTable(name, alias);
    }

    public static TableInfo getDataRowTable(QuerySchema schema, ExpProtocol protocol, String alias)
    {
        return new MsFractionRunDataTable(schema, alias, protocol);
    }

    public static DbSchema getSchema()
    {
        return DbSchema.get("xarassay");
    }

    public static PropertyDescriptor[] getExistingDataProperties(ExpProtocol protocol) throws SQLException
    {
        String propPrefix = new Lsid(MsFractionDataHandler.FRACTION_PROPERTY_LSID_PREFIX, protocol.getName(), "").toString();
        SimpleFilter propertyFilter = new SimpleFilter();
        propertyFilter.addCondition("PropertyURI", propPrefix, CompareType.STARTS_WITH);

        return Table.select(OntologyManager.getTinfoPropertyDescriptor(), Table.ALL_COLUMNS,
                propertyFilter, null, PropertyDescriptor.class);
    }

}