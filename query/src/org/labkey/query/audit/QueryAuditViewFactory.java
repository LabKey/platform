/*
 * Copyright (c) 2012-2015 LabKey Corporation
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
package org.labkey.query.audit;

import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.SimpleAuditViewFactory;
import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DetailsColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Sort;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.query.controllers.QueryController;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Event field documentation:
 *
 * key1 - schemaName
 * key2 - queryName
 * key3 - DetailsURL including sort/filter parameters.
 *
 * User: kevink
 * Date: 6/15/12
 */
public class QueryAuditViewFactory extends SimpleAuditViewFactory
{
    public static final String QUERY_AUDIT_EVENT = "QueryExportAuditEvent";

    private static final QueryAuditViewFactory INSTANCE = new QueryAuditViewFactory();

    private QueryAuditViewFactory() { }

    public static QueryAuditViewFactory getInstance()
    {
        return INSTANCE;
    }

    @Override
    public String getName()
    {
        return "Query export events";
    }

    @Override
    public String getEventType()
    {
        return QUERY_AUDIT_EVENT;
    }

    @Override
    public QueryView createDefaultQueryView(ViewContext context)
    {
        AuditLogQueryView view = AuditLogService.get().createQueryView(context, null, getEventType());
        view.setSort(new Sort("-Date"));
        addDetailsColumn(view);
        return view;
    }

    private void addDetailsColumn(AuditLogQueryView view)
    {
        ColumnInfo containerId = view.getTable().getColumn("ContainerId");

        ColumnInfo schemaCol = view.getTable().getColumn("Key1");
        ColumnInfo queryCol = view.getTable().getColumn("Key2");
        ColumnInfo sortFilterCol = view.getTable().getColumn("Key3");

        view.addDisplayColumn(0, new QueryDetailsColumn(containerId, schemaCol, queryCol, sortFilterCol));
    }

    public List<FieldKey> getDefaultVisibleColumns()
    {
        List<FieldKey> columns = new ArrayList<>();

        columns.add(FieldKey.fromParts("Date"));
        columns.add(FieldKey.fromParts("CreatedBy"));
        columns.add(FieldKey.fromParts("ImpersonatedBy"));
        columns.add(FieldKey.fromParts("ProjectId"));
        columns.add(FieldKey.fromParts("ContainerId"));
        columns.add(FieldKey.fromParts("Key1"));
        columns.add(FieldKey.fromParts("Key2"));
        columns.add(FieldKey.fromParts("IntKey1"));
        columns.add(FieldKey.fromParts("Comment"));

        return columns;
    }

    @Override
    public void setupTable(FilteredTable table, UserSchema schema)
    {
        super.setupTable(table, schema);

        ColumnInfo schemaCol = table.getColumn("Key1");
        schemaCol.setLabel("SchemaName");

        ColumnInfo queryCol = table.getColumn("Key2");
        queryCol.setLabel("QueryName");

        ColumnInfo sortFilterCol = table.getColumn("Key3");
        sortFilterCol.setLabel("URL");

        table.getColumn("IntKey1").setLabel("Data Row Count");
        table.getColumn("IntKey2").setHidden(true);
        table.getColumn("IntKey3").setHidden(true);
    }

    public static class QueryDetailsColumn extends DetailsColumn
    {
        ColumnInfo _containerCol, _schemaCol, _queryCol, _sortFilterCol;

        public QueryDetailsColumn(ColumnInfo containerCol, ColumnInfo schemaCol, ColumnInfo queryCol, ColumnInfo sortFilterCol)
        {
            super(null, null);
            _containerCol = containerCol;
            _schemaCol = schemaCol;
            _queryCol = queryCol;
            _sortFilterCol = sortFilterCol;
        }

        @Override
        public void addQueryFieldKeys(Set<FieldKey> keys)
        {
            super.addQueryFieldKeys(keys);
            keys.add(_containerCol.getFieldKey());
            keys.add(_schemaCol.getFieldKey());
            keys.add(_queryCol.getFieldKey());
            keys.add(_sortFilterCol.getFieldKey());
        }

        @Override
        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            String containerId = (String)ctx.get("ContainerId");
            Container c = ContainerManager.getForId(containerId);

            String schemaName = ctx.get(_schemaCol.getFieldKey(), String.class);
            String queryName = ctx.get(_queryCol.getFieldKey(), String.class);

            if (c != null && schemaName != null && queryName != null)
            {
                super.renderGridCellContents(ctx, out);
            }
            out.write("&nbsp;");
        }

        @Override
        public String renderURL(RenderContext ctx)
        {
            // NOTE: Not all grid work well with executeQuery yet (ms2, specimen).
            // Ideally we would use the URL returned from queryDef.urlFor(QueryAction.executeQuery)
            // but constructing the table for each row is too expensive.
            // Until we can cheaply get table URLs, the QueryController.ExecuteQuery will have to do.

            String containerId = (String)ctx.get("ContainerId");
            Container c = ContainerManager.getForId(containerId);
            if (c == null)
                return null;

            String schemaName = ctx.get(_schemaCol.getFieldKey(), String.class);
            String queryName = ctx.get(_queryCol.getFieldKey(), String.class);
            if (schemaName == null || queryName == null)
                return null;

            ActionURL url = new ActionURL(QueryController.ExecuteQueryAction.class, c);

            // Apply the sorts and filters
            String sortFilter = ctx.get(_sortFilterCol.getFieldKey(), String.class);
            if (sortFilter != null)
            {
                // Issue 18605: IllegalArgumentException thrown when parsing the query parameters
                try
                {
                    ActionURL sortFilterURL = new ActionURL(sortFilter);
                    url.setPropertyValues(sortFilterURL.getPropertyValues());
                }
                catch (IllegalArgumentException e)
                {
                    return null;
                }
            }

            if (url.getParameter(QueryParam.schemaName) == null)
                url.addParameter(QueryParam.schemaName, schemaName);
            if (url.getParameter(QueryParam.queryName) == null && url.getParameter(QueryView.DATAREGIONNAME_DEFAULT + "." + QueryParam.queryName) == null)
                url.addParameter(QueryParam.queryName, queryName);

            return url.toString();
        }

        @Override
        public boolean isVisible(RenderContext ctx)
        {
            return true;
        }
    }
}
