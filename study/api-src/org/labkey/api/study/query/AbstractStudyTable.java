/*
 * Copyright (c) 2008-2026 LabKey Corporation
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
package org.labkey.api.study.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.column.BuiltInColumnTypes;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.specimen.SpecimenMigrationService;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.DemoMode;
import org.labkey.api.util.StringExpression;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public abstract class AbstractStudyTable<SchemaType extends AbstractStudyQuerySchema> extends FilteredTable<SchemaType>
{
    public AbstractStudyTable(SchemaType schema, TableInfo realTable, ContainerFilter cf)
    {
        this(schema, realTable, cf, false);
    }

    public AbstractStudyTable(SchemaType schema, TableInfo realTable, ContainerFilter cf, boolean includeSourceStudyData)
    {
        super(realTable, schema);

        Study study = schema.getStudy();
        if (includeSourceStudyData && null != study && !study.isDataspaceStudy())
        {
            boolean hasReaderRole = getContextualRoles().contains(RoleManager.getRole(ReaderRole.class));
            _setContainerFilter(new ContainerFilter.StudyAndSourceStudy(schema.getContainer(), schema.getUser(), hasReaderRole));
        }
        else if (null != cf && supportsContainerFilter())
            _setContainerFilter(cf);
        else
            _setContainerFilter(getDefaultContainerFilter());
    }

    protected MutableColumnInfo addWrapParticipantColumn(String rootTableColumnName)
    {
        final String subjectColName = StudyService.get().getSubjectColumnName(getContainer());
        final String subjectTableName = StudyService.get().getSubjectTableName(getContainer());

        var participantColumn =
                new AliasedColumn(this, subjectColName, _rootTable.getColumn(rootTableColumnName));
        LookupForeignKey lfk = new LookupForeignKey(subjectTableName, subjectColName, null)
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return _userSchema.getTable(subjectTableName);
            }

            @Override
            public StringExpression getURL(ColumnInfo parent)
            {
                TableInfo table = getLookupTableInfo();
                if (table == null)
                    return null;
                return LookupForeignKey.getDetailsURL(parent, table, _columnName);
            }
        };
        lfk.addJoin(FieldKey.fromParts("Container"), "Container", false);
        participantColumn.setFk(lfk);

        // Don't setKeyField. Use addQueryFieldKeys where needed

        if (DemoMode.isDemoMode(_userSchema.getContainer(), _userSchema.getUser()))
        {
            participantColumn.setDisplayColumnFactory(PtidObfuscatingDisplayColumn::new);
        }

        return addColumn(participantColumn);
    }


    @Override
    protected ColumnInfo resolveColumn(String name)
    {
        // The name of the subject ID column can now be customized by study.  Since there are some joins
        // from the assay side which join to cross-study data, we need a single column name that works
        // in all containers.  This is 'ParticipantId', since this was the column name before
        // customization was possible:
        if ("ParticipantId".equalsIgnoreCase(name))
        {
            String alt = StudyService.get().getSubjectColumnName(getContainer());
            if (!"ParticipantId".equalsIgnoreCase(alt))
                return getColumn(alt);
        }
        return super.resolveColumn(name);
    }


    protected MutableColumnInfo addWrapLocationColumn(String wrappedName, String rootTableColumnName)
    {
        var locationColumn = new AliasedColumn(this, wrappedName, _rootTable.getColumn(rootTableColumnName));
        locationColumn.setFk(new LookupForeignKey("RowId")
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return _userSchema.createLocationLookupTable(_userSchema.allowSetContainerFilter() ? getContainerFilter() : null);
            }
        });
        return addColumn(locationColumn);
    }

    protected MutableColumnInfo addContainerColumn()
    {
        return addContainerColumn(false);
    }

    protected MutableColumnInfo addContainerColumn(boolean isProvisioned)
    {
        BaseColumnInfo containerCol;
        if (isProvisioned)
        {
            SQLFragment sql = SQLFragment.unsafe("CAST (");
            sql.appendValue(getContainer());
            sql.unsafeAppend(" AS ").append(getSchema().getSqlDialect().getGuidTypeAsSql()).unsafeAppend(")");
            containerCol = new ExprColumn(this, "Container", sql, JdbcType.GUID);
        }
        else
        {
            containerCol = new AliasedColumn(this, "Container", _rootTable.getColumn("Container"));
        }
        containerCol.setConceptURI(BuiltInColumnTypes.CONTAINERID_CONCEPT_URI);
        return addColumn(containerCol);
    }

    protected ColumnInfo addWrapTypeColumn(String wrappedName, final String rootTableColumnName)
    {
        var typeColumn = new AliasedColumn(this, wrappedName, _rootTable.getColumn(rootTableColumnName));
        LookupForeignKey fk = new LookupForeignKey(getContainerFilter(), "RowId", null)
        {
            @Override
            public TableInfo getLookupTableInfo()
            {
                return _userSchema.createSpecimenTypeLookupTable(rootTableColumnName, getLookupContainerFilter());
            }
        };
        typeColumn.setFk(fk);

        return addColumn(typeColumn);
    }

    /* Subclass can override to support per-table roles */
    protected @NotNull Set<Role> getContextualRoles()
    {
        return getUserSchema().getContextualRoles();
    }

    /* NOTE getUpdateService() and hasPermission() should usually be overridden together */

    @Override
    public QueryUpdateService getUpdateService()
    {
        return null;
    }

    /**
     * ONLY OVERRIDE THIS IF TABLE SHOULD BE EDITABLE IN DATASPACE PROJECT-LEVEL CONTAINER
     * CONSIDER: use a "allowEditInDataspace" field to avoid need to override
     */
    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        checkedPermissions.add(perm);
        // Most tables should not be editable in Dataspace
        if (!perm.equals(ReadPermission.class) && StudyService.get().isDataspace(getContainer()))
            return false;
        return hasPermissionOverridable(user, perm);
    }

    /**
     *  Override this method if
     * a) You need special per-table read behavior (e.g. datasets)
     * b) You implement getUpdateService()
     */
    protected boolean hasPermissionOverridable(UserPrincipal user, Class<? extends Permission> perm)
    {
        return checkHasReadPermission(user, perm);
    }

    protected boolean checkReadOrIsAdminPermission(UserPrincipal user, Class<? extends Permission> perm)
    {
        return ReadPermission.class == perm && _userSchema.getContainer().hasPermission(user, perm, getContextualRoles()) ||
                _userSchema.getContainer().hasPermission(user, AdminPermission.class, getContextualRoles());
    }

    protected boolean checkHasReadPermission(UserPrincipal user, Class<? extends Permission> perm)
    {
        return ReadPermission.class == perm && _userSchema.getContainer().hasPermission(user, perm, getContextualRoles());
    }

    protected boolean checkContainerPermission(UserPrincipal user, Class<? extends Permission> perm)
    {
        return _userSchema.getContainer().hasPermission(user, perm, getContextualRoles());
    }

    protected boolean checkSpecimenEditPermissions(UserPrincipal user, Class<? extends Permission> perm)
    {
        if (perm.equals(ReadPermission.class))
            return getContainer().hasPermission(user, perm, getContextualRoles());

        SpecimenMigrationService sms = SpecimenMigrationService.get();
        return sms != null && getContainer().hasPermissions(user, Set.of(perm, sms.getEditSpecimenDataPermission()), getContextualRoles());
    }

    protected void addOptionalColumns(List<DomainProperty> optionalProperties, boolean editable, @Nullable List<String> readOnlyColumnNames)
    {
        SqlDialect dialect = getSqlDialect();
        Supplier<Map<DomainProperty, Object>> defaultsSupplier = null;
        for (DomainProperty domainProperty : optionalProperties)
        {
            PropertyDescriptor property = domainProperty.getPropertyDescriptor();
            SQLFragment sql = SQLFragment.unsafe(ExprColumn.STR_TABLE_ALIAS);
            var legalName = property.getLegalSelectName(dialect);
            sql.unsafeAppend(".").appendIdentifier(legalName);
            var column = new ExprColumn(this, legalName.getId(), sql, property.getJdbcType());
            defaultsSupplier = PropertyColumn.copyAttributes(getUserSchema().getUser(), column, domainProperty, getContainer(), null, defaultsSupplier);
            if (editable)
            {
                // Make editable, but some should be read only
                column.setUserEditable(true);
                if (null != readOnlyColumnNames && readOnlyColumnNames.contains(column.getName().toLowerCase()))
                    column.setReadOnly(true);
            }
            addColumn(column);
        }
    }

    @Override
    public boolean supportsContainerFilter()
    {
        if (!_userSchema.allowSetContainerFilter())
            return false;
        return super.supportsContainerFilter();
    }
}
