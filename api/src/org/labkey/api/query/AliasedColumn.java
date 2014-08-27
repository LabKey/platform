/*
 * Copyright (c) 2006-2014 LabKey Corporation
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

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.util.ContainerContext;

import java.util.HashMap;
import java.util.Map;

public class AliasedColumn extends ColumnInfo
{
    protected ColumnInfo _column;

    /** Usually defer to the column we're aliasing for its concept URI, but allow explicit overrides */
    private String _overriddenConceptURI;

    public AliasedColumn(TableInfo parent, FieldKey key, ColumnInfo column, boolean forceKeepLabel)
    {
        super(key, parent);
        copyAttributesFrom(column);

        Map<FieldKey, FieldKey> remap = new HashMap<>();
        remap.put(column.getFieldKey(), key);
        if (parent != null && parent != column.getParentTable())
        {
            // SchemaTableInfo and the wrapping AbstractTableInfo may have different container context (e.g, "Container" versus "Folder" in Pipeline's Job table)
            ContainerContext originalCC = column.getParentTable().getContainerContext();
            ContainerContext parentCC = parent.getContainerContext();
            if (originalCC instanceof ContainerContext.FieldKeyContext && parentCC instanceof ContainerContext.FieldKeyContext)
            {
                remap.put(((ContainerContext.FieldKeyContext)originalCC).getFieldKey(), ((ContainerContext.FieldKeyContext)parentCC).getFieldKey());
            }
        }
        copyURLFrom(column, null, remap);

        if (!forceKeepLabel && !key.getName().equalsIgnoreCase(column.getFieldKey().getName()))
            setLabel(null);

        _column = column;
        metaDataName = column.getMetaDataName();
        selectName = column.getSelectName();
    }

    public AliasedColumn(TableInfo parent, String name, ColumnInfo column)
    {
        this(parent, new FieldKey(null,name), column, false);
    }

    public AliasedColumn(String name, ColumnInfo column)
    {
        this(column.getParentTable(), name, column);
    }

    public SQLFragment getValueSql(String tableAlias)
    {
        return _column.getValueSql(tableAlias);
    }

    @Override
    public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
    {
        if (getParentTable() == _column.getParentTable())
            _column.declareJoins(parentAlias, map);
    }

    public ColumnInfo getColumn()
    {
        return _column;
    }

    @Override
    public String getTableAlias(String baseAlias)
    {
        return _column.getTableAlias(baseAlias);
    }

    @Override
    public void setConceptURI(String conceptURI)
    {
        super.setConceptURI(conceptURI);
        _overriddenConceptURI = conceptURI;
    }

    @Override
    public String getConceptURI()
    {
        return _overriddenConceptURI == null ? _column.getConceptURI() : _overriddenConceptURI;
    }
}
