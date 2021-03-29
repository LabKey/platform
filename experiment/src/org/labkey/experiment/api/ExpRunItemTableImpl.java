/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
package org.labkey.experiment.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.MultiValuedForeignKey;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.ParameterMapStatement;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.exp.query.ExpMaterialInputTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;

import java.sql.Connection;
import java.util.Set;
import java.util.function.Supplier;

import static org.labkey.api.data.UpdateableTableInfo.ObjectUriType.schemaColumn;

public abstract class ExpRunItemTableImpl<C extends Enum> extends ExpTableImpl<C> implements UpdateableTableInfo
{
    protected ExpRunItemTableImpl(String name, TableInfo rootTable, UserSchema schema, ContainerFilter cf)
    {
        super(name, rootTable, schema, cf);
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return isAllowedPermission(perm) && getContainer().hasPermission(user, perm) && canUserAccessPhi();
    }

    protected MutableColumnInfo createAliasColumn(String alias, Supplier<TableInfo> aliasMapTable)
    {
        var aliasCol = wrapColumn("Alias", getRealTable().getColumn("LSID"));
        aliasCol.setDescription("Contains the list of aliases for this data object");
        aliasCol.setFk(new MultiValuedForeignKey(new LookupForeignKey("LSID")
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return aliasMapTable.get();
            }
        }, "Alias")
        {
            @Override
            public boolean isMultiSelectInput()
            {
                return false;
            }
        });
        aliasCol.setCalculated(false);
        aliasCol.setNullable(true);
        aliasCol.setRequired(false);
        aliasCol.setDisplayColumnFactory(new AliasDisplayColumnFactory());
        aliasCol.setConceptURI("http://www.labkey.org/exp/xml#alias");
        aliasCol.setPropertyURI("http://www.labkey.org/exp/xml#alias");
        return aliasCol;
    }

    /**
     * Create a column with a lookup to MaterialInput that is joined by materialId
     * and a targetProtocolApplication provided by the <code>protocolApplication</code> column.
     */
    protected MutableColumnInfo createEdgeColumn(String alias, C protocolAppColumn, ExpSchema.TableType lookupTable)
    {
        var col = wrapColumn(alias, _rootTable.getColumn("rowId"));
        LookupForeignKey fk = new LookupForeignKey()
        {
            @Override
            public @Nullable TableInfo getLookupTableInfo()
            {
                UserSchema schema = getUserSchema();
                ExpSchema expSchema = new ExpSchema(schema.getUser(), schema.getContainer());
                return expSchema.getTable(lookupTable);
            }
        };
        fk.addJoin(FieldKey.fromParts(protocolAppColumn.name()), ExpMaterialInputTable.Column.TargetProtocolApplication.name(), false);
        col.setFk(fk);
        col.setUserEditable(false);
        col.setReadOnly(true);
        col.setHidden(true);
        col.setAutoIncrement(false);
        col.setCalculated(true);
        return col;
    }


    protected MutableColumnInfo createLineageColumn(ExpTableImpl table, String alias, boolean inputs)
    {
        var ret = table.wrapColumn(alias, table.getRealTable().getColumn("objectid"));
        if (1==1) // use LineageDisplayColumn
            ret.setFk(LineageForeignKey.createWithDisplayColumn(table.getUserSchema(), table, inputs));
        else    // use MultiValueForeignKey
            ret.setFk(LineageForeignKey.createWithMultiValuedColumn(table.getUserSchema(),new SQLFragment("SELECT objectid FROM ").append(table.getFromSQL("qq")), inputs));
        ret.setCalculated(true);
        ret.setUserEditable(false);
        ret.setReadOnly(true);
        ret.setShownInDetailsView(false);
        ret.setShownInInsertView(false);
        ret.setShownInUpdateView(false);
        ret.setIsUnselectable(true);
        ret.setHidden(true);
        ret.setConceptURI("http://www.labkey.org/exp/xml#" + alias);
        ret.setPropertyURI("http://www.labkey.org/exp/xml#" + alias);
        return ret;
    }


    //
    // UpdateableTableInfo
    //

    @Override
    public boolean insertSupported()
    {
        return true;
    }

    @Override
    public boolean updateSupported()
    {
        return true;
    }

    @Override
    public boolean deleteSupported()
    {
        return true;
    }

    @Override
    public TableInfo getSchemaTableInfo()
    {
        TableInfo t = getRealTable();
        if (t instanceof FilteredTable)
            t = ((FilteredTable)t).getRealTable();
        return t;
    }

    @Override
    public ObjectUriType getObjectUriType()
    {
        return schemaColumn;
    }

    @Override
    public @Nullable String getObjectURIColumnName()
    {
        return "lsid";
    }

    @Override
    public @Nullable String getObjectIdColumnName()
    {
        return "objectid";
    }

    @Override
    public @Nullable CaseInsensitiveHashMap<String> remapSchemaColumns()
    {
        return null;
    }

    @Override
    public @Nullable CaseInsensitiveHashSet skipProperties()
    {
        return null;
    }

    @Override
    public DataIteratorBuilder persistRows(DataIteratorBuilder data, DataIteratorContext context)
    {
        return null;
    }

    @Override
    public ParameterMapStatement insertStatement(Connection conn, User user)
    {
        return null;
    }

    @Override
    public ParameterMapStatement updateStatement(Connection conn, User user, Set<String> columns)
    {
        return null;
    }

    @Override
    public ParameterMapStatement deleteStatement(Connection conn)
    {
        return null;
    }

    @Override
    public boolean isAlwaysInsertExpObject()
    {
        return true;
    }

    @Override
    public boolean supportTableRules()
    {
        return true;
    }
}
