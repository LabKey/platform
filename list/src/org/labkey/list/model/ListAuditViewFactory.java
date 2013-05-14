/*
 * Copyright (c) 2007-2012 LabKey Corporation
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

package org.labkey.list.model;

import org.apache.log4j.Logger;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.SimpleAuditViewFactory;
import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DetailsColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainAuditViewFactory;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;
import org.labkey.list.controllers.ListController;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: Karl Lum
 * Date: Oct 22, 2007
 *
 * Event field documentation:
 *
 * created - Timestamp
 * createdBy - User who created the record
 * impersonatedBy - user who was impersonating the user (or null)
 * comment - record description
 * projectId - the project id
 * container - container id of the domain event
 * intKey1 - the list id
 * key1 - the domain uri
 * key2 - the item entityId
 * key3 - the list name
 *
 * listItemKey - the item key of the record that was modified, inserted, deleted (ontology table prop)
 *
 */
public class ListAuditViewFactory extends SimpleAuditViewFactory
{
    private static final Logger _log = Logger.getLogger(ListAuditViewFactory.class);
    private static final ListAuditViewFactory _instance = new ListAuditViewFactory();

    private ListAuditViewFactory(){}

    public static ListAuditViewFactory getInstance()
    {
        return _instance;
    }

    public String getEventType()
    {
        return ListManager.LIST_AUDIT_EVENT;
    }

    public String getName()
    {
        return "List events";
    }

    public QueryView createDefaultQueryView(ViewContext context)
    {
        AuditLogQueryView view = AuditLogService.get().createQueryView(context, null, getEventType());
        view.setSort(new Sort("-Date"));
        addDetailsColumn(view);

        return view;
    }

    public AuditLogQueryView createListItemDetailsView(ViewContext context, String entityId)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition("Key2", entityId);

        AuditLogQueryView view = AuditLogService.get().createQueryView(context, filter, getEventType());
        view.setTitle("List Item History:");
        view.setSort(new Sort("-Date"));
        view.setVisibleColumns(new String[]{"Date", "CreatedBy", "ImpersonatedBy", "Comment"});
        addDetailsColumn(view);

        return view;
    }

    private void addDetailsColumn(AuditLogQueryView view)
    {
        Map<String, ColumnInfo> params = new HashMap<String, ColumnInfo>();

        params.put("listId", view.getTable().getColumn("IntKey1"));
        params.put("entityId", view.getTable().getColumn("Key2"));
        params.put("rowId", view.getTable().getColumn("RowId"));
        ColumnInfo containerId = view.getTable().getColumn("ContainerId");

        view.addDisplayColumn(0, new ListItemDetailsColumn(params, containerId));
    }

    public List<FieldKey> getDefaultVisibleColumns()
    {
        List<FieldKey> columns = new ArrayList<FieldKey>();

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
        SimpleFilter filter = new SimpleFilter();

        SimpleFilter.OrClause or = new SimpleFilter.OrClause();
        or.addClause(new CompareType.CompareClause(FieldKey.fromParts("EventType"), CompareType.EQUAL, ListManager.LIST_AUDIT_EVENT));
        or.addClause(new CompareType.CompareClause(FieldKey.fromParts("EventType"), CompareType.EQUAL, DomainAuditViewFactory.DOMAIN_AUDIT_EVENT));
        filter.addClause(or);

        // try to filter on just list domains
        filter.addCondition(FieldKey.fromParts("Key1"), ":" + ListDefinitionImpl.NAMESPACE_PREFIX + ".", CompareType.CONTAINS);

        table.addCondition(filter);

        final ColumnInfo containerId = table.getColumn("ContainerId");
        ColumnInfo col = table.getColumn("Key1");
        col.setLabel("List");
        col.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new DomainAuditViewFactory.DomainColumn(colInfo, containerId, table.getColumn("Key3"));
            }
        });

        col = table.getColumn("Property");
        if (col != null)
        {
            col.setHidden(true);
            col.setIsUnselectable(true);
        }
    }

    public AuditLogQueryView createListHistoryView(ViewContext context, ListDefinition def)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition("Key1", def.getDomain().getTypeURI());

        AuditLogQueryView view = AuditLogService.get().createQueryView(context, filter, getEventType());
        view.setSort(new Sort("-Date"));
        addDetailsColumn(view);

        return view;
    }

    private static class ListItemDetailsColumn extends DetailsColumn
    {
        private Map<String, ColumnInfo> _columns;
        private ColumnInfo _containerId;

        public ListItemDetailsColumn(Map<String, ColumnInfo> columns, ColumnInfo containerId)
        {
            super(null, null);

            _columns = columns;
            _containerId = containerId;
        }

        @Override
        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            ColumnInfo listId = _columns.get("listId");
            ColumnInfo entityId = _columns.get("entityId");
            String containerId = (String)ctx.get("ContainerId");
            Container c = ContainerManager.getForId(containerId);
            Integer id = (Integer)listId.getValue(ctx);

            if (c != null && id != null)
            {
                String entity = (String)entityId.getValue(ctx);
                if (entity != null)
                {
                    super.renderGridCellContents(ctx, out);
                    return;
                }
            }
            out.write("&nbsp;");
        }

        @Override
        public String renderURL(RenderContext ctx)
        {
            String containerId = (String)ctx.get("ContainerId");
            Container c = ContainerManager.getForId(containerId);
            if (c == null)
                return null;

            ctx.setContainer(c);
            ActionURL url = new ActionURL(ListController.ListItemDetailsAction.class, c);
            url.addParameter(ActionURL.Param.redirectUrl, ctx.getViewContext().getActionURL().getLocalURIString());
            return new DetailsURL(url, _columns).eval(ctx);
        }

        @Override
        public void addQueryColumns(Set<ColumnInfo> set)
        {
            for (Map.Entry<String, ColumnInfo> entry : _columns.entrySet())
            {
                if (entry.getValue() != null)
                    set.add(entry.getValue());
            }
            set.add(_containerId);
        }

        @Override
        public boolean isVisible(RenderContext ctx)
        {
            return true;
        }
    }

    @Override
    public void initialize(ContainerUser context) throws Exception
    {
        ensureDomain(context.getUser());
    }

    private void ensureDomain(User user) throws ChangePropertyDescriptorException
    {
        Container c = ContainerManager.getSharedContainer();
        String domainURI = AuditLogService.get().getDomainURI(ListManager.LIST_AUDIT_EVENT);
        Domain domain = PropertyService.get().getDomain(c, domainURI);

        if (domain == null)
        {
            try
            {
                domain = PropertyService.get().createDomain(c, domainURI, "ListAuditEventDomain");
                domain.save(user);
                domain = PropertyService.get().getDomain(c, domainURI);
            }
            catch (Exception e)
            {
                _log.error(e);
            }
        }

        if (domain != null)
        {
            ensureProperties(user, domain, new PropertyInfo[]{
                    new PropertyInfo("oldRecord", "Old Record", PropertyType.STRING),
                    new PropertyInfo("newRecord", "New Record", PropertyType.STRING),
                    new PropertyInfo(OLD_RECORD_PROP_NAME, OLD_RECORD_PROP_CAPTION, PropertyType.STRING),
                    new PropertyInfo(NEW_RECORD_PROP_NAME, NEW_RECORD_PROP_CAPTION, PropertyType.STRING)
            });
        }
    }
}
