/*
 * Copyright (c) 2008-2015 LabKey Corporation
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
import org.labkey.api.audit.data.DataMapColumn;
import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.DatasetController;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.SecurityType;
import org.labkey.study.model.StudyImpl;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
        AuditLogQueryView view = AuditLogService.get().createQueryView(context, null, getEventType());
        view.setSort(new Sort("-Date"));
        addDetailsColumn(view);

        return view;
    }

    public AuditLogQueryView createDatasetView(ViewContext context, DatasetDefinition def)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("IntKey1"), def.getRowId());

        AuditLogQueryView view = AuditLogService.get().createQueryView(context, filter, getEventType());
        view.setSort(new Sort("-Date"));
        view.setTitle("<br/><b>Dataset Snapshot History:</b>");
        addDetailsColumn(view);

        return view;
    }
    
    @Override
    public void setupTable(final FilteredTable table, UserSchema schema)
    {
        super.setupTable(table, schema);

        ColumnInfo datasetColumn = new AliasedColumn(table, "Dataset", table.getColumn("IntKey1"));
        LookupForeignKey fk = new LookupForeignKey("DatasetId", "Label") {
            public TableInfo getLookupTableInfo()
            {
                return StudySchema.getInstance().getTableInfoDataset();
            }
        };
        fk.addJoin(FieldKey.fromParts("ContainerId"), "container", false);
        datasetColumn.setFk(fk);
        datasetColumn.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(final ColumnInfo colInfo)
            {
                return new DataColumn(colInfo)
                {
                    final FieldKey containerFieldKey = new FieldKey(null, "ContainerId");

                    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
                    {
                        Object containerId = ctx.get(containerFieldKey);
                        Integer datasetId = (Integer)getBoundColumn().getValue(ctx);
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

                        Dataset def = study.getDataset(datasetId.intValue());
                        if (def == null)
                        {
                            return;
                        }

                        ActionURL url = new ActionURL(StudyController.DatasetAction.class, container);
                        url.addParameter("datasetId", datasetId.toString());

                        out.write("<a href=\"");
                        out.write(PageFlowUtil.filter(url.getLocalURIString()));
                        out.write("\">");
                        out.write((String)ctx.get(getDisplayColumn().getFieldKey()));
                        out.write("</a>");
                    }
                };
            }
        });
        table.addColumn(datasetColumn);

        FieldKey oldFieldKey = FieldKey.fromParts("Property", OLD_RECORD_PROP_NAME);
        FieldKey newFieldKey = FieldKey.fromParts("Property", NEW_RECORD_PROP_NAME);

        Map<FieldKey,ColumnInfo> cols = QueryService.get().getColumns(table, Arrays.<FieldKey>asList(oldFieldKey, newFieldKey));
        ColumnInfo oldCol = cols.get(oldFieldKey);
        ColumnInfo newCol = cols.get(newFieldKey);

        if(oldCol != null){
            oldCol.setDisplayColumnFactory(new DisplayColumnFactory()
            {
                public DisplayColumn createRenderer(final ColumnInfo colInfo)
                {
                    return new DataMapColumn(colInfo);
                }
            });
            table.addColumn(new AliasedColumn(table, "OldValues", oldCol));
        }

        if(newCol != null)
        {
            newCol.setDisplayColumnFactory(new DisplayColumnFactory()
            {
                public DisplayColumn createRenderer(final ColumnInfo colInfo)
                {
                    return new DataMapColumn(colInfo);
                }

            });
            table.addColumn(new AliasedColumn(table, "NewValues", newCol));
        }

        restrictDatasetAccess(table, schema.getUser());
    }

    /**
     * issue 14463 : filter the audit records to those that the user has read access to. For basic
     * study security, the container security policy should suffice, for advanced security we
     * need to check the list of datasets the user can read.
     */
    private void restrictDatasetAccess(FilteredTable table, User user)
    {
        Study study = StudyService.get().getStudy(table.getContainer());

        if (study instanceof StudyImpl)
        {
            SecurityType type = ((StudyImpl)study).getSecurityType();

            // create the dataset in clause if we are configured for advanced security
            if (type == SecurityType.ADVANCED_READ || type == SecurityType.ADVANCED_WRITE)
            {
                List<Integer> readDatasets = new ArrayList<>();
                for (Dataset ds : study.getDatasets())
                {
                    if (ds.canRead(user))
                        readDatasets.add(ds.getDatasetId());
                }

                table.addInClause(table.getRealTable().getColumn("IntKey1"), readDatasets);
            }
        }
    }

    private void addDetailsColumn(AuditLogQueryView view)
    {
        TableInfo table = view.getTable();
        ColumnInfo detailBitCol = table.getColumn("IntKey2");
        ColumnInfo containerCol = table.getColumn("ContainerId");
        ColumnInfo rowCol = table.getColumn("RowId");

        view.addDisplayColumn(0, new DetailsColumn(rowCol, containerCol, detailBitCol));
    }

    public List<FieldKey> getDefaultVisibleColumns()
    {
        List<FieldKey> columns = new ArrayList<>();

        columns.add(FieldKey.fromParts("Date"));
        columns.add(FieldKey.fromParts("CreatedBy"));
        columns.add(FieldKey.fromParts("ImpersonatedBy"));
        columns.add(FieldKey.fromParts("ProjectId"));
        columns.add(FieldKey.fromParts("ContainerId"));
        columns.add(FieldKey.fromParts("Dataset"));
        columns.add(FieldKey.fromParts("Comment"));

        return columns;
    }

    private void ensureDomain(User user) throws Exception
    {
        Container c = ContainerManager.getSharedContainer();
        String domainURI = AuditLogService.get().getDomainURI(DATASET_AUDIT_EVENT);

        Domain domain = PropertyService.get().getDomain(c, domainURI);
        if (domain == null)
        {
            domain = PropertyService.get().createDomain(c, domainURI, "DatasetAuditEventDomain");
            domain.save(user);
            domain = PropertyService.get().getDomain(c, domainURI);
        }

        if (domain != null)
        {
            ensureProperties(user, domain, new PropertyInfo[]{
                    new PropertyInfo(OLD_RECORD_PROP_NAME, OLD_RECORD_PROP_CAPTION, PropertyType.STRING),
                    new PropertyInfo(NEW_RECORD_PROP_NAME, NEW_RECORD_PROP_CAPTION, PropertyType.STRING)
            });
        }
    }

    @Override
    public void initialize(ContainerUser context) throws Exception
    {
        ensureDomain(context.getUser());
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

            out.write(PageFlowUtil.textLink("details", url));
        }
    }
}
