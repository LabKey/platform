package org.labkey.query.reports;

import common.Logger;
import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.CustomViewChangeListener;
import org.labkey.api.query.QueryChangeListener;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.view.ReportUtil;

import java.util.Collection;
import java.util.Collections;

/**
* User: kevink
* Date: 4/18/13
*/
class ReportQueryChangeListener implements QueryChangeListener, CustomViewChangeListener
{
    protected void _uncacheDependent(CustomView view)
    {
        try
        {
            QueryDefinition def = view.getQueryDefinition();
            String key = ReportUtil.getReportKey(def.getSchemaName(), def.getName());

            for (Report report : ReportService.get().getReports(null, view.getContainer(), key))
            {
                if (StringUtils.equals(view.getName(), report.getDescriptor().getProperty(ReportDescriptor.Prop.viewName)))
                    report.clearCache();
            }
        }
        catch (Exception e)
        {
            Logger.getLogger(ReportQueryChangeListener.class).error("An error occurred uncaching dependent reports", e);
        }
    }


    @Override
    public void queryCreated(Container container, ContainerFilter scope, SchemaKey schema, Collection<String> queries)
    {
    }

    @Override
    public void queryChanged(Container container, ContainerFilter scope, SchemaKey schema, QueryProperty property, Collection<QueryPropertyChange> changes)
    {
    }

    @Override
    public void queryDeleted(Container container, ContainerFilter scope, SchemaKey schema, Collection<String> queries)
    {
    }

    @Override
    public Collection<String> queryDependents(Container container, ContainerFilter scope, SchemaKey schema, Collection<String> queries)
    {
        // UNDONE
        return Collections.emptyList();
    }

    @Override
    public void viewCreated(CustomView view)
    {
    }

    @Override
    public void viewChanged(CustomView view)
    {
        _uncacheDependent(view);
    }

    @Override
    public void viewDeleted(CustomView view)
    {
        _uncacheDependent(view);
    }

    @Override
    public Collection<String> viewDependents(CustomView view)
    {
        // UNDONE
        return Collections.emptyList();
    }

}
