package org.labkey.core.query;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CoreSchema;
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

        addCopyWrapColumn("Label");
        addCopyWrapColumn("Description");
        addCopyWrapColumn("URL");
        addCopyWrapColumn("Author");
        addCopyWrapColumn("Maintainer");
        ColumnInfo orgCol = addCopyWrapColumn("Organization");
        orgCol.setURL(StringExpressionFactory.createURL("${OrganizationURL}"));
        orgCol.setURLTargetWindow("_blank");
        addCopyWrapColumn("OrganizationURL");

        ColumnInfo licenseCol = addCopyWrapColumn("License");
        licenseCol.setURL(StringExpressionFactory.createURL("${LicenseURL}"));
        licenseCol.setURLTargetWindow("_blank");
        addCopyWrapColumn("LicenseURL");
        addCopyWrapColumn("VcsRevision");
        addCopyWrapColumn("VcsURL");
        addCopyWrapColumn("Dependencies");

        addWrapColumn(getRealTable().getColumn("Schemas"));

        setDefaultVisibleColumns(Arrays.asList(
                FieldKey.fromParts("Name"),
                FieldKey.fromParts("InstalledVersion"),
                FieldKey.fromParts("Label"),
                FieldKey.fromParts("Organization"),
                FieldKey.fromParts("License")
        ));
    }

    // NOTE: We can't just wrap the real table's ColumnInfo since it doesn't exist in the underlying
    // core.modules table.  The "missing" columns will be constructed as a VirtualColumnInfo and will
    // always select NULL when queried.  Luckily the metadata xml will still be applied.
    private ColumnInfo addCopyWrapColumn(String name)
    {
        ColumnInfo col = new ColumnInfo(getRealTable().getColumn(name), this);
        col.setIsUnselectable(false); // Undo VirtualColumnInfo setting
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
            cte.append(",?").add(module.getLabel());
            cte.append(",?").add(module.getDescription());
            cte.append(",?").add(module.getUrl());
            cte.append(",?").add(module.getAuthor());
            cte.append(",?").add(module.getMaintainer());
            cte.append(",?").add(module.getOrganization());
            cte.append(",?").add(module.getOrganizationUrl());
            cte.append(",?").add(module.getLicense());
            cte.append(",?").add(module.getLicenseUrl());
            cte.append(",?").add(module.getVcsRevision());
            cte.append(",?").add(module.getVcsUrl());
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
        ret.addCommonTableExpression(cte.toString(), tableName, cte);

        // join with core.modules
        ret.append("(SELECT m.name, m.installedversion, m.classname, m.schemas");
        ret.append(",").append(tableName).append(".*");
        ret.append("\n");
        ret.append("FROM ").append(getFromTable().getFromSQL("m")).append("\n");
        ret.append("INNER JOIN ").append(tableName).append(" ON m.name = ").append(tableName).append(".ModuleName\n");
        // CONSIDER: LEFT OUTER JOIN to include rows from core.modules for modules not currently installed

        // WHERE
        Map<FieldKey, ColumnInfo> columnMap = Table.createColumnMap(getFromTable(), getFromTable().getColumns());
        SQLFragment filterFrag = getFilter().getSQLFragment(_rootTable.getSqlDialect(), columnMap);
        ret.append("\n").append(filterFrag).append(") ").append(alias);

        return ret;
    }
}
