package org.labkey.lab.audit;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.SimpleAuditViewFactory;
import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.ContainerDisplayColumn;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryView;
import org.labkey.api.view.ViewContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            put("AssayPublishAuditEvent", "Assay Data Published");
            put("DatasetAuditEvent", "Dataset");
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
    public void setupTable(FilteredTable table)
    {
        // show only the specified types
        SimpleFilter filter = new SimpleFilter(table.getRealTable().getColumn("EventType").getName(), (Object)StringUtils.join(EVENT_TYPE_MAP.keySet(), ";"), CompareType.IN);
        table.addCondition(filter);

        final ColumnInfo col = table.getColumn("ContainerId");
        col.setDisplayColumnFactory(new DisplayColumnFactory()
        {
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new ContainerDisplayColumn(colInfo, false);
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
