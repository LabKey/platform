/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.audit;

import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.data.*;
import org.labkey.api.settings.WriteableAppProps;

import java.util.List;
import java.util.ArrayList;
import java.io.Writer;
import java.io.IOException;

/*
 * User: Dave
 * Date: May 27, 2008
 * Time: 3:10:03 PM
 */

public class SiteSettingsAuditViewFactory implements AuditLogService.AuditViewFactory
{
    public String getEventType()
    {
        return WriteableAppProps.AUDIT_EVENT_TYPE;
    }

    public String getName()
    {
        return "Site Settings events";
    }

    public String getDescription()
    {
        return "Displays information about modifications to the site settings.";
    }

    public QueryView createDefaultQueryView(ViewContext context)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition("EventType", WriteableAppProps.AUDIT_EVENT_TYPE);

        AuditLogQueryView view = AuditLogService.get().createQueryView(context, filter, getEventType());
        view.setSort(new Sort("-Date"));
        view.setButtonBarPosition(DataRegion.ButtonBarPosition.BOTH);

        return view;
    }

    public List<FieldKey> getDefaultVisibleColumns()
    {
        List<FieldKey> columns = new ArrayList<FieldKey>();

        columns.add(FieldKey.fromParts("Date"));
        columns.add(FieldKey.fromParts("CreatedBy"));
        columns.add(FieldKey.fromParts("ImpersonatedBy"));
        columns.add(FieldKey.fromParts("Comment"));

        return columns;
    }

    public void setupTable(TableInfo table)
    {
        //nothing yet
    }

    public void setupView(DataView view)
    {
        DataRegion dataRegion = view.getDataRegion();
        TableInfo table = dataRegion.getTable();
        ColumnInfo idCol = table.getColumn("RowId");

        ExprColumn detailsCol = new ExprColumn(table, "details", idCol.getValueSql(ExprColumn.STR_TABLE_ALIAS), idCol.getSqlTypeInt());
        detailsCol.copyAttributesFrom(idCol);
        detailsCol.setLabel("Details");
        detailsCol.setDisplayColumnFactory(new DisplayColumnFactory(){
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new DetailsDisplayColumn(colInfo);
            }
        });

        dataRegion.addColumn(0, detailsCol);
    }

    private class DetailsDisplayColumn extends DataColumn
    {
        ActionURL _urlDetails = new ActionURL(AuditController.ShowSiteSettingsAuditDetailsAction.class, ContainerManager.getRoot());

        DetailsDisplayColumn(ColumnInfo column)
        {
            super(column);
        }

        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            Object val = getValue(ctx);
            if(null == val)
                return;

            _urlDetails.replaceParameter("id", val.toString());

            out.write("[<a href='");
            out.write(_urlDetails.getLocalURIString());
            out.write("'>details</a>]");
        }

        public void renderTitle(RenderContext ctx, Writer out) throws IOException
        {
            //don't display a title
            out.write("&nbsp;");
        }

        public boolean isSortable()
        {
            return false;
        }

        public boolean isFilterable()
        {
            return false;
        }
    }
}