/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.study.assay.query;

import org.apache.log4j.Logger;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.SimpleAuditViewFactory;
import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.data.*;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupURLExpression;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.User;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.study.assay.AssayPublishManager;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.Study;
import org.labkey.study.model.StudyManager;

import java.io.IOException;
import java.io.Writer;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Oct 16, 2007
 *
 * Event field documentation:
 *
 * createdBy - User who created the record
 * created - Timestamp
 * comment - record description
 * container - container id of the source lsid (publish source)
 * intKey1 - the protocol id
 * key1 - the target study container id
 *
 * datasetId - the dataset id of the publish target (ontology table prop)
 * sourceLsid - publish event sourceLsid
 * recordCount - the original number of records published
 */
public class AssayAuditViewFactory extends SimpleAuditViewFactory
{
    private static final Logger _log = Logger.getLogger(AssayAuditViewFactory.class);
    private static final AssayAuditViewFactory _instance = new AssayAuditViewFactory();

    private AssayAuditViewFactory(){}

    public static AssayAuditViewFactory getInstance()
    {
        return _instance;
    }

    public String getEventType()
    {
        return AssayPublishManager.ASSAY_PUBLISH_AUDIT_EVENT;
    }

    public String getName()
    {
        return "Copy-to-Study Assay events";
    }

    public QueryView createDefaultQueryView(ViewContext context)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition("EventType", AssayPublishManager.ASSAY_PUBLISH_AUDIT_EVENT);

        AuditLogQueryView view = AuditLogService.get().createQueryView(context, filter, AssayPublishManager.ASSAY_PUBLISH_AUDIT_EVENT);
        view.setButtonBarPosition(DataRegion.ButtonBarPosition.BOTH);
        view.setSort(new Sort("-Date"));

        return view;
    }

    public AuditLogQueryView createPublishHistoryView(ViewContext context, int protocolId)
    {
        SimpleFilter filter = new SimpleFilter();
        if (protocolId != -1)
            filter.addCondition("IntKey1", protocolId);
        filter.addCondition("EventType", AssayPublishManager.ASSAY_PUBLISH_AUDIT_EVENT);
        filter.addCondition("ContainerId", context.getContainer().getId());

        AuditLogQueryView view = AuditLogService.get().createQueryView(context, filter, AssayPublishManager.ASSAY_PUBLISH_AUDIT_EVENT);
        view.setSort(new Sort("-Date"));

        return view;
    }

    public List<FieldKey> getDefaultVisibleColumns()
    {
        List<FieldKey> columns = new ArrayList();

        columns.add(FieldKey.fromParts("Date"));
        columns.add(FieldKey.fromParts("CreatedBy"));
        columns.add(FieldKey.fromParts("Key1"));
        columns.add(FieldKey.fromParts("Comment"));

        return columns;
    }

    public void setupTable(TableInfo table)
    {
        ColumnInfo col = table.getColumn("Key1");

        if (col != null)
        {
            col.setCaption("Target Study");
            col.setDisplayColumnFactory(new DisplayColumnFactory()
            {
                public DisplayColumn createRenderer(ColumnInfo colInfo)
                {
                    return new TargetStudyColumn(colInfo);
                }
            });
        }

        col = table.getColumn("Property");
        if (col != null)
        {
            col.setIsHidden(true);
            col.setIsUnselectable(true);
        }
    }

    public void setupView(DataView view)
    {
        ColumnInfo col = view.getTable().getColumn("Property");
        if (col != null)
        {
            List<FieldKey> keys = new ArrayList<FieldKey>();
            keys.add(FieldKey.fromParts("Property", "sourceLsid"));
            keys.add(FieldKey.fromParts("Property", "datasetId"));
            keys.add(FieldKey.fromParts("Property", "recordCount"));

            Map<String, ColumnInfo> params = new HashMap<String, ColumnInfo>();
            for (Map.Entry<FieldKey, ColumnInfo> entry : QueryService.get().getColumns(view.getTable(), keys).entrySet())
            {
                params.put(entry.getKey().getName(), entry.getValue());
            }

            params.put("protocolId", view.getTable().getColumn("IntKey1"));
            ColumnInfo containerId = view.getTable().getColumn("Key1");

            view.getDataRegion().addDisplayColumn(0, new PublishDetailsColumn(params, containerId));
        }
    }

    public static class TargetStudyColumn extends DataColumn
    {
        public TargetStudyColumn(ColumnInfo col)
        {
            super(col);
        }

        public String getName()
        {
            return "targetStudy";
        }

        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            String id = (String)getBoundColumn().getValue(ctx);
            Integer datasetId = (Integer)ctx.get("property_datasetId");
            Container c = ContainerManager.getForId(id);
            if (c != null && datasetId != null)
            {
                Study s = StudyManager.getInstance().getStudy(c);
                if (s != null)
                {
                    out.write("<a href=\"" +
                            new ActionURL("Study", "dataset", c).addParameter(DataSetDefinition.DATASETKEY, datasetId) + "\">");
                    out.write(s.getLabel().replaceAll(" ", "&nbsp;") + "</a>");
                }
            }
            out.write("&nbsp;");
        }
    }

    private static class PublishDetailsColumn extends DetailsColumn
    {
        private Map<String, ColumnInfo> _columns;
        private ColumnInfo _containerId;

        public PublishDetailsColumn(Map<String, ColumnInfo> columns, ColumnInfo containerId)
        {
            super(null);

            _columns = columns;
            _containerId = containerId;
        }

        @Override
        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            ColumnInfo col = _columns.get("sourceLsid");
            String containerId = (String)_containerId.getValue(ctx);
            Container c = ContainerManager.getForId(containerId);

            if (c != null && col != null && ctx.get(col.getAlias()) != null)
                super.renderGridCellContents(ctx, out);
            else
                out.write("&nbsp;");
        }

        public String getURL(RenderContext ctx)
        {
            String containerId = (String)_containerId.getValue(ctx);
            Container c = ContainerManager.getForId(containerId);
            if (c == null)
                return null;

            return new LookupURLExpression(new ActionURL("Study", "publishHistoryDetails", c), _columns).eval(ctx);
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
    }

    public void ensureDomain(User user)
    {
        Container c = ContainerManager.getSharedContainer();
        String domainURI = AuditLogService.get().getDomainURI(AssayPublishManager.ASSAY_PUBLISH_AUDIT_EVENT);

        Domain domain = PropertyService.get().getDomain(c, domainURI);
        if (domain == null)
        {
            try {
                domain = PropertyService.get().createDomain(c, domainURI, "PublishAuditEventDomain");
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
            try {
                ensureProperties(user, domain, new PropertyInfo[]{
                        new PropertyInfo("datasetId", "Dataset Id", PropertyType.INTEGER),
                        new PropertyInfo("sourceLsid", "Source LSID", PropertyType.STRING),
                        new PropertyInfo("recordCount", "Record Count", PropertyType.INTEGER),
                        new PropertyInfo("targetContainer", "Target Container", PropertyType.STRING)
                });
            }
            catch (Exception e)
            {
                _log.error(e);
            }
        }
    }
}
