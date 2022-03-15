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
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.query.ExpMaterialInputTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;

import java.sql.Connection;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.labkey.api.data.UpdateableTableInfo.ObjectUriType.schemaColumn;

public abstract class ExpRunItemTableImpl<C extends Enum> extends ExpTableImpl<C> implements UpdateableTableInfo
{
    public static final String ALIAS_CONCEPT_URI = "http://www.labkey.org/exp/xml#alias";

    protected ExpRunItemTableImpl(String name, TableInfo rootTable, UserSchema schema, ContainerFilter cf)
    {
        super(name, rootTable, schema, cf);
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        if (!isAllowedPermission(perm))
            return false;
        if (_userSchema instanceof UserSchema.HasContextualRoles)
        {
            if (!getContainer().hasPermission(user, perm, ((UserSchema.HasContextualRoles)_userSchema).getContextualRoles()))
                return false;
        }
        else if (!getContainer().hasPermission(user, perm))
            return false;

        return perm.equals(ReadPermission.class) || canUserAccessPhi();
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
        aliasCol.setConceptURI(ALIAS_CONCEPT_URI);
        aliasCol.setPropertyURI(ALIAS_CONCEPT_URI);
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


    protected MutableColumnInfo createLineageColumn(ExpTableImpl table, String alias, boolean inputs, boolean asMultiValued)
    {
        var ret = table.wrapColumn(alias, table.getRealTable().getColumn("objectid"));
        if (asMultiValued) // use MultiValueForeignKey
            ret.setFk(LineageForeignKey.createWithMultiValuedColumn(table.getUserSchema(),new SQLFragment("SELECT objectid FROM ").append(table.getFromSQL("qq")), inputs));
        else // use LineageDisplayColumn
            ret.setFk(LineageForeignKey.createWithDisplayColumn(table.getUserSchema(), table, inputs));
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

    protected String getExpNameExpressionPreview(String schemaName, String queryName, User user)
    {
        String domainURI = PropertyService.get().getDomainURI(schemaName, queryName, getContainer(), user);
        Domain domain = PropertyService.get().getDomain(getContainer(), domainURI);
        if (domain != null && domain.getDomainKind() != null)
        {
            List<String> previews = domain.getDomainKind().getDomainNamePreviews(schemaName, queryName, getContainer(), user);
            if (previews != null && !previews.isEmpty())
               return previews.get(0);
        }

        return null;
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
