/*
 * Copyright (c) 2008-2012 LabKey Corporation
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

    // Set of additional FieldKeys that query should select to make the join successful.
    private Set<FieldKey> _suggestedFields;

    // Map original FieldKey to query's remapped FieldKey.
    private Map<FieldKey, FieldKey> _remappedFields;

    protected AbstractForeignKey()
    {
    }
    
    protected AbstractForeignKey(String tableName, String columnName)
    {
        this(tableName, columnName, null);
    }

    protected AbstractForeignKey(String tableName, String columnName, String schemaName)
    {
        _tableName = tableName;
        _columnName = columnName;
        _lookupSchemaName = schemaName;
    }

    public String getLookupSchemaName()
    {
        return _lookupSchemaName;
    }

    public void setLookupSchemaName(String lookupSchemaName)
    {
        _lookupSchemaName = lookupSchemaName;
    }

    public Container getLookupContainer()
    {
        return null;
    }

    protected void setTableName(String name)
    {
        this._tableName = name;
    }

    public String getLookupTableName()
    {
        if (_tableName == null)
        {
            initTableAndColumnNames();
        }
        return _tableName;
    }

    protected void setColumnName(String columnName)
    {
//        if (_columnName == null)
//        {
//            initTableAndColumnNames();
//        }
        _columnName = columnName;
    }

    public String getLookupColumnName()
    {
        if (_columnName == null)
        {
            initTableAndColumnNames();
        }
        return _columnName;
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

    public NamedObjectList getSelectList()
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
        assert _remappedFields == null : "Already remapped ForeignKey.  If we hit this we need to 'compose' the field mappings";

        Set<FieldKey> suggested = getSuggestedColumns();
        if (suggested == null || suggested.isEmpty())
            return this;

        // Create a subset of the FieldKey mapping for only the suggested columns
        Map<FieldKey, FieldKey> remappedSuggested = new HashMap<FieldKey, FieldKey>(suggested.size());
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
            return cloned;
        }
        catch (CloneNotSupportedException e)
        {
            assert false : "Silly programmer, clone not supported for " + this.getClass().getName();
            e.printStackTrace();
            return null;
        }
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
            _suggestedFields = new HashSet<FieldKey>();
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
