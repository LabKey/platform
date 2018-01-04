/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
package org.labkey.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerForeignKey;
import org.labkey.api.data.ForeignKey;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MultiValuedForeignKey;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.flag.FlagForeignKey;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.UserIdForeignKey;
import org.labkey.api.query.UserIdQueryForeignKey;
import org.labkey.api.query.UserSchema;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
* User: kevink
* Date: 12/10/12
*/
public class LinkedTableInfo extends SimpleUserSchema.SimpleTable<UserSchema>
{
    final Set<FieldKey> includedColumns;
    final Set<FieldKey> excludedColumns;

    public LinkedTableInfo(@NotNull LinkedSchema schema, @NotNull TableInfo table)
    {
        this(schema, table, null, null);
    }

    public LinkedTableInfo(@NotNull LinkedSchema schema, @NotNull TableInfo table,
                           @Nullable Set<FieldKey> includedColumns,
                           @Nullable Set<FieldKey> excludedColumns)
    {
        super(schema, table);
        this.includedColumns = includedColumns;
        this.excludedColumns = excludedColumns;
    }

    @Override
    protected boolean acceptColumn(ColumnInfo col)
    {
        if (null != includedColumns)
        {
            return includedColumns.contains(col.getFieldKey());
        }
        if (null != excludedColumns)
        {
            return !excludedColumns.contains(col.getFieldKey());
        }
        return true;
    }

    @Override @NotNull
    public LinkedSchema getUserSchema()
    {
        return (LinkedSchema)super.getUserSchema();
    }

    @Override
    protected void applyContainerFilter(ContainerFilter filter)
    {
        // Don't need to filter here, let the underlying table handle it
    }

    // Disallow container filtering.  At some point in the future we may introduce a 'inherit' bit on
    // external and linked schemas so they are available in sub-folders and become container filterable.
    @Override
    public boolean supportsContainerFilter()
    {
        return false;
    }

    @Override
    protected void addTableURLs()
    {
        // Disallow all table URLs
        setImportURL(LINK_DISABLER);
        setInsertURL(LINK_DISABLER);
        setUpdateURL(LINK_DISABLER);
        setDeleteURL(LINK_DISABLER);

        // Always use generic query details and grid page
        DetailsURL detailsURL = QueryService.get().urlDefault(getContainer(), QueryAction.detailsQueryRow, this);
        setDetailsURL(detailsURL);

        DetailsURL gridURL = QueryService.get().urlDefault(getContainer(), QueryAction.executeQuery, getPublicSchemaName(), getPublicName(), Collections.emptyMap());
        setGridURL(gridURL);
    }

    @Override
    protected void fixupWrappedColumn(ColumnInfo wrap, ColumnInfo col)
    {
        super.fixupWrappedColumn(wrap, col);

        // Fixup the column's ForeignKey if it is a user id, container, or exp.flag foreign key
        // or if the FK points to the current schema and one of the exposed tables or queries;
        // otherwise remove the FK to disallow the FK.
        ForeignKey fk = wrap.getFk();
        ForeignKey fixedFk = null;
        if (fk instanceof UserIdForeignKey || fk instanceof UserIdQueryForeignKey || fk instanceof ContainerForeignKey || fk instanceof FlagForeignKey)
        {
            fixedFk = fk;
        }
        else if (fk != null)
        {
            LinkedSchema schema = getUserSchema();
            UserSchema sourceSchema = schema.getSourceSchema();

            if ((fk.getLookupSchemaName() != null && sourceSchema.getName().equals(fk.getLookupSchemaName())) &&
                    (fk.getLookupTableName() != null && (schema.getTableNames().contains(fk.getLookupTableName()) || schema.getQueryDefs().keySet().contains(fk.getLookupTableName()))) &&
                    (fk.getLookupContainer() == null || sourceSchema.getContainer().equals(fk.getLookupContainer())))
            {
                // XXX: Do we need to set the container on the join to ensure the linked schema lookups aren't exposing too much data?
                boolean useRawFKValue = false;
                if (fk instanceof QueryForeignKey)
                {
                    useRawFKValue = ((QueryForeignKey)fk).isUseRawFKValue();
                }

                fixedFk = new QueryForeignKey(schema, schema.getContainer(), fk.getLookupTableName(), fk.getLookupColumnName(), fk.getLookupDisplayName(), useRawFKValue);

                if (fk instanceof MultiValuedForeignKey)
                {
                    fixedFk = new MultiValuedForeignKey(fixedFk, ((MultiValuedForeignKey)fk).getJunctionLookup());
                }
            }
        }
        wrap.setFk(fixedFk);

        // Remove URL. LinkedTableInfo doesn't include URLs.
        wrap.setURL(LINK_DISABLER);
    }

    @Override
    public ColumnInfo wrapColumn(ColumnInfo col)
    {
        if ("Container".equalsIgnoreCase(col.getName()) || "Folder".equalsIgnoreCase(col.getName()))
        {
            // Remap the container column to be the the target instead
            //ISSUE 19600: explicitly cast to varchar on postgres to avoid "failed to find conversion function from unknown to text" error
            SQLFragment sql = col.getSqlDialect().isPostgreSQL() ? new SQLFragment("CAST('" + getContainer().getEntityId() + "' AS VARCHAR)") : new SQLFragment("'" + getContainer().getEntityId() + "'");
            ColumnInfo ret = new ExprColumn(col.getParentTable(), col.getName(), sql, JdbcType.VARCHAR);
            ret.copyAttributesFrom(col);
            ret.setHidden(col.isHidden());
            col = ret;
        }
        return super.wrapColumn(col);
    }

    @NotNull
    @Override
    protected Collection<FieldKey> addDomainColumns()
    {
        // LinkedTableInfos only adds columns from the source table and has no Domain columns.
        return Collections.emptyList();
    }

    @NotNull
    @Override
    public Collection<QueryService.ParameterDecl> getNamedParameters()
    {
        // LinkedTableInfo only exposes named parameters defined in the original TableInfo
        // and removes named parameters that are added to the generated LinkedSchema.createQueryDef() query.
        //return super.getNamedParameters();
        return Collections.emptyList();
    }

    @NotNull
    @Override
    public SQLFragment getFromSQL(String alias)
    {
        SQLFragment frag = super.getFromSQL(alias);

        // Bind named parameters added to the generated LinkedSchema.createQueryDef() query. .. looks like these are bound second after URL parameters.
        Map<String, Object> paramValues = fireCustomizeParameterValues();

        QueryService.get().bindNamedParameters(frag, paramValues);

        return frag;
    }

    protected Map<String, Object> fireCustomizeParameterValues()
    {
        return getUserSchema().fireCustomizeParameterValues(this);
    }
}
