/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.AbstractForeignKey;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilterable;
import org.labkey.api.data.DelegatingContainerFilter;
import org.labkey.api.data.LookupColumn;
import org.labkey.api.data.MultiValuedForeignKey;
import org.labkey.api.data.TableInfo;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.ActionURL;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

abstract public class LookupForeignKey extends AbstractForeignKey implements Cloneable
{
    private final ActionURL _baseURL;
    private final Object _param;
    private boolean _prefixColumnCaption = false;

    private final Map<FieldKey, Pair<String, Boolean>> _additionalJoins = new HashMap<>();

    protected LookupForeignKey(ActionURL baseURL, Object param, String schemaName, String tableName, String pkColumnName, String titleColumn)
    {
        super(schemaName, tableName, pkColumnName, titleColumn);
        _baseURL = baseURL;
        _param = param;
    }

    public LookupForeignKey(ActionURL baseURL, String paramName, String schemaName, String tableName, String pkColumnName, String titleColumn)
    {
        this(baseURL, (Object)paramName, schemaName, tableName, pkColumnName, titleColumn);
    }

    public LookupForeignKey(ActionURL baseURL, String paramName, String tableName, String pkColumnName, String titleColumn)
    {
        this(baseURL,  paramName, null, tableName, pkColumnName, titleColumn);
    }

    public LookupForeignKey(ActionURL baseURL, String paramName, String pkColumnName, String titleColumn)
    {
        this(baseURL, paramName, null, null, pkColumnName, titleColumn);
    }

    public LookupForeignKey(ActionURL baseURL, Enum paramName, String pkColumnName, String titleColumn)
    {
        this(baseURL, paramName, null, null, pkColumnName, titleColumn);
    }

    public LookupForeignKey(String tableName, @Nullable String pkColumnName, String titleColumn)
    {
         this(null, null, null, tableName, pkColumnName, titleColumn);
    }

    public LookupForeignKey(@Nullable String pkColumnName, @Nullable String titleColumn)
    {
         this(null, null, null, null, pkColumnName, titleColumn);
    }

    public LookupForeignKey(@Nullable String pkColumnName)
    {
        this(null, null, null, null, pkColumnName, null);
    }

    /** Use the table's (single) PK column as the lookup target */
    public LookupForeignKey()
    {
        this(null);
    }

    public void setPrefixColumnCaption(boolean prefix)
    {
        _prefixColumnCaption = prefix;
    }

    /** Adds an extra pair of columns to the join. This doesn't affect how the lookup is presented through query,
     * but does change the SQL that we generate for the join criteria */
    public void addJoin(FieldKey fkColumn, String lookupColumnName, boolean equalOrIsNull)
    {
        assert fkColumn.getParent() == null : "ForeignKey must belong to this table";
        addSuggested(fkColumn);
        _additionalJoins.put(fkColumn, Pair.of(lookupColumnName, equalOrIsNull));
    }

    public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
    {
        TableInfo table = getLookupTableInfo();
        if (table == null)
        {
            return null;
        }
        if (displayField == null)
        {
            displayField = _displayColumnName;
            if (displayField == null)
                displayField = table.getTitleColumn();
        }
        if (displayField == null)
            return null;

        propagateContainerFilter(parent, table);

        LookupColumn result = LookupColumn.create(parent, getPkColumn(table), table.getColumn(displayField), _prefixColumnCaption);
        if (result != null)
        {
            for (Map.Entry<FieldKey, Pair<String, Boolean>> entry : _additionalJoins.entrySet())
            {
                Pair<String, Boolean> pair = entry.getValue();
                ColumnInfo lookupColumn = table.getColumn(pair.first);
                assert lookupColumn != null : "Couldn't find additional lookup column of name '" + pair.first + "' in " + table;

                // Get the possibly remapped foreign key column
                FieldKey foreignKey = getRemappedField(entry.getKey());
                result.addJoin(foreignKey, lookupColumn, pair.second);
            }
        }

        return result;
    }

    /**
     * Override this method if the primary key of the lookup table does not really exist.
     */
    protected ColumnInfo getPkColumn(TableInfo table)
    {
        return table.getColumn(getLookupColumnName());
    }


    public StringExpression getURL(ColumnInfo parent)
    {
        return getURL(parent, false);
    }


    protected StringExpression getURL(ColumnInfo parent, boolean useDetailsURL)
    {
        if (null != _baseURL)
        {
            // CONSIDER: set ContainerContext in AbstractForeignKey.getURL() so all subclasses can benefit
            DetailsURL url = new DetailsURL(_baseURL, _param.toString(), parent.getFieldKey());
            setURLContainerContext(url, getLookupTableInfo(), parent);
            return url;
        }

        if (!useDetailsURL)
            return null;

        TableInfo lookupTable = getLookupTableInfo();
        if (lookupTable == null || getLookupColumnName() == null)
            return null;

        return getDetailsURL(parent, lookupTable, getLookupColumnName());
    }


    public static StringExpression getDetailsURL(ColumnInfo parent, TableInfo lookupTable, String columnName)
    {
        StringExpression expr = lookupTable.getDetailsURL(null, null);
        if (expr == AbstractTableInfo.LINK_DISABLER)
            return AbstractTableInfo.LINK_DISABLER;

        if (expr instanceof StringExpressionFactory.FieldKeyStringExpression)
        {
            StringExpressionFactory.FieldKeyStringExpression f = (StringExpressionFactory.FieldKeyStringExpression)expr;
            StringExpressionFactory.FieldKeyStringExpression rewrite;

            FieldKey columnKey = new FieldKey(null,columnName);
            Set<FieldKey> keys = Collections.singleton(columnKey);

            // If the URL only substitutes the PK we can rewrite as FK (does the DisplayColumn handle when the join fails?)
            // except if the parent's FK is a multi-value (in which case the parent ColumnInfo is an FK
            // to the junction table's key column while this URL is the url of the junction table's lookup value column)
            if (f.validateFieldKeys(keys) && !(parent.getFk() instanceof MultiValuedForeignKey))
                rewrite = f.remapFieldKeys(null, Collections.singletonMap(columnKey, parent.getFieldKey()));
            else
                rewrite = f.remapFieldKeys(parent.getFieldKey(), null);

            // CONSIDER: set ContainerContext in AbstractForeignKey.getURL() so all subclasses can benefit
            if (rewrite instanceof DetailsURL)
                setURLContainerContext((DetailsURL)rewrite, lookupTable, parent);

            return rewrite;
        }
        return null;
    }

    protected static DetailsURL setURLContainerContext(DetailsURL url, TableInfo lookupTable, ColumnInfo parent)
    {
        ContainerContext cc = lookupTable.getContainerContext();
        if (cc != null)
        {
            // XXX: Why would the DetailsURL not have a ContainerContext but the lookupTable does?
            // Usually, the DetailsURL has received a ContainerContext (set in AbstractTableInfo.getDetailsURL(...))
            // from the lookupTable and its FieldKeyContext has been fixed up.
            //if (!url.hasContainerContext())
            //    _log.warn("Table's DetailURL does not have a container context. Table: " + lookupTable.getPublicSchemaName() + "." + lookupTable.getName() + ", column: " + parent.getName());
            if (null != parent && cc instanceof ContainerContext.FieldKeyContext)
            {
                ContainerContext.FieldKeyContext fkc = (ContainerContext.FieldKeyContext)cc;
                cc = new ContainerContext.FieldKeyContext(FieldKey.fromParts(parent.getFieldKey(),fkc.getFieldKey()));
            }
            url.setContainerContext(cc, false);
        }
        return url;
    }

}
