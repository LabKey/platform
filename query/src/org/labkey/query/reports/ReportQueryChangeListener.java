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
import org.labkey.api.security.User;
import org.labkey.api.writer.ContainerUser;
import org.labkey.api.writer.DefaultContainerUser;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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
    public void queryCreated(User user, Container container, ContainerFilter scope, SchemaKey schema, Collection<String> queries)
    {
    }

    @Override
    public void queryChanged(User user, Container container, ContainerFilter scope, SchemaKey schema, QueryProperty property, Collection<QueryPropertyChange> changes)
    {
        if (property != null && property.equals(QueryProperty.Name))
        {
            _updateReportQueryNameChange(user, container, schema, changes);
        }
    }

    @Override
    public void queryDeleted(User user, Container container, ContainerFilter scope, SchemaKey schema, Collection<String> queries)
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

    private void _updateReportQueryNameChange(User user, Container container, SchemaKey schemaKey, Collection<QueryPropertyChange> changes)
    {
        // most property updates only care about the query name old value string and new value string
        Map<String, String> queryNameChangeMap = new HashMap<String, String>();
        for (QueryPropertyChange qpc : changes)
        {
            queryNameChangeMap.put((String)qpc.getOldValue(), (String)qpc.getNewValue());
        }

        // passing in null for the user should get all reports (private and public, independent of the owner)
        for (Report report : ReportService.get().getReports(null, container))
        {
            try {
                boolean hasUpdates = false;
                ReportDescriptor descriptor = report.getDescriptor();

                // update reportKey (stored in core.Report)
                String[] keyParts = ReportUtil.splitReportKey(descriptor.getReportKey());
                if (keyParts.length > 0 && keyParts[0].equals(schemaKey.toString()))
                {
                    String reportKeyQuery = keyParts[keyParts.length-1];
                    if (queryNameChangeMap.containsKey(reportKeyQuery))
                    {
                        descriptor.setReportKey(ReportUtil.getReportKey(schemaKey.toString(), queryNameChangeMap.get(reportKeyQuery)));
                        hasUpdates = true;
                    }
                }

                // update report queryName property
                String schemaName = descriptor.getProperty(ReportDescriptor.Prop.schemaName);
                String queryName = descriptor.getProperty(ReportDescriptor.Prop.queryName);
                if (queryName != null && schemaName != null && schemaName.equals(schemaKey.toString()))
                {
                    if (queryNameChangeMap.containsKey(queryName))
                    {
                        descriptor.setProperty(ReportDescriptor.Prop.queryName, queryNameChangeMap.get(queryName));
                        hasUpdates = true;
                    }
                }

                // report specific migration for JSON config properties that contain queryName
                hasUpdates = descriptor.updateQueryNameReferences(changes) || hasUpdates;

                if (hasUpdates)
                {
                    ContainerUser rptContext = new DefaultContainerUser(container, user);
                    ReportService.get().saveReport(rptContext, descriptor.getReportKey(), report, true);
                }
            }
            catch (Exception e)
            {
                Logger.getLogger(ReportQueryChangeListener.class).error("An error occurred upgrading report properties: ", e);
            }
        }
    }
}
