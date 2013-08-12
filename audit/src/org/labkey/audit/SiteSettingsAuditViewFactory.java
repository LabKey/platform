/*
 * Copyright (c) 2008-2013 LabKey Corporation
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
import org.labkey.api.audit.SimpleAuditViewFactory;
import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Sort;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/*
 * User: Dave
 * Date: May 27, 2008
 * Time: 3:10:03 PM
 */

public class SiteSettingsAuditViewFactory extends SimpleAuditViewFactory
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
        AuditLogQueryView view = AuditLogService.get().createQueryView(context, null, getEventType());
        view.setSort(new Sort("-Date"));
        view.setShowDetailsColumn(true);

        return view;
    }

    public List<FieldKey> getDefaultVisibleColumns()
    {
        List<FieldKey> columns = new ArrayList<>();

        columns.add(FieldKey.fromParts("Date"));
        columns.add(FieldKey.fromParts("CreatedBy"));
        columns.add(FieldKey.fromParts("ImpersonatedBy"));
        columns.add(FieldKey.fromParts("Comment"));

        return columns;
    }

    public void setupTable(FilteredTable table, UserSchema schema)
    {
        super.setupTable(table, schema);
        ColumnInfo idCol = table.getColumn("RowId");

        ActionURL url = new ActionURL(AuditController.ShowSiteSettingsAuditDetailsAction.class, table.getContainer());
        table.setDetailsURL(new DetailsURL(url, Collections.singletonMap("id", idCol.getFieldKey())));
    }

    @Override
    public void initialize(ContainerUser context) throws Exception
    {
        AuditLogService.I svc = AuditLogService.get();
        String domainUri = svc.getDomainURI(WriteableAppProps.AUDIT_EVENT_TYPE);
        Container c = ContainerManager.getSharedContainer();

        Domain domain = PropertyService.get().getDomain(c, domainUri);
        if (domain == null)
        {
            domain = PropertyService.get().createDomain(c, domainUri, WriteableAppProps.AUDIT_EVENT_TYPE + "Domain");
            domain.save(context.getUser());
            domain = PropertyService.get().getDomain(c, domainUri);
        }

        if (domain != null)
        {
            ensureProperties(context.getUser(), domain, new PropertyInfo[] {
                new PropertyInfo(WriteableAppProps.AUDIT_PROP_DIFF, null, PropertyType.STRING)
            });
        }
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

            out.write(PageFlowUtil.textLink("details", _urlDetails.getLocalURIString()));
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
