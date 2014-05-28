/*
 * Copyright (c) 2010-2014 LabKey Corporation
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
package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;

import java.util.Map;
import java.util.Set;

/**
* User: adam
* Date: Sep 14, 2010
* Time: 1:10:03 PM
*/
public class MultiValuedForeignKey implements ForeignKey
{
    private final ForeignKey _fk;
    private final String _junctionLookup;
    private final String _displayField;

    /**
     * @param fk the foreign key from the current column to its target in the junction table
     * @param junctionLookup the name of the column in the junction table that points to the table that has multiple
     * matches to this table
     */
    public MultiValuedForeignKey(ForeignKey fk, String junctionLookup)
    {
        this(fk, junctionLookup, null);
    }

    /**
     * @param fk the foreign key from the current column to its target in the junction table
     * @param junctionLookup the name of the column in the junction table that points to the table that has multiple
     * matches to this table
     * @param displayField the name of the column in the value table to use as the display field.
     */
    public MultiValuedForeignKey(ForeignKey fk, String junctionLookup, @Nullable String displayField)
    {
        _fk = fk;
        _junctionLookup = junctionLookup;
        _displayField = displayField;
    }

    public String getJunctionLookup()
    {
        return _junctionLookup;
    }

    /**
     * Get the junction join column as a LookupColumn with FieldKey relative to parent.
     */
    public ColumnInfo createJunctionLookupColumn(@NotNull ColumnInfo parent)
    {
        TableInfo junction = _fk.getLookupTableInfo();
        if (junction == null)
            return null;

        ColumnInfo junctionKey = _fk.createLookupColumn(parent, _junctionLookup);

        if (junctionKey == null)
            throw new IllegalStateException("Could not find column '" + _junctionLookup + "' on table " + junction);

        return junctionKey;
    }

    /**
     * Get the junction join column as a regular ColumnInfo with FieldKey relative to lookup table.
     */
    private ColumnInfo getJunctionColumn()
    {
        TableInfo junction = _fk.getLookupTableInfo();
        if (junction == null)
            return null;

        ColumnInfo junctionKey = junction.getColumn(_junctionLookup);

        if (junctionKey == null)
            throw new IllegalStateException("Could not find column '" + _junctionLookup + "' on table " + junction);

        return junctionKey;
    }

    @Override
    public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
    {
        TableInfo junction = _fk.getLookupTableInfo();
        if (junction == null)
            return null;

        ColumnInfo junctionKey = junction.getColumn(_junctionLookup);       // Junction join to value table
        ColumnInfo childKey = junction.getColumn(getLookupColumnName());    // Junction join to primary table
        if (junctionKey == null)
        {
            throw new IllegalStateException("Could not find column '" + _junctionLookup + "' on table " + junction);
        }
        ForeignKey fk = junctionKey.getFk();                                // Wrapped foreign key to value table (elided lookup)

        // Default display field on the lookup table
        if (displayField == null)
        {
            displayField = _displayField;
        }

        ColumnInfo lookupColumn = fk.createLookupColumn(junctionKey, displayField);

        if (lookupColumn == null)
        {
            return null;
        }

        // Pass the container filter through the lookup
        TableInfo lookupTable = lookupColumn.getParentTable();
        if (parent.getParentTable() != null && parent.getParentTable().supportsContainerFilter() && lookupTable != null && lookupTable.supportsContainerFilter())
        {
            ContainerFilterable table = (ContainerFilterable) lookupTable;
            if (table.hasDefaultContainerFilter())
            {
                table.setContainerFilter(new DelegatingContainerFilter(parent.getParentTable(), true));
            }
        }

        if (lookupColumn.getURL() instanceof StringExpressionFactory.FieldKeyStringExpression)
        {
            // We need to strip off the junction table's contribution to the FieldKey in the URL since we don't
            // expose the junction table itself as part of the Query tree of tables and columns
            StringExpressionFactory.FieldKeyStringExpression url = (StringExpressionFactory.FieldKeyStringExpression)lookupColumn.getURL();
            if (url != AbstractTableInfo.LINK_DISABLER)
                url = url.dropParent(junctionKey.getName());
            lookupColumn.setURL(url);
        }

        return createMultiValuedLookupColumn(lookupColumn, parent, childKey, junctionKey, fk);
    }


    // Give subclasses a chance to alter these parameters before MVLC construction
    protected MultiValuedLookupColumn createMultiValuedLookupColumn(ColumnInfo lookupColumn, ColumnInfo parent, ColumnInfo childKey, ColumnInfo junctionKey, ForeignKey fk)
    {
        return new MultiValuedLookupColumn(lookupColumn.getFieldKey(), parent, childKey, junctionKey, fk, lookupColumn);
    }


    @Override
    public TableInfo getLookupTableInfo()
    {
        ColumnInfo junctionColumn = getJunctionColumn();
        if (junctionColumn != null)
        {
            ForeignKey junctionFK = junctionColumn.getFk();
            if (junctionFK != null)
            {
                return junctionFK.getLookupTableInfo();
            }
        }
        return null;
    }

    @Override
    public StringExpression getURL(ColumnInfo parent)
    {
        return _fk.getURL(parent);
    }

    @Override
    public NamedObjectList getSelectList(RenderContext ctx)
    {
        ColumnInfo junctionColumn = getJunctionColumn();
        if (junctionColumn != null)
        {
            ForeignKey junctionFK = junctionColumn.getFk();
            if (junctionFK != null)
            {
                return junctionFK.getSelectList(ctx);
            }
        }
        return null;
    }

    @Override
    public Container getLookupContainer()
    {
        return _fk.getLookupContainer();
    }

    @Override
    public String getLookupTableName()
    {
        return _fk.getLookupTableName();
    }

    @Override
    public String getLookupSchemaName()
    {
        return _fk.getLookupSchemaName();
    }

    @Override
    public String getLookupColumnName()
    {
        return _fk.getLookupColumnName();
    }

    @Override
    public String getLookupDisplayName()
    {
        return _displayField;
    }

    @Override
    public ForeignKey remapFieldKeys(FieldKey parent, Map<FieldKey, FieldKey> mapping)
    {
        return new MultiValuedForeignKey(_fk.remapFieldKeys(null, mapping), _junctionLookup);
    }

    @Override
    public Set<FieldKey> getSuggestedColumns()
    {
        return _fk.getSuggestedColumns();
    }
}
