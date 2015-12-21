/*
 * Copyright (c) 2007-2015 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.module.Module;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.UserIdQueryForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

import java.util.Set;

/**
 * User: jeckels
 * Date: Feb 22, 2007
 */
public class ContainerTable extends FilteredTable<UserSchema>
{
    public ContainerTable(UserSchema schema)
    {
        this(schema, null);
    }

    public ContainerTable(UserSchema schema, ActionURL url)
    {
        super(CoreSchema.getInstance().getTableInfoContainers(), schema);

        // Call this after having a chance to set _schema's value. It's invoked in the superclass constructor,
        // but that's too early for this scenario
        applyContainerFilter(getContainerFilter());
        init(url);
    }

    @Override
    public FieldKey getContainerFieldKey()
    {
        return FieldKey.fromParts("EntityId");
    }

    private void init(ActionURL url)
    {
        SqlDialect dialect = getSchema().getSqlDialect();
        setDescription("Contains one row for every folder, workbook, or project");
        
        wrapAllColumns(true);
        getColumn("_ts").setHidden(true);

        ColumnInfo entityIdColumn = getColumn("EntityId");
        entityIdColumn.setHidden(true);
        entityIdColumn.setKeyField(true);
        entityIdColumn.setReadOnly(true);

        getColumn("RowId").setHidden(true);
        getColumn("RowId").setReadOnly(true);
        getColumn("RowId").setUserEditable(true);

        ColumnInfo parentColumn = getColumn("Parent");
        ContainerForeignKey.initColumn(parentColumn, _userSchema);

        if (url == null)
            url = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(ContainerManager.getRoot());
        DetailsURL detailsURL = new DetailsURL(url);
        setDetailsURL(detailsURL);

        ColumnInfo col = this.wrapColumn("ID", getRealTable().getColumn("RowId"));
        col.setReadOnly(true);
        col.setURL(detailsURL);
        this.addColumn(col);

        ColumnInfo name = getColumn("Name");
        name.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new ContainerDisplayColumn(colInfo, false);
            }
        });
        name.setURL(detailsURL);
        name.setReadOnly(true); // CONSIDER: allow renames via QueryUpdateService api

        ColumnInfo sortOrderCol = getColumn("SortOrder");
        sortOrderCol.setReadOnly(true);
        sortOrderCol.setUserEditable(false);

        PropertySchema propertySchema = PropertySchema.getInstance();
        SQLFragment folderTypeSQL = new SQLFragment("(SELECT Value FROM " + propertySchema.getTableInfoProperties() + " p, " +
                propertySchema.getTableInfoPropertySets() + " ps WHERE ps.ObjectId = " + ExprColumn.STR_TABLE_ALIAS +
                ".EntityId AND ps.Category = ? AND ps." + getSqlDialect().getColumnSelectName("set") + " = p." +
                getSqlDialect().getColumnSelectName("set") + " AND p.Name = ?)");
        folderTypeSQL.add(ContainerManager.FOLDER_TYPE_PROPERTY_SET_NAME);
        folderTypeSQL.add(ContainerManager.FOLDER_TYPE_PROPERTY_NAME);
        ExprColumn folderTypeColumn = new ExprColumn(this, "FolderType", folderTypeSQL, JdbcType.VARCHAR);
        folderTypeColumn.setReadOnly(true);
        addColumn(folderTypeColumn);

        SQLFragment folderDisplaySQL = new SQLFragment("COALESCE("+ ExprColumn.STR_TABLE_ALIAS +".title, "+ ExprColumn.STR_TABLE_ALIAS +".name)");
        ExprColumn folderDisplayColumn = new ExprColumn(this, "DisplayName", folderDisplaySQL, JdbcType.VARCHAR);
        addColumn(folderDisplayColumn);
        folderDisplayColumn.setURL(detailsURL);
        folderDisplayColumn.setReadOnly(true);
        setTitleColumn(folderDisplayColumn.getName());

        final ColumnInfo folderPathCol = this.wrapColumn("Path", getRealTable().getColumn("Name"));
        folderPathCol.setReadOnly(true);
        folderPathCol.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            @Override
            public DisplayColumn createRenderer(final ColumnInfo colInfo)
            {
                return new ContainerDisplayColumn(colInfo, true);
            }
        });
        addColumn(folderPathCol);
        folderPathCol.setURL(detailsURL);

        ColumnInfo typeCol = getColumn("Type");
        typeCol.setReadOnly(true);
        typeCol.setUserEditable(false);

        SQLFragment containerTypeSQL = new SQLFragment("CASE WHEN "+ ExprColumn.STR_TABLE_ALIAS +".Type = 'workbook' THEN 'workbook' " +
            "WHEN "+ExprColumn.STR_TABLE_ALIAS+".Type = 'tab' THEN 'tab' " +
            "WHEN "+ExprColumn.STR_TABLE_ALIAS+".entityid = ? THEN 'root' " +
            "WHEN "+ExprColumn.STR_TABLE_ALIAS+".parent = ? THEN 'project' " +
            "ELSE 'folder' END");
        containerTypeSQL.add(ContainerManager.getRoot().getEntityId());
        containerTypeSQL.add(ContainerManager.getRoot().getEntityId());
        ExprColumn containerTypeColumn = new ExprColumn(this, "ContainerType", containerTypeSQL, JdbcType.VARCHAR);
        containerTypeColumn.setReadOnly(true);
        addColumn(containerTypeColumn);

        SQLFragment containerWorkbookSQL = new SQLFragment("CASE WHEN "+ ExprColumn.STR_TABLE_ALIAS +".Type = 'workbook' THEN " + dialect.getBooleanTRUE() +
                " ELSE " + dialect.getBooleanFALSE() + " END");
        ExprColumn containerWorkbookColumn = new ExprColumn(this, "Workbook", containerWorkbookSQL, JdbcType.BOOLEAN);
        containerWorkbookColumn.setReadOnly(true);
        addColumn(containerWorkbookColumn);

        SQLFragment containerDisplaySQL = new SQLFragment("CASE WHEN "+ ExprColumn.STR_TABLE_ALIAS + ".Type = 'workbook' " +
            "THEN " + getSqlDialect().concatenate("CAST(" + ExprColumn.STR_TABLE_ALIAS + ".rowid as varchar)", "'. '", ExprColumn.STR_TABLE_ALIAS + ".title") +
            " ELSE " + ExprColumn.STR_TABLE_ALIAS + ".name END");
        ExprColumn containerDisplayColumn = new ExprColumn(this, "IdPrefixedName", containerDisplaySQL, JdbcType.VARCHAR);
        containerDisplayColumn.setURL(detailsURL);
        containerDisplayColumn.setReadOnly(true);
        addColumn(containerDisplayColumn);

        col = getColumn("CreatedBy");
        col.setReadOnly(true);
        col.setFk(new UserIdQueryForeignKey(_userSchema.getUser(), _userSchema.getContainer(), true));

        ColumnInfo title = getColumn("Title");
        title.setURL(detailsURL);

        ColumnInfo activeModules = new AliasedColumn("ActiveModules", getColumn("RowId"));
        activeModules.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            @Override
            public DisplayColumn createRenderer(ColumnInfo rowIdCol)
            {
                return new ActiveModulesDisplayColumn(rowIdCol);
            }
        });
        activeModules.setReadOnly(true);
        activeModules.setHidden(true);
        addColumn(activeModules);

        setTitleColumn("DisplayName");
        
        setImportURL(LINK_DISABLER);
    }

    protected String getContainerFilterColumn()
    {
        return "EntityId";
    }

    @Override
    protected void applyContainerFilter(ContainerFilter filter)
    {
        if (_schema != null)
        {
            super.applyContainerFilter(filter);
        }
    }

    private static class ActiveModulesDisplayColumn extends DataColumn
    {
        public ActiveModulesDisplayColumn(ColumnInfo rowIdCol)
        {
            super(rowIdCol);
        }

        @Override
        public boolean isSortable()
        {
            return false;
        }

        @Override
        public boolean isFilterable()
        {
            return false;
        }

        @NotNull
        @Override
        public String getFormattedValue(RenderContext ctx)
        {
            int rowId = (Integer)super.getValue(ctx);
            Container c = ContainerManager.getForRowId(rowId);
            if (c == null)
                return null;

            Set<Module> modules = c.getActiveModules();
            StringBuilder sb = new StringBuilder();
            String sep = "";
            for (Module module : modules)
            {
                sb.append(sep).append(module.getName());
                sep = ", ";
            }
            return sb.toString();
        }
    }
}
