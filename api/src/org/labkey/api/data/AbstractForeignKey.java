/*
 * Copyright (c) 2008-2013 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.query.FieldKey;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        NamedObjectList ret = new NamedObjectList();
        TableInfo lookupTable = getLookupTableInfo();
        if (lookupTable == null)
            return ret;

        return lookupTable.getSelectList(getLookupColumnName());
    }


    @Override
    public ForeignKey remapFieldKeys(FieldKey parent, Map<FieldKey, FieldKey> mapping)
    {
        boolean assertsEnabled = false;
        assert assertsEnabled = true;
        assert _remappedFields == null : "Already remapped ForeignKey.  If we hit this we need to 'compose' the field mappings.  Original remapping stacktrace: " + appendStackTrace(_remappedStackTrace, 30);

        Set<FieldKey> suggested = getSuggestedColumns();
        if (suggested == null || suggested.isEmpty())
            return this;

        // Create a subset of the FieldKey mapping for only the suggested columns
        Map<FieldKey, FieldKey> remappedSuggested = new HashMap<>(suggested.size());
        boolean identityMapping = true;
        for (FieldKey originalField : suggested)
        {
            FieldKey remappedField = mapping.get(originalField);
            if (remappedField == null)
                return null;
            remappedSuggested.put(originalField, remappedField);
            identityMapping &= originalField.equals(remappedField);
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
            e.printStackTrace();
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

        return Collections.unmodifiableSet(_suggestedFields);
    }
}
