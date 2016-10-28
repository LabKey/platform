/*
 * Copyright (c) 2015-2016 LabKey Corporation
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
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Table;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.util.StringExpressionFactory;

import java.util.Arrays;
import java.util.Map;

/**
 * User: kevink
 * Date: 5/5/15
 */
public class ModulesTableInfo extends SimpleUserSchema.SimpleTable<CoreQuerySchema>
{
    public ModulesTableInfo(CoreQuerySchema schema)
    {
        super(schema, CoreSchema.getInstance().getTableInfoModules());
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
        ColumnInfo nameCol = addWrapColumn(getRealTable().getColumn("Name"));
        nameCol.setKeyField(true);
        nameCol.setURL(new StringExpressionFactory.URLStringExpression("${URL}"));
        nameCol.setURLTargetWindow("_blank");

        addWrapColumn(getRealTable().getColumn("InstalledVersion"));
        addWrapColumn(getRealTable().getColumn("ClassName"));

        addTextColumn("Label").setScale(255);
        addTextColumn("Description").setScale(4000);
        addTextColumn("URL").setURLTargetWindow("_blank");
        addTextColumn("Author");
        addTextColumn("Maintainer");

        ColumnInfo orgCol = addTextColumn("Organization");
        orgCol.setURL(StringExpressionFactory.createURL("${OrganizationURL}"));
        orgCol.setURLTargetWindow("_blank");
        addTextColumn("OrganizationURL").setHidden(true);

        ColumnInfo licenseCol = addTextColumn("License");
        licenseCol.setURL(StringExpressionFactory.createURL("${LicenseURL}"));
        licenseCol.setURLTargetWindow("_blank");
        addTextColumn("LicenseURL").setHidden(true);
        addTextColumn("VcsRevision");
        addTextColumn("VcsURL");
        addTextColumn("Dependencies");

        addWrapColumn(getRealTable().getColumn("Schemas"));

        setDefaultVisibleColumns(Arrays.asList(
                FieldKey.fromParts("Name"),
                FieldKey.fromParts("InstalledVersion"),
                FieldKey.fromParts("Label"),
                FieldKey.fromParts("Organization"),
                FieldKey.fromParts("License")
        ));
    }

    private ColumnInfo addTextColumn(String name)
    {
        ColumnInfo col = new ColumnInfo(FieldKey.fromParts(name), this, JdbcType.VARCHAR);
        col.setReadOnly(true);
        addColumn(col);
        return col;
    }

    @Override
    public ModulesTableInfo init()
    {
        return (ModulesTableInfo) super.init();
    }

    @NotNull
    @Override
    public SQLFragment getFromSQL(String alias)
    {
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
            cte.append("(");
            cte.append("?").add(module.getName());
            cte.append(",?").add(StringUtils.trimToNull(module.getLabel()));
            cte.append(",?").add(StringUtils.trimToNull(module.getDescription()));
            cte.append(",?").add(StringUtils.trimToNull(module.getUrl()));
            cte.append(",?").add(StringUtils.trimToNull(module.getAuthor()));
            cte.append(",?").add(StringUtils.trimToNull(module.getMaintainer()));
            cte.append(",?").add(StringUtils.trimToNull(module.getOrganization()));
            cte.append(",?").add(StringUtils.trimToNull(module.getOrganizationUrl()));
            cte.append(",?").add(StringUtils.trimToNull(module.getLicense()));
            cte.append(",?").add(StringUtils.trimToNull(module.getLicenseUrl()));
            cte.append(",?").add(StringUtils.trimToNull(module.getVcsRevision()));
            cte.append(",?").add(StringUtils.trimToNull(module.getVcsUrl()));
            cte.append(",?").add(StringUtils.join(module.getModuleDependenciesAsSet(), ", "));
            cte.append(")");
        }
        cte.append(") AS T (");
        cte.append("ModuleName");
        cte.append(",Label");
        cte.append(",Description");
        cte.append(",Url");
        cte.append(",Author");
        cte.append(",Maintainer");
        cte.append(",Organization, OrganizationURL");
        cte.append(",License, LicenseURL");
        cte.append(",VcsRevision, VcsURL");
        cte.append(",Dependencies");
        cte.append(")\n");

        String tableName = alias + "$m";
        String token = ret.addCommonTableExpression(cte.toString(), tableName, cte);

        // join with core.modules
        ret.append("(SELECT m.name, m.installedversion, m.classname, m.schemas");
        ret.append(",").append(tableName).append(".*");
        ret.append("\n");
        ret.append("FROM ").append(getFromTable().getFromSQL("m")).append("\n");
        ret.append("INNER JOIN ").append(token).append(" ").append(tableName).append(" ON m.name = ").append(tableName).append(".ModuleName\n");
        // CONSIDER: LEFT OUTER JOIN to include rows from core.modules for modules not currently installed

        // WHERE
        Map<FieldKey, ColumnInfo> columnMap = Table.createColumnMap(getFromTable(), getFromTable().getColumns());
        SQLFragment filterFrag = getFilter().getSQLFragment(_rootTable.getSqlDialect(), columnMap);
        ret.append("\n").append(filterFrag).append(") ").append(alias);

        return ret;
    }
}
