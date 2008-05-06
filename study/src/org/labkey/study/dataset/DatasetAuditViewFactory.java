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
import org.labkey.api.view.ViewContext;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.Study;
import org.labkey.study.model.StudyManager;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
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
        view.setShowCustomizeViewLinkInButtonBar(true);
        view.setButtonBarPosition(DataRegion.ButtonBarPosition.BOTH);

        return view;
    }

    public void setupTable(final TableInfo table)
    {
        final ColumnInfo containerColumn = table.getColumn("ContainerId");
        final ColumnInfo datasetDefColumn = table.getColumn("IntKey1");
        datasetDefColumn.setCaption("Dataset");

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
                        Study study = StudyManager.getInstance().getStudy(container);
                        DataSetDefinition def = StudyManager.getInstance().
                                getDataSetDefinition(study,datasetId.intValue());

                        if (def == null)
                        {
                            out.write(datasetId.toString());
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

    public List<FieldKey> getDefaultVisibleColumns()
    {
        List<FieldKey> columns = new ArrayList<FieldKey>();

        columns.add(FieldKey.fromParts("Date"));
        columns.add(FieldKey.fromParts("CreatedBy"));
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
}
