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

package org.labkey.api.exp.property;

import org.apache.log4j.Logger;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.SimpleAuditViewFactory;
import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.data.*;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ViewContext;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * User: Karl Lum
 * Date: Nov 5, 2007
 *
 * Event field documentation:
 *
 * created - Timestamp
 * createdBy - User who created the record
 * impersonatedBy - user who was impersonating the user (or null)
 * comment - record description
 * projectId - the project id
 * container - container id of the domain event
 * key1 - the domain uri
 * key3 - the domain name
 */
public class DomainAuditViewFactory extends SimpleAuditViewFactory
{
    private static final Logger _log = Logger.getLogger(DomainAuditViewFactory.class);
    private static final DomainAuditViewFactory _instance = new DomainAuditViewFactory();
    public static final String DOMAIN_AUDIT_EVENT = "DomainAuditEvent";

    private DomainAuditViewFactory(){}

    public static DomainAuditViewFactory getInstance()
    {
        return _instance;
    }

    public String getEventType()
    {
        return DOMAIN_AUDIT_EVENT;
    }

    public String getName()
    {
        return "Domain events";
    }

    public QueryView createDefaultQueryView(ViewContext context)
    {
        AuditLogQueryView view = AuditLogService.get().createQueryView(context, null, getEventType());
        view.setSort(new Sort("-Date"));

        return view;
    }

    public List<FieldKey> getDefaultVisibleColumns()
    {
        List<FieldKey> columns = new ArrayList<>();

        columns.add(FieldKey.fromParts("Date"));
        columns.add(FieldKey.fromParts("CreatedBy"));
        columns.add(FieldKey.fromParts("ImpersonatedBy"));
        columns.add(FieldKey.fromParts("ProjectId"));
        columns.add(FieldKey.fromParts("Key1"));
        columns.add(FieldKey.fromParts("Comment"));

        return columns;
    }

    public void setupTable(final FilteredTable table, UserSchema schema)
    {
        super.setupTable(table, schema);
        final ColumnInfo containerId = table.getColumn("ContainerId");
        ColumnInfo col = table.getColumn("Key1");
        col.setLabel("Domain");
        col.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new DomainColumn(colInfo, containerId, table.getColumn("Key3"));
            }
        });
    }

    public static class DomainColumn extends DataColumn
    {
        private ColumnInfo _containerId;
        private ColumnInfo _defaultName;

        public DomainColumn(ColumnInfo col, ColumnInfo containerId, ColumnInfo defaultName)
        {
            super(col);
            _containerId = containerId;
            _defaultName = defaultName;
        }

        public String getName()
        {
            return getColumnInfo().getLabel();
        }

        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            String uri = (String)getBoundColumn().getValue(ctx);
            String cId = (String)ctx.get("ContainerId");
            if (cId == null)
                cId = (String)ctx.get("Container");

            if (uri != null && cId != null)
            {
                Container c = ContainerManager.getForId(cId);
                if (c != null)
                {
                    Domain domain = PropertyService.get().getDomain(c, uri);
                    if (domain != null)
                    {
                        DomainKind kind = PropertyService.get().getDomainKind(domain.getTypeURI());
                        if (kind != null)
                            out.write("<a href=\"" + kind.urlShowData(domain, ctx.getViewContext()) + "\">" + PageFlowUtil.filter(domain.getName()) + "</a>");
                        else
                            out.write(PageFlowUtil.filter(domain.getName()));
                        return;
                    }
                }
            }

            if (_defaultName != null)
                out.write(Objects.toString(PageFlowUtil.filter(_defaultName.getValue(ctx)), "&nbsp;").toString());
            else
                out.write("&nbsp;");
        }

        public void addQueryColumns(Set<ColumnInfo> columns)
        {
            super.addQueryColumns(columns);
            if (_containerId != null)
                columns.add(_containerId);
            if (_defaultName != null)
                columns.add(_defaultName);
        }

        public boolean isFilterable()
        {
            return false;
        }
    }
}
