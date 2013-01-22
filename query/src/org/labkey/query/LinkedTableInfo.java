/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
package org.labkey.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.UserSchema;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.queryCustomView.FilterType;
import org.labkey.data.xml.queryCustomView.LocalOrRefFiltersType;
import org.labkey.data.xml.queryCustomView.NamedFiltersType;

import java.util.Collection;

/**
* User: kevink
* Date: 12/10/12
*/
public class LinkedTableInfo extends SimpleUserSchema.SimpleTable<UserSchema>
{
    public LinkedTableInfo(UserSchema schema, TableInfo table)
    {
        super(schema, table);
    }

    @Override
    protected void addTableURLs()
    {
        // Disallow all table URLs
        setGridURL(LINK_DISABLER);
        setDetailsURL(LINK_DISABLER);
        setImportURL(LINK_DISABLER);
        setInsertURL(LINK_DISABLER);
        setUpdateURL(LINK_DISABLER);
        setDeleteURL(LINK_DISABLER);
    }

    @Override
    protected void fixupWrappedColumn(ColumnInfo wrap, ColumnInfo col)
    {
        super.fixupWrappedColumn(wrap, col);

        // Remove FK and URL. LinkedTableInfo doesn't include FKs or URLs.
        wrap.setFk(null);
        wrap.setURL(LINK_DISABLER);
    }

    @Override
    protected void addDomainColumns()
    {
        // LinkedTableInfos only adds columns from the source table and has no Domain columns.
    }

    private static final NamedFiltersType[] NO_FILTERS = new NamedFiltersType[0];

    @Override
    protected void loadAllButCustomizerFromXML(QuerySchema schema, TableType xmlTable, @Nullable NamedFiltersType[] filtersArray, Collection<QueryException> errors)
    {
        filtersArray = filtersArray == null ? NO_FILTERS : filtersArray;

        if (xmlTable.isSetFilters())
        {
            LocalOrRefFiltersType xmlFilters = xmlTable.getFilters();
            if (xmlFilters.isSetRef())
            {
                String refId = xmlFilters.getRef();
                if (!findMatchingFilters(filtersArray, refId))
                {
                    errors.add(new QueryException("Could not find filter with id '" + refId + "'"));
                }
            }

            addFilters(xmlFilters.getFilterArray());
        }
    }

    /** @return true if match was found, false if it's not in the array */
    private boolean findMatchingFilters(NamedFiltersType[] filtersArray, String refId)
    {
        for (NamedFiltersType namedFiltersType : filtersArray)
        {
            if (namedFiltersType.getName().equals(refId))
            {
                addFilters(namedFiltersType.getFilterArray());
                return true;
            }
        }
        return false;
    }

    private void addFilters(FilterType[] xmlFilters)
    {
        for (FilterType xmlFilter : xmlFilters)
        {
            SimpleFilter filter = null;
            for (CompareType compareType : CompareType.values())
            {
                if (compareType.getXmlType().equals(xmlFilter.getOperator()))
                {
                    filter = new SimpleFilter(FieldKey.fromString(xmlFilter.getColumn()), xmlFilter.getValue(), compareType);
                    break;
                }
            }
            if (filter == null)
            {
                throw new IllegalArgumentException("Unsupported filter type: " + xmlFilter.getOperator());
            }
            addCondition(filter);
        }
    }
}
