/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

package org.labkey.study.dataset;

import org.apache.log4j.Logger;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.SimpleAuditViewFactory;
import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.data.*;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryView;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.DataSet;
import org.labkey.study.controllers.DatasetController;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.DataSetDefinition;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * IntKey1 is the dataset row id
 * IntKey2 is 0 if no details are available
 * Key1 is the UploadLog path (if set)
 *
 * User: jgarms
 */
public class DatasetAuditViewFactory extends SimpleAuditViewFactory
{
    public static final String DATASET_AUDIT_EVENT = "DatasetAuditEvent";

    private static final DatasetAuditViewFactory INSTANCE = new DatasetAuditViewFactory();

    static final Logger LOG = Logger.getLogger(DatasetAuditViewFactory.class);

    private DatasetAuditViewFactory() {}

    public static DatasetAuditViewFactory getInstance()
    {
        return INSTANCE;
    }

    public String getName()
    {
        return "Dataset events";
    }

    public String getEventType()
    {
        return DATASET_AUDIT_EVENT;
    }

    public QueryView createDefaultQueryView(ViewContext context)
    {
        SimpleFilter filter = new SimpleFilter();

        filter.addCondition("EventType", DATASET_AUDIT_EVENT, CompareType.EQUAL);

        AuditLogQueryView view = AuditLogService.get().createQueryView(context, filter, getEventType());
        view.setSort(new Sort("-Date"));
        view.setButtonBarPosition(DataRegion.ButtonBarPosition.BOTH);

        return view;
    }

    public AuditLogQueryView createDatasetView(ViewContext context, DataSetDefinition def)
    {
        SimpleFilter filter = new SimpleFilter("IntKey1", def.getRowId());
        filter.addCondition("EventType", DATASET_AUDIT_EVENT, CompareType.EQUAL);

        AuditLogQueryView view = AuditLogService.get().createQueryView(context, filter, getEventType());
        view.setSort(new Sort("-Date"));
        view.setTitle("<br/><b>Dataset Snapshot History:</b>");

        return view;
    }
    
    @Override
    public void setupTable(final TableInfo table)
    {
        final ColumnInfo containerColumn = table.getColumn("ContainerId");
        final ColumnInfo datasetDefColumn = table.getColumn("IntKey1");
        datasetDefColumn.setLabel("Dataset");

        datasetDefColumn.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(final ColumnInfo colInfo)
            {
                return new DataColumn(colInfo)
                {
                    public void addQueryColumns(Set<ColumnInfo> columns)
                    {
                        columns.add(containerColumn);
                        super.addQueryColumns(columns);
                    }

                    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
                    {
                        Object containerId = containerColumn.getValue(ctx);
                        Integer datasetId = (Integer)getValue(ctx);
                        if (datasetId == null)
                            return;

                        Container container = ContainerManager.getForId(containerId.toString());
                        if (container == null)
                        {
                            return;
                        }

                        Study study = StudyService.get().getStudy(container);
                        if (study == null)
                        {
                            return;
                        }

                        DataSet def = study.getDataSet(datasetId.intValue());
                        if (def == null)
                        {
                            return;
                        }

                        ActionURL url = new ActionURL(StudyController.DatasetAction.class,
                                container);
                        url.addParameter("datasetId", datasetId.toString());

                        out.write("<a href=\"");
                        out.write(PageFlowUtil.filter(url.getLocalURIString()));
                        out.write("\">");
                        out.write(def.getName());
                        out.write("</a>");

                    }
                };
            }
        });
    }

    @Override
    public void setupView(DataView view)
    {
        TableInfo table = view.getTable();
        ColumnInfo detailBitCol = table.getColumn("IntKey2");
        ColumnInfo containerCol = table.getColumn("ContainerId");
        ColumnInfo rowCol = table.getColumn("RowId");
        view.getDataRegion().addDisplayColumn(0, new DetailsColumn(rowCol, containerCol, detailBitCol));
    }

    public List<FieldKey> getDefaultVisibleColumns()
    {
        List<FieldKey> columns = new ArrayList<FieldKey>();

        columns.add(FieldKey.fromParts("Date"));
        columns.add(FieldKey.fromParts("CreatedBy"));
        columns.add(FieldKey.fromParts("ImpersonatedBy"));
        columns.add(FieldKey.fromParts("ProjectId"));
        columns.add(FieldKey.fromParts("ContainerId"));
        columns.add(FieldKey.fromParts("IntKey1"));
        columns.add(FieldKey.fromParts("Comment"));

        return columns;
    }

    public void ensureDomain(User user)
    {
        Container c = ContainerManager.getSharedContainer();
        String domainURI = AuditLogService.get().getDomainURI(DATASET_AUDIT_EVENT);

        Domain domain = PropertyService.get().getDomain(c, domainURI);
        if (domain == null)
        {
            try
            {
                domain = PropertyService.get().createDomain(c, domainURI, "DatasetAuditEventDomain");
                domain.save(user);
                domain = PropertyService.get().getDomain(c, domainURI);
            }
            catch (Exception e)
            {
                LOG.error(e);
            }
        }

        if (domain != null)
        {
            try
            {
                ensureProperties(user, domain, new PropertyInfo[]{
                        new PropertyInfo("oldRecordMap", "Old Record Map", PropertyType.STRING),
                        new PropertyInfo("newRecordMap", "New Record Map", PropertyType.STRING)

                });
            }
            catch (Exception e)
            {
                LOG.error(e);
            }
        }
    }

    private static class DetailsColumn extends DataColumn
    {
        private final ColumnInfo containerCol;
        private final ColumnInfo rowCol;

        public DetailsColumn(ColumnInfo rowCol, ColumnInfo containerCol, ColumnInfo detailBitCol)
        {
            super(detailBitCol);
            this.containerCol = containerCol;
            this.rowCol = rowCol;
        }

        public void renderTitle(RenderContext ctx, Writer out) throws IOException
        {
            out.write("&nbsp;");
        }
        
        public boolean isFilterable()
        {
            return false;
        }

        public void addQueryColumns(Set<ColumnInfo> columns)
        {
            columns.add(containerCol);
            columns.add(rowCol);
            super.addQueryColumns(columns);
        }

        public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
        {
            Integer detailBit = (Integer)getValue(ctx);
            if (detailBit == null || detailBit.intValue() == 0)
            {
                // No details
                return;
            }

            Integer rowId = (Integer)rowCol.getValue(ctx);
            String containerId = containerCol.getValue(ctx).toString();

            Container c = ContainerManager.getForId(containerId);
            if (c == null)
                return;

            ActionURL url = new ActionURL(DatasetController.DatasetAuditHistoryAction.class, c);
            url.addParameter("auditRowId", rowId.intValue());

            out.write("[<a href=\"");
            out.write(PageFlowUtil.filter(url.getLocalURIString()));
            out.write("\">details</a>]");

        }
    }
}
