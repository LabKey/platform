package org.labkey.experiment;

import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.SimpleAuditViewFactory;
import org.labkey.api.audit.query.AuditLogQueryView;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.query.QueryView;
import org.labkey.api.view.ViewContext;

/**
 * Created by IntelliJ IDEA.
 * User: bbimber
 * Date: 1/13/12
 * Time: 7:29 AM
 */
public class SampleSetAuditViewFactory extends SimpleAuditViewFactory
{
    private static final SampleSetAuditViewFactory INSTANCE = new SampleSetAuditViewFactory();
    public static final String EVENT_TYPE = "SampleSetAuditEvent";

    public static SampleSetAuditViewFactory getInstance()
    {
        return INSTANCE;
    }

    @Override
    public String getName()
    {
        return getEventType();
    }

    @Override
    public String getEventType()
    {
        return EVENT_TYPE;
    }

    @Override
    public String getDescription()
    {
        return "Summarizes events from sample set inserts or updates";
    }

    public QueryView createDefaultQueryView(ViewContext context)
    {
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition("EventType", EVENT_TYPE, CompareType.EQUAL);

        AuditLogQueryView view = AuditLogService.get().createQueryView(context, filter);
        view.setButtonBarPosition(DataRegion.ButtonBarPosition.BOTH);
        view.setSort(new Sort("-Date"));

        return view;
    }
}
