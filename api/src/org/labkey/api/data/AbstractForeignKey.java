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
package org.labkey.api.data;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User: kevink
 * Date: Nov 29, 2007 5:30:26 PM
 */
public abstract class AbstractForeignKey implements ForeignKey, Cloneable
{
    protected String _lookupSchemaName;
    protected String _tableName;
    protected String _columnName;
    protected String _displayColumnName;

    // Set of additional FieldKeys that query should select to make the join successful.
    private Set<FieldKey> _suggestedFields;

    // Map original FieldKey to query's remapped FieldKey.
    private Map<FieldKey, FieldKey> _remappedFields;
    private StackTraceElement[] _remappedStackTrace;

    protected AbstractForeignKey()
    {
    }
    
    protected AbstractForeignKey(String tableName, String columnName)
    {
        this(null, tableName, columnName);
    }

    protected AbstractForeignKey(@Nullable String schemaName, String tableName, @Nullable String columnName)
    {
        this(schemaName, tableName, columnName, null);
    }

    protected AbstractForeignKey(@Nullable String schemaName, String tableName, @Nullable String columnName, @Nullable String displayColumnName)
    {
        _lookupSchemaName = schemaName;
        _tableName = tableName;
        _columnName = columnName;
        _displayColumnName = displayColumnName;
    }

    @Override
    public String getLookupSchemaName()
    {
        return _lookupSchemaName;
    }

    @Override
    public Container getLookupContainer()
    {
        return null;
    }

    @Override
    public String getLookupTableName()
    {
        if (_tableName == null)
        {
            initTableAndColumnNames();
        }
        return _tableName;
    }

    @Override
    public String getLookupColumnName()
    {
        if (_columnName == null)
        {
            initTableAndColumnNames();
        }
        return _columnName;
    }

    @Override
    public String getLookupDisplayName()
    {
        return _displayColumnName;
    }

    private boolean _initNames = false;

    protected void initTableAndColumnNames()
    {
        if (!_initNames)
        {
            _initNames = true;
            TableInfo table = getLookupTableInfo();
            if (table != null)
            {
                if (_lookupSchemaName == null)
                {
                    _lookupSchemaName = table.getPublicSchemaName();
                }

                if (_tableName == null)
                {
                    _tableName = table.getPublicName();
                    if (_tableName == null)
                        _tableName = table.getName();
                }

                if (_columnName == null)
                {
                    List<String> pkColumns = table.getPkColumnNames();
                    if (pkColumns != null && pkColumns.size() > 0)
                        _columnName = pkColumns.get(0);
                }
            }
        }
    }

    @Override
    public NamedObjectList getSelectList(RenderContext ctx)
    {
        TableInfo lookupTable = getLookupTableInfo();
        if (lookupTable == null)
            return new NamedObjectList();

        return lookupTable.getSelectList(getLookupColumnName());
    }


    @Override
    public ForeignKey remapFieldKeys(@Nullable FieldKey parent, @Nullable Map<FieldKey, FieldKey> mapping)
    {
        boolean assertsEnabled = false;
        assert assertsEnabled = true;

        Set<FieldKey> suggested = getSuggestedColumns();
        if (suggested == null || suggested.isEmpty())
            return this;

        // Create a subset of the FieldKey mapping for only the suggested columns
        Map<FieldKey, FieldKey> remappedSuggested = new HashMap<>(suggested.size());
        boolean identityMapping = true;
        for (FieldKey originalField : suggested)
        {
            // Check if the field has already be remapped
            FieldKey field = getRemappedField(originalField);
            FieldKey remappedField = mapping == null ? field : mapping.get(field);
            if (remappedField == null)
                return null;
            remappedSuggested.put(originalField, remappedField);
            identityMapping &= field.equals(remappedField);
        }
        if (identityMapping)
            return this;

        try
        {
            AbstractForeignKey cloned = (AbstractForeignKey)this.clone();
            cloned._remappedFields = remappedSuggested;
            if (assertsEnabled)
                cloned._remappedStackTrace = Thread.currentThread().getStackTrace();
            return cloned;
        }
        catch (CloneNotSupportedException e)
        {
            assert false : "Silly programmer, clone not supported for " + this.getClass().getName();
            Logger.getLogger(AbstractForeignKey.class).error(e);
            return null;
        }
    }

    private String appendStackTrace(StackTraceElement[] ste, int count)
    {
        int i=1;  // Always skip getStackTrace() call
        for ( ; i<ste.length ; i++)
        {
            String line = ste[i].toString();
            if (!(line.startsWith("org.labkey.api.data.") || line.startsWith("java.lang.Thread")))
                break;
        }
        StringBuilder sb = new StringBuilder();
        int last = Math.min(ste.length,i+count);
        for ( ; i<last ; i++)
        {
            String line = ste[i].toString();
            if (line.startsWith("javax.servlet.http.HttpServlet.service("))
                break;
            sb.append("\n    ").append(line);
        }
        return sb.toString();
    }


    // Get the possibly remapped FieldKey.
    // Look here for the corrected FieldKey name when trying to resolve columns on the foreign key's parent table.
    protected FieldKey getRemappedField(FieldKey originalFieldKey)
    {
        if (_remappedFields == null || _remappedFields.isEmpty())
            return originalFieldKey;

        FieldKey remappedFieldKey = _remappedFields.get(originalFieldKey);
        if (remappedFieldKey != null)
            return remappedFieldKey;

        return originalFieldKey;
    }

    // Indicate that the FieldKey should be included in the select list of the foreign key parent table.
    public void addSuggested(FieldKey fieldKey)
    {
        if (_suggestedFields == null)
            _suggestedFields = new HashSet<>();
        _suggestedFields.add(fieldKey);
    }

    @Override
    public Set<FieldKey> getSuggestedColumns()
    {
        if (_suggestedFields == null)
            return null;

        if (null == _remappedFields)
            return Collections.unmodifiableSet(_suggestedFields);

        return _suggestedFields.stream()
            .map(this::getRemappedField)
            .collect(Collectors.toSet());
    }

    /**
     * Check if an alternate key can be used when importing a value for this lookup.
     * The lookup table must meet the following requirements:
     * - Has a single primary key
     * - Has a unique index over a single column that isn't the primary key
     * - The column in the unique index must be a string type
     */
    @Override
    public boolean allowImportByAlternateKey()
    {
        TableInfo lookupTable = getLookupTableInfo();
        if (lookupTable == null)
            return false;

        List<ColumnInfo> pkCols = lookupTable.getPkColumns();
        if (pkCols.size() != 1)
            return false;

        ColumnInfo pkCol = pkCols.get(0);

        List<List<ColumnInfo>> candidates = new ArrayList<>();
        for (Pair<TableInfo.IndexType, List<ColumnInfo>> index : lookupTable.getIndices().values())
        {
            if (index.getKey() != TableInfo.IndexType.Unique)
                continue;

            if (index.getValue().size() != 1)
                continue;

            ColumnInfo col = index.getValue().get(0);
            if (pkCol == col)
                continue;

            if (!col.getJdbcType().isText())
                continue;

            candidates.add(index.getValue());
        }

        return !candidates.isEmpty();
    }

}
