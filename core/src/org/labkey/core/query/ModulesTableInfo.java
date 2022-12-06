/*
 * Copyright (c) 2015-2019 LabKey Corporation
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
package org.labkey.core.query;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnDecorator;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.dialect.DialectStringHandler;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.util.StringExpressionFactory;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * User: kevink
 * Date: 5/5/15
 */
public class ModulesTableInfo extends SimpleUserSchema.SimpleTable<CoreQuerySchema>
{
    public ModulesTableInfo(CoreQuerySchema schema)
    {
        super(schema, CoreSchema.getInstance().getTableInfoModules(), null);
        setDescription("Contains a row for each module known to the server.");
    }

    @Override
    public FieldKey getContainerFieldKey()
    {
        return null;
    }

    @Override
    protected void addTableURLs()
    {
        setInsertURL(LINK_DISABLER);
        setImportURL(LINK_DISABLER);
        setUpdateURL(LINK_DISABLER);
        setDeleteURL(LINK_DISABLER);
        super.addTableURLs();
    }

    @Override
    public void addColumns()
    {
        var nameCol = addWrapColumn(getRealTable().getColumn("Name"));
        nameCol.setKeyField(true);
        nameCol.setURL(new StringExpressionFactory.URLStringExpression("${URL}"));
        nameCol.setURLTargetWindow("_blank");

        addTextColumn("ReleaseVersion").setScale(255);
        var schemaVersionColumn = addWrapColumn(getRealTable().getColumn("SchemaVersion"));
        schemaVersionColumn.setDisplayColumnFactory(new SchemaVersionDisplayColumnFactory(schemaVersionColumn.getDisplayColumnFactory()));
        addWrapColumn(getRealTable().getColumn("ClassName"));

        addTextColumn("Label").setScale(255);
        addTextColumn("Description").setScale(4000);
        addTextColumn("URL").setURLTargetWindow("_blank");
        addTextColumn("Author");
        addTextColumn("Maintainer");
        addColumn("ManageVersion", JdbcType.BOOLEAN);

        var orgCol = addTextColumn("Organization");
        orgCol.setURL(StringExpressionFactory.createURL("${OrganizationURL}"));
        orgCol.setURLTargetWindow("_blank");
        addTextColumn("OrganizationURL").setHidden(true);

        var licenseCol = addTextColumn("License");
        licenseCol.setURL(StringExpressionFactory.createURL("${LicenseURL}"));
        licenseCol.setURLTargetWindow("_blank");
        addTextColumn("LicenseURL").setHidden(true);
        addTextColumn("VcsRevision");
        addTextColumn("VcsURL");
        addTextColumn("Dependencies");

        addWrapColumn(getRealTable().getColumn("Schemas"));

        setDefaultVisibleColumns(List.of(
            FieldKey.fromParts("Name"),
            FieldKey.fromParts("ReleaseVersion"),
            FieldKey.fromParts("SchemaVersion"),
            FieldKey.fromParts("Label"),
            FieldKey.fromParts("Organization"),
            FieldKey.fromParts("License")
        ));
    }

    @Override
    protected ColumnInfo resolveColumn(String name)
    {
        if ("InstalledVersion".equalsIgnoreCase(name))
        {
            return getColumn("SchemaVersion");
        }
        return super.resolveColumn(name);
    }

    private BaseColumnInfo addTextColumn(String name)
    {
        return addColumn(name, JdbcType.VARCHAR);
    }

    private BaseColumnInfo addColumn(String name, JdbcType type)
    {
        var col = new BaseColumnInfo(FieldKey.fromParts(name), this, type);
        col.setReadOnly(true);
        addColumn(col);
        return col;
    }

    @Override
    public ModulesTableInfo init()
    {
        return (ModulesTableInfo) super.init();
    }

    private void appendStringLiteral(DialectStringHandler h, SQLFragment sql, String sep, String s)
    {
        sql.append(sep);
        s = StringUtils.trimToNull(s);
        if (null == s)
            sql.append("NULL");
        else
            sql.append(h.quoteStringLiteral(s));
    }

    private void appendBooleanLiteral(SQLFragment sql, String sep, boolean b)
    {
        sql.append(sep);

        SqlDialect dialect = getSqlDialect();
        String literal = dialect.getBooleanLiteral(b);

        if (dialect.isSqlServer())
        {
            sql.append("CAST (").append(literal).append(" AS BIT)");
        }
        else
        {
            sql.append(literal);
        }
    }

    @NotNull
    @Override
    public SQLFragment getFromSQL(String alias)
    {
        checkReadBeforeExecute();
        var h = getSqlDialect().getStringHandler();
        SQLFragment ret = new SQLFragment();

        // select in-memory modules
        SQLFragment cte = new SQLFragment();
        cte.append("SELECT * FROM (\n");
        cte.append("VALUES ");
        String sep = "";
        for (Module module : ModuleLoader.getInstance().getModules())
        {
            cte.append(sep);
            sep = ",\n";
            appendStringLiteral(h, cte,"(",module.getName());
            appendStringLiteral(h, cte,",",module.getReleaseVersion());
            appendStringLiteral(h, cte,",",module.getLabel());
            appendStringLiteral(h, cte,",",module.getDescription());
            appendStringLiteral(h, cte,",",module.getUrl());
            appendStringLiteral(h, cte,",",module.getAuthor());
            appendStringLiteral(h, cte,",",module.getMaintainer());
            appendBooleanLiteral(cte, ",", module.shouldManageVersion());
            appendStringLiteral(h, cte,",",module.getOrganization());
            appendStringLiteral(h, cte,",",module.getOrganizationUrl());
            appendStringLiteral(h, cte,",",module.getLicense());
            appendStringLiteral(h, cte,",",module.getLicenseUrl());
            appendStringLiteral(h, cte,",",module.getVcsRevision());
            appendStringLiteral(h, cte,",",module.getVcsUrl());
            appendStringLiteral(h, cte,",",StringUtils.join(module.getModuleDependenciesAsSet(), ", "));
            cte.append(")");
        }
        cte.append(") AS T (");
        cte.append("ModuleName");
        cte.append(",ReleaseVersion");
        cte.append(",Label");
        cte.append(",Description");
        cte.append(",Url");
        cte.append(",Author");
        cte.append(",Maintainer");
        cte.append(",ManageVersion");
        cte.append(",Organization, OrganizationURL");
        cte.append(",License, LicenseURL");
        cte.append(",VcsRevision, VcsURL");
        cte.append(",Dependencies");
        cte.append(")\n");

        String tableName = alias + "$m";
        String token = ret.addCommonTableExpression("modulestableconstants", tableName, cte);

        // join with core.modules
        ret.append("(SELECT m.name, m.schemaversion, m.classname, m.schemas");
        ret.append(",").append(tableName).append(".*");
        ret.append("\n");
        ret.append("FROM ").append(getFromTable().getFromSQL("m")).append("\n");
        ret.append("INNER JOIN ").append(token).append(" ").append(tableName).append(" ON m.name = ").append(tableName).append(".ModuleName\n");
        // CONSIDER: LEFT OUTER JOIN to include rows from core.modules for modules not currently installed

        // WHERE
        SQLFragment filterFrag = getFilter().getSQLFragment(getFromTable(), null);
        ret.append("\n").append(filterFrag).append(") ").append(alias);

        return ret;
    }

    // Format SchemaVersion column using the standard ModuleContext formatting rules: force three-decimal places for >= 20.000,
    // otherwise suppress trailing zeroes. Also, right align the values in the grid.
    private static class SchemaVersionDisplayColumnFactory implements DisplayColumnFactory
    {
        private final DisplayColumnFactory _factory;

        public SchemaVersionDisplayColumnFactory(DisplayColumnFactory factory)
        {
            _factory = factory;
        }

        @Override
        public DisplayColumn createRenderer(ColumnInfo colInfo)
        {
            // This DisplayColumn's rendering assumes column type is Double; fail fast if it's something else
            assert colInfo.getJdbcType() == JdbcType.DOUBLE;
            return new DisplayColumnDecorator(_factory.createRenderer(colInfo))
            {
                {
                    _textAlign = "right";
                }

                @Override
                public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
                {
                    Object o = getValue(ctx);

                    if (null == o)
                    {
                        super.renderGridCellContents(ctx, out);
                    }
                    else
                    {
                        String formatted = ModuleContext.formatVersion((double)o);
                        out.write(formatted);
                    }
                }
            };
        }
    }
}
