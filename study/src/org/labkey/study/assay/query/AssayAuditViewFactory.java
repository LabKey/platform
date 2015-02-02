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

package org.labkey.study.assay.query;

import org.apache.log4j.Logger;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.SimpleAuditViewFactory;
import org.labkey.api.audit.data.ProtocolColumn;
import org.labkey.api.audit.data.RunColumn;
import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.data.*;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.*;
import org.labkey.api.security.User;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.study.Study;
import org.labkey.api.writer.ContainerUser;
import org.labkey.study.StudySchema;
import org.labkey.study.assay.AssayPublishManager;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.StudyManager;

import java.io.IOException;
import java.io.Writer;
import java.util.*;

/**
 * User: Karl Lum
 * Date: Oct 16, 2007
 *
 * Event field documentation:
 *
 * created - Timestamp
 * createdBy - User who created the record
 * impersonatedBy - user who was impersonating the user (or null)
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

    @Override
    public String getDescription()
    {
        return "Information about copy-to-study Assay events.";
    }

    public QueryView createDefaultQueryView(ViewContext context)
    {
        AuditLogQueryView view = AuditLogService.get().createQueryView(context, null, AssayPublishManager.ASSAY_PUBLISH_AUDIT_EVENT);
        view.setSort(new Sort("-Date"));
        addDetailsColumn(view);

        return view;
    }

    public AuditLogQueryView createPublishHistoryView(ViewContext context, int protocolId, ContainerFilter containerFilter)
    {
        SimpleFilter filter = new SimpleFilter();
        if (protocolId != -1)
            filter.addCondition(FieldKey.fromParts("IntKey1"), protocolId);
        filter.addCondition(containerFilter.createFilterClause(ExperimentService.get().getSchema(), FieldKey.fromParts("ContainerId"), context.getContainer()));

        AuditLogQueryView view = AuditLogService.get().createQueryView(context, filter, AssayPublishManager.ASSAY_PUBLISH_AUDIT_EVENT);
        view.setSort(new Sort("-Date"));
        addDetailsColumn(view);

        return view;
    }

    public List<FieldKey> getDefaultVisibleColumns()
    {
        List<FieldKey> columns = new ArrayList<>();

        columns.add(FieldKey.fromParts("Date"));
        columns.add(FieldKey.fromParts("CreatedBy"));
        columns.add(FieldKey.fromParts("ImpersonatedBy"));
        columns.add(FieldKey.fromParts("IntKey1"));
        columns.add(FieldKey.fromParts("Run"));
        columns.add(FieldKey.fromParts("TargetStudy"));
        columns.add(FieldKey.fromParts("Comment"));

        return columns;
    }

    public void setupTable(final FilteredTable table, UserSchema schema)
    {
        super.setupTable(table, schema);
        final ColumnInfo containerId = table.getColumn("ContainerId");
        ColumnInfo col = table.getColumn("Key1");
        if (col != null)
        {
            ColumnInfo studyCol = new AliasedColumn(table, "TargetStudy", col);

            LookupForeignKey fk = new LookupForeignKey("Container", "Label") {
                public TableInfo getLookupTableInfo()
                {
                    return StudySchema.getInstance().getTableInfoStudy();
                }
            };
            studyCol.setFk(fk);
            table.addColumn(studyCol);

            ColumnInfo propCol = table.getColumn("Property");
            if (propCol != null)
            {
                final List<FieldKey> keys = new ArrayList<>();
                keys.add(FieldKey.fromParts("Property", "sourceLsid"));
                keys.add(FieldKey.fromParts("Property", "datasetId"));
                keys.add(FieldKey.fromParts("Property", "recordCount"));

                studyCol.setDisplayColumnFactory(new DisplayColumnFactory()
                {
                    public DisplayColumn createRenderer(ColumnInfo colInfo)
                    {
                        return new TargetStudyColumn(colInfo, QueryService.get().getColumns(table, keys));
                    }
                });
            }
        }

        col = table.getColumn("Property");
        if (col != null)
        {
            col.setHidden(true);
            col.setIsUnselectable(true);
        }

        // protocol column
        ColumnInfo protocolCol = table.getColumn("IntKey1");
        protocolCol.setLabel("Assay/Protocol");
        protocolCol.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new ProtocolColumn(colInfo, containerId, null);
            }
        });

        // assay run column
        FieldKey lsidField = FieldKey.fromParts("Property", "sourceLsid");
        Map<FieldKey, ColumnInfo> entry = QueryService.get().getColumns(table, Collections.singletonList(lsidField));

        if (entry.containsKey(lsidField))
        {
            ColumnInfo runCol = entry.get(lsidField);
            runCol.setDisplayColumnFactory(new DisplayColumnFactory()
            {
                public DisplayColumn createRenderer(ColumnInfo colInfo)
                {
                    return new RunColumn(colInfo, containerId, null);
                }
            });

            table.addColumn(new AliasedColumn(runCol.getParentTable(), "Run", runCol));
        }
    }

    private void addDetailsColumn(AuditLogQueryView view)
    {
        ColumnInfo col = view.getTable().getColumn("Property");
        if (col != null)
        {
            List<FieldKey> keys = new ArrayList<>();
            keys.add(FieldKey.fromParts("Property", "sourceLsid"));
            keys.add(FieldKey.fromParts("Property", "datasetId"));
            keys.add(FieldKey.fromParts("Property", "recordCount"));

            Map<String, ColumnInfo> params = new HashMap<>();
            for (Map.Entry<FieldKey, ColumnInfo> entry : QueryService.get().getColumns(view.getTable(), keys).entrySet())
            {
                params.put(entry.getKey().getName(), entry.getValue());
            }

            params.put("protocolId", view.getTable().getColumn("IntKey1"));
            ColumnInfo containerId = view.getTable().getColumn("Key1");

            view.addDisplayColumn(0, new PublishDetailsColumn(params, containerId));
        }
    }

    public static class TargetStudyColumn extends DataColumn
    {
        Map<FieldKey, ColumnInfo> _params;
        public TargetStudyColumn(ColumnInfo col, Map<FieldKey, ColumnInfo> params)
        {
            super(col);
            _params = params;
        }

        public String getName()
        {
            return "targetStudy";
        }

        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            String id = (String)getBoundColumn().getValue(ctx);
            ColumnInfo datasetCol = _params.get(FieldKey.fromParts("Property", "datasetId"));

            if (datasetCol != null)
            {
                Integer datasetId = (Integer)ctx.get(datasetCol.getAlias());
                Container c = ContainerManager.getForId(id);
                if (c != null && datasetId != null)
                {
                    Study s = StudyManager.getInstance().getStudy(c);
                    if (s != null)
                    {
                        out.write("<a href=\"" +
                                new ActionURL(StudyController.DatasetAction.class, c).addParameter(DatasetDefinition.DATASETKEY, datasetId) + "\">");
                        out.write(s.getLabel().replaceAll(" ", "&nbsp;") + "</a>");
                    }
                }
            }
            out.write("&nbsp;");
        }

        @Override
        public void addQueryFieldKeys(Set<FieldKey> keys)
        {
            super.addQueryFieldKeys(keys);
            keys.addAll(_params.keySet());
        }
    }

    private static class PublishDetailsColumn extends DetailsColumn
    {
        private Map<String, ColumnInfo> _columns;
        private ColumnInfo _containerId;

        public PublishDetailsColumn(Map<String, ColumnInfo> columns, ColumnInfo containerId)
        {
            super(null, null);

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

        @Override
        public String renderURL(RenderContext ctx)
        {
            String containerId = (String)_containerId.getValue(ctx);
            Container c = ContainerManager.getForId(containerId);
            if (c == null)
                return null;

            DetailsURL url = new DetailsURL(new ActionURL(StudyController.PublishHistoryDetailsAction.class, c), _columns);
            url.setContainerContext(c);
            return url.eval(ctx);
        }

        @Override
        public boolean isVisible(RenderContext ctx)
        {
            return true;
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

    @Override
    public void initialize(ContainerUser context) throws Exception
    {
        ensureDomain(context.getUser());
    }

    private void ensureDomain(User user) throws Exception
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
            ensureProperties(user, domain, new PropertyInfo[]{
                    new PropertyInfo("datasetId", "Dataset Id", PropertyType.INTEGER),
                    new PropertyInfo("sourceLsid", "Source LSID", PropertyType.STRING),
                    new PropertyInfo("recordCount", "Record Count", PropertyType.INTEGER),
                    new PropertyInfo("targetContainer", "Target Container", PropertyType.STRING)
            });
        }
    }
}
