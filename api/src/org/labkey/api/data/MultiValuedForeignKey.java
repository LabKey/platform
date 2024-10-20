/*
 * Copyright (c) 2010-2019 LabKey Corporation
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
import org.labkey.api.query.SchemaKey;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;

import java.util.Map;
import java.util.Set;

/**
 * Implementation of {@link org.labkey.api.data.ForeignKey} that allows for the display of multiple values, instead
 * of the typical single-value lookup. Assumes that there's a mapping table that specifies the many-to-many
 * (or one-to-many) relationship.
 *
 * User: adam
 * Date: Sep 14, 2010
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
    // TODO ContainerFilter
    public MultiValuedForeignKey(ForeignKey fk, String junctionLookup)
    {
        this(fk, junctionLookup, null);
    }

    // TODO ContainerFilter
    public MultiValuedForeignKey(Builder<ForeignKey> fk, String junctionLookup)
    {
        this(fk.build(), junctionLookup, null);
    }

    /**
     * @param fk the foreign key from the current column to its target in the junction table
     * @param junctionLookup the name of the column in the junction table that points to the table that has multiple
     * matches to this table
     * @param displayField the name of the column in the value table to use as the display field.
     */
    // TODO ContainerFilter
    public MultiValuedForeignKey(ForeignKey fk, String junctionLookup, @Nullable String displayField)
    {
        _fk = fk;
        _junctionLookup = junctionLookup;
        _displayField = displayField;
    }

    /* this is to help subclasses implement remapFieldKeys() */
    // TODO ContainerFilter
    protected MultiValuedForeignKey(MultiValuedForeignKey source, FieldKey parent, Map<FieldKey, FieldKey> mapping)
    {
        _fk = source._fk.remapFieldKeys(parent, mapping);
        _junctionLookup = source._junctionLookup;
        _displayField = source._displayField;
    }

    //TODO ContainerFilter
    protected ContainerFilter getLookupContainerFilter()
    {
        return null;    // use default on target table
    }

    // TODO: better name?
    public ForeignKey getSourceFk()
    {
        return _fk;
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
        if (fk == null)
        {
            return null;
        }

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

// TODO ContainerFilter _fk should already be holding onto it's ContainerFilter, verify this is the case
//        // Pass the container filter through the lookup
//        TableInfo lookupTable = lookupColumn.getParentTable();
//        if (parent.getParentTable() != null && parent.getParentTable().supportsContainerFilter() && lookupTable != null && lookupTable.supportsContainerFilter())
//        {
//            ContainerFilterable table = (ContainerFilterable) lookupTable;
//            if (table.hasDefaultContainerFilter())
//            {
//                table.setContainerFilter(new DelegatingContainerFilter(parent.getParentTable(), true));
//            }
//        }

        if (lookupColumn.getURL() instanceof StringExpressionFactory.FieldKeyStringExpression url)
        {
            // We need to strip off the junction table's contribution to the FieldKey in the URL since we don't
            // expose the junction table itself as part of the Query tree of tables and columns
            if (url != AbstractTableInfo.LINK_DISABLER)
                url = url.dropParent(junctionKey.getName());
            ((MutableColumnInfo) lookupColumn).setURL(url);
        }

        return createMultiValuedLookupColumn(lookupColumn, parent, childKey, junctionKey, fk);
    }

    // Give subclasses a chance to alter these parameters before MVLC construction
    protected MultiValuedLookupColumn createMultiValuedLookupColumn(ColumnInfo lookupColumn, ColumnInfo parent, ColumnInfo childKey, ColumnInfo junctionKey, ForeignKey fk)
    {
        return new MultiValuedLookupColumn(parent, childKey, junctionKey, fk, lookupColumn);
    }

    @Nullable
    private ForeignKey getJunctionFK()
    {
        ColumnInfo junctionColumn = getJunctionColumn();
        if (junctionColumn == null)
            return null;
        return junctionColumn.getFk();
    }

    @Override
    @Nullable
    public TableInfo getLookupTableInfo()
    {
        ForeignKey junctionFK = getJunctionFK();
        if (junctionFK == null)
            return null;
        return junctionFK.getLookupTableInfo();
    }

    @Override
    @Nullable
    public StringExpression getURL(ColumnInfo parent)
    {
        ForeignKey junctionFK = getJunctionFK();
        if (junctionFK == null)
            return null;
        return junctionFK.getURL(parent);
    }

    @Override
    @NotNull
    public NamedObjectList getSelectList(RenderContext ctx)
    {
        ForeignKey junctionFK = getJunctionFK();
        if (junctionFK == null)
            return new NamedObjectList();
        return junctionFK.getSelectList(ctx);
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
    public SchemaKey getLookupSchemaKey()
    {
        return _fk.getLookupSchemaKey();
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
        return new MultiValuedForeignKey(this, parent, mapping);
    }

    @Override
    public Set<FieldKey> getSuggestedColumns()
    {
        return _fk.getSuggestedColumns();
    }

    // default is to render a multi-selection form input
    public boolean isMultiSelectInput()
    {
        return true;
    }

    @Override
    public boolean isShowAsPublicDependency()
    {
        return true;
    }
}
