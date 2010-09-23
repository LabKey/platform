/*
 * Copyright (c) 2007-2010 LabKey Corporation
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

import org.labkey.api.portal.ProjectUrls;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.ActionURL;

import java.sql.Types;

/**
 * User: jeckels
 * Date: Feb 22, 2007
 */
public class ContainerTable extends FilteredTable
{
    protected UserSchema _schema;

    public ContainerTable()
    {
        super(CoreSchema.getInstance().getTableInfoContainers());
        init();
    }

    public ContainerTable(UserSchema schema)
    {
        super(CoreSchema.getInstance().getTableInfoContainers(), schema.getContainer());
        _schema = schema;
        // Call this after having a chance to set _schema's value. It's invoked in the superclass constructor,
        // but that's too early for this scenario
        applyContainerFilter(getContainerFilter());
        setDescription("Contains one row for every folder, workbook, or project");
        init();
    }

    private void init()
    {
        wrapAllColumns(true);
        getColumn("_ts").setHidden(true);
        getColumn("EntityId").setHidden(true);
        getColumn("RowId").setHidden(true);

        getColumn("Parent").setFk(new LookupForeignKey("EntityId", "Name")
        {
            public TableInfo getLookupTableInfo()
            {
                return new ContainerTable();
            }
        });

        ActionURL projBegin = PageFlowUtil.urlProvider(ProjectUrls.class).getBeginURL(ContainerManager.getRoot());
        String wbURL = AppProps.getInstance().getContextPath() + "/" + projBegin.getPageFlow()
                + "/__r${ID}/" + projBegin.getAction() + ".view";
        StringExpression webURLExp = StringExpressionFactory.create(wbURL, true);

        ColumnInfo col = this.wrapColumn("ID", getRealTable().getColumn("RowId"));
        col.setKeyField(true);
        col.setReadOnly(true);
        col.setURL(webURLExp);
        this.addColumn(col);

        PropertyManager.PropertySchema propertySchema = PropertyManager.PropertySchema.getInstance();
        SQLFragment folderTypeSQL = new SQLFragment("(SELECT Value FROM " + propertySchema.getTableInfoProperties() + " p, " +
                propertySchema.getTableInfoPropertySets() + " ps WHERE ps.ObjectId = " + ExprColumn.STR_TABLE_ALIAS +
                ".EntityId AND ps.Category = ? AND ps." + getSqlDialect().getColumnSelectName("Set") + " = p." +
                getSqlDialect().getColumnSelectName("Set") + " AND p.Name = ?)");
        folderTypeSQL.add(Container.FOLDER_TYPE_PROPERTY_SET_NAME);
        folderTypeSQL.add(Container.FOLDER_TYPE_PROPERTY_NAME);
        ExprColumn folderTypeColumn = new ExprColumn(this, "FolderType", folderTypeSQL, Types.VARCHAR);
        addColumn(folderTypeColumn);

        col = getColumn("CreatedBy");
        final boolean isSiteAdmin = _schema != null && _schema.getUser().isAdministrator();
        col.setFk(new LookupForeignKey("UserId", "DisplayName")
        {
            public TableInfo getLookupTableInfo()
            {
                String tableName = isSiteAdmin ? "SiteUsers" : "Users";
                return QueryService.get().getUserSchema(_schema.getUser(), _schema.getContainer(), "core").getTable(tableName);
            }
        });

        getColumn("Name").setURL(webURLExp);
        getColumn("Title").setURL(webURLExp);
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
