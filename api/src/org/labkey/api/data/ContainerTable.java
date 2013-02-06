/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.ActionURL;

import java.util.Set;

/**
 * User: jeckels
 * Date: Feb 22, 2007
 */
public class ContainerTable extends FilteredTable<UserSchema>
{
    @Nullable private ActionURL _url;

    public ContainerTable(UserSchema schema)
    {
        super(CoreSchema.getInstance().getTableInfoContainers(), schema);
        // Call this after having a chance to set _schema's value. It's invoked in the superclass constructor,
        // but that's too early for this scenario
        applyContainerFilter(getContainerFilter());
        init();
    }

    public ContainerTable(UserSchema schema, ActionURL url)
    {
        this(schema);
        _url = url;
    }

    private void init()
    {
        SqlDialect dialect = getSchema().getSqlDialect();
        setDescription("Contains one row for every folder, workbook, or project");
        
        wrapAllColumns(true);
        getColumn("_ts").setHidden(true);
        ColumnInfo entityIdColumn = getColumn("EntityId");
        entityIdColumn.setHidden(true);
        entityIdColumn.setKeyField(true);
        getColumn("RowId").setHidden(true);

        getColumn("Parent").setFk(new LookupForeignKey("EntityId", "Name")
        {
           public TableInfo getLookupTableInfo()
           {
                return new ContainerTable(_userSchema);
           }
        });

        ActionURL projBegin = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(ContainerManager.getRoot());
        String wbURL = AppProps.getInstance().getContextPath() + "/" + projBegin.getController()
                + "/__r${ID}/" + projBegin.getAction() + ".view";
        StringExpression webURLExp = StringExpressionFactory.create(wbURL, true);

        ColumnInfo col = this.wrapColumn("ID", getRealTable().getColumn("RowId"));
        col.setReadOnly(true);
        col.setURL(webURLExp);
        this.addColumn(col);

        getColumn("Name").setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new ContainerDisplayColumn(colInfo, false, _url);
            }
        });

        PropertyManager.PropertySchema propertySchema = PropertyManager.PropertySchema.getInstance();
        SQLFragment folderTypeSQL = new SQLFragment("(SELECT Value FROM " + propertySchema.getTableInfoProperties() + " p, " +
                propertySchema.getTableInfoPropertySets() + " ps WHERE ps.ObjectId = " + ExprColumn.STR_TABLE_ALIAS +
                ".EntityId AND ps.Category = ? AND ps." + getSqlDialect().getColumnSelectName("set") + " = p." +
                getSqlDialect().getColumnSelectName("set") + " AND p.Name = ?)");
        folderTypeSQL.add(ContainerManager.FOLDER_TYPE_PROPERTY_SET_NAME);
        folderTypeSQL.add(ContainerManager.FOLDER_TYPE_PROPERTY_NAME);
        ExprColumn folderTypeColumn = new ExprColumn(this, "FolderType", folderTypeSQL, JdbcType.VARCHAR);
        addColumn(folderTypeColumn);

        SQLFragment folderDisplaySQL = new SQLFragment("COALESCE("+ ExprColumn.STR_TABLE_ALIAS +".title, "+ ExprColumn.STR_TABLE_ALIAS +".name)");
        ExprColumn folderDisplayColumn = new ExprColumn(this, "DisplayName", folderDisplaySQL, JdbcType.VARCHAR);
        addColumn(folderDisplayColumn);
        setTitleColumn(folderDisplayColumn.getName());

        final ColumnInfo folderPathCol = this.wrapColumn("Path", getRealTable().getColumn("Name"));
        folderPathCol.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            @Override
            public DisplayColumn createRenderer(final ColumnInfo colInfo)
            {
                return new ContainerDisplayColumn(colInfo, true, _url);
            }
        });
        addColumn(folderPathCol);

        SQLFragment containerTypeSQL = new SQLFragment("CASE WHEN "+ ExprColumn.STR_TABLE_ALIAS +".Type = 'workbook' THEN 'workbook' " +
            "WHEN "+ExprColumn.STR_TABLE_ALIAS+".Type = 'tab' THEN 'tab' " +
            "WHEN "+ExprColumn.STR_TABLE_ALIAS+".entityid = ? THEN 'root' " +
            "WHEN "+ExprColumn.STR_TABLE_ALIAS+".parent = ? THEN 'project' " +
            "ELSE 'folder' END");
        containerTypeSQL.add(ContainerManager.getRoot().getEntityId());
        containerTypeSQL.add(ContainerManager.getRoot().getEntityId());
        ExprColumn containerTypeColumn = new ExprColumn(this, "ContainerType", containerTypeSQL, JdbcType.VARCHAR);
        addColumn(containerTypeColumn);

        SQLFragment containerWorkbookSQL = new SQLFragment("CASE WHEN "+ ExprColumn.STR_TABLE_ALIAS +".Type = 'workbook' THEN " + dialect.getBooleanTRUE() +
                " ELSE " + dialect.getBooleanFALSE() + " END");
        ExprColumn containerWorkbookColumn = new ExprColumn(this, "Workbook", containerWorkbookSQL, JdbcType.BOOLEAN);
        addColumn(containerWorkbookColumn);

        SQLFragment containerDisplaySQL = new SQLFragment("CASE WHEN "+ ExprColumn.STR_TABLE_ALIAS + ".Type = 'workbook' " +
            "THEN " + getSqlDialect().concatenate("CAST(" + ExprColumn.STR_TABLE_ALIAS + ".rowid as varchar)", "'. '", ExprColumn.STR_TABLE_ALIAS + ".title") +
            " ELSE " + ExprColumn.STR_TABLE_ALIAS + ".name END");
        ExprColumn containerDisplayColumn = new ExprColumn(this, "IdPrefixedName", containerDisplaySQL, JdbcType.VARCHAR);
        addColumn(containerDisplayColumn);

        col = getColumn("CreatedBy");
        col.setFk(new LookupForeignKey("UserId", "DisplayName")
        {
            public TableInfo getLookupTableInfo()
            {
                String tableName = _userSchema.getUser().isAdministrator() ? "SiteUsers" : "Users";
                return QueryService.get().getUserSchema(_userSchema.getUser(), _userSchema.getContainer(), "core").getTable(tableName);
            }
        });

        //NOTE: the string expression used above for the URL will not automatically include the correct field keys
        //since it doesnt inherit from FieldKeyStringExpression.  see DisplayColumn.addQueryFieldKeys
        //therefore we make sure the ID col is included below
        DisplayColumnFactory dcf = new DisplayColumnFactory()
        {
            @Override
            public DisplayColumn createRenderer(final ColumnInfo colInfo)
            {
                return new DataColumn(colInfo){
                    @Override
                    public void addQueryFieldKeys(Set<FieldKey> keys)
                    {
                        super.addQueryFieldKeys(keys);
                        keys.add(FieldKey.fromString("ID"));
                    }
                };
            }
        };

        ColumnInfo name = getColumn("Name");
        name.setURL(webURLExp);

        ColumnInfo title = getColumn("Title");
        title.setURL(webURLExp);
        title.setDisplayColumnFactory(dcf);

        ColumnInfo displayName = getColumn("DisplayName");
        displayName.setURL(webURLExp);
        displayName.setDisplayColumnFactory(dcf);

        ColumnInfo idPrefixed = getColumn("IdPrefixedName");
        idPrefixed.setURL(webURLExp);
        idPrefixed.setDisplayColumnFactory(dcf);

        setTitleColumn("DisplayName");
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

}
