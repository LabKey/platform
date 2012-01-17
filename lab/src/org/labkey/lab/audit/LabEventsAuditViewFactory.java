package org.labkey.lab.audit;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.SimpleAuditViewFactory;
import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerDisplayColumn;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.files.FileUrls;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryView;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: bbimber
 * Date: 1/10/12
 * Time: 12:11 PM
 */
public class LabEventsAuditViewFactory extends SimpleAuditViewFactory
{
    public static final Map<String, String> EVENT_TYPE_MAP = new HashMap<String, String>(){
        {
            //NOTE: the intent of using a map was to provide a friendlier description for each audit event type; however, it is not currently visible
            //put("AssayPublishAuditEvent", "Assay Data Published");
            //put("DatasetAuditEvent", "Dataset");
            put("ExperimentAuditEvent", "Assay Data");
            put("FileSystemBatch", "Files");
            put("SampleSetAuditEvent", "Samples");
            put(ContainerManager.CONTAINER_AUDIT_EVENT, "Folder");
        }
    };

    private static final LabEventsAuditViewFactory INSTANCE = new LabEventsAuditViewFactory();

    public static LabEventsAuditViewFactory getInstance()
    {
        return INSTANCE;
    }

    @Override
    public String getName()
    {
        return "LabAuditEvents";
    }

    @Override
    public String getEventType()
    {
        return "LabAuditEvents";
    }

    @Override
    public String getDescription()
    {
        return "Summarizes events from assays, files, sample sets";
    }

    @Override
    public void setupTable(final FilteredTable table)
    {
        // show only the specified types
        SimpleFilter filter = new SimpleFilter(table.getRealTable().getColumn("EventType").getName(), (Object)StringUtils.join(EVENT_TYPE_MAP.keySet(), ";"), CompareType.IN);
        table.addCondition(filter);

        final ColumnInfo col = table.getColumn("ContainerId");
        col.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                ContainerDisplayColumn cdc = new ContainerDisplayColumn(colInfo, false);
                cdc.setEntityIdColumn(col);
                return cdc;
            }
        });

        final ColumnInfo commentCol = table.getColumn("Comment");
        commentCol.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                DataColumn dc = new DataColumn(colInfo)
                {
                    @Override
                    public String renderURL(RenderContext ctx)
                    {
                        String eventType = (String)ctx.get("EventType");

                        String id = (String)ctx.get(FieldKey.fromParts("ContainerId"));
                        Container c = ContainerManager.getForId(id);
                        if(c == null)
                            return null;

                        if(ContainerManager.CONTAINER_AUDIT_EVENT.equals(eventType))
                        {
                            return c.getStartURL(ctx.getViewContext().getUser()).toString();
                        }
                        else if("ExperimentAuditEvent".equals(eventType))
                        {
                            String protocolLsid = (String)ctx.get(FieldKey.fromParts("Key1"));
                            if(protocolLsid == null)
                                return null;

                            ExpProtocol protocol = ExperimentService.get().getExpProtocol(protocolLsid);
                            AssayProvider provider = null;
                            if (protocol != null)
                                provider = AssayService.get().getProvider(protocol);

                            ActionURL url = null;
                            if (provider != null)
                                url = PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(c, protocol);
                            else if (protocol != null)
                                url = PageFlowUtil.urlProvider(ExperimentUrls.class).getProtocolDetailsURL(protocol);

                            return url == null ? null : url.toString();
                        }
                        else if("FileSystemBatch".equals(eventType))
                        {
                            ActionURL url = PageFlowUtil.urlProvider(FileUrls.class).urlBegin(c);
                            return url == null ? null : url.toString();
                        }
                        else if("SampleSetAuditEvent".equals(eventType))
                        {
                            String sampleLsid = (String)ctx.get(FieldKey.fromParts("Key1"));
                            ExpSampleSet sampleSet = ExperimentService.get().getSampleSet(sampleLsid);
                            if (sampleSet == null)
                                return null;

                            ActionURL url = PageFlowUtil.urlProvider(ExperimentUrls.class).getShowSampleSetURL(sampleSet);
                            return url == null ? null : url.toString();
                        }

                        return null;
                    }

                    public void addQueryColumns(Set<ColumnInfo> columns)
                    {
                        super.addQueryColumns(columns);
                        columns.add(table.getColumn("EventType"));
                        columns.add(table.getColumn("Key1"));
                        columns.add(table.getColumn("Key2"));
                    }
                };

                return dc;
            }
        });

    }

    public QueryView createDefaultQueryView(ViewContext context)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition("EventType", EVENT_TYPE_MAP.keySet(), CompareType.IN);

        AuditLogQueryView view = AuditLogService.get().createQueryView(context, filter);
        view.setButtonBarPosition(DataRegion.ButtonBarPosition.BOTH);
        view.setSort(new Sort("-Date"));

        return view;
    }

    public List<FieldKey> getDefaultVisibleColumns()
    {
        List<FieldKey> columns = new ArrayList<FieldKey>();

        columns.add(FieldKey.fromParts("Date"));
        columns.add(FieldKey.fromParts("CreatedBy"));
        columns.add(FieldKey.fromParts("Comment"));
        columns.add(FieldKey.fromParts("ContainerId"));

        return columns;
    }
}
