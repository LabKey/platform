/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
package org.labkey.query.reports;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
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
import org.labkey.api.security.UserManager;
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
    public void queryCreated(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull Collection<String> queries)
    {
    }

    @Override
    public void queryChanged(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull QueryProperty property, @NotNull Collection<QueryPropertyChange> changes)
    {
        if (property.equals(QueryProperty.Name))
        {
            _updateReportQueryNameChange(user, container, schema, changes);
        }
    }

    @Override
    public void queryDeleted(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull Collection<String> queries)
    {
    }

    @Override
    public Collection<String> queryDependents(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull Collection<String> queries)
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
        Logger logger = Logger.getLogger(ReportQueryChangeListener.class);

        // most property updates only care about the query name old value string and new value string
        Map<String, String> queryNameChangeMap = new HashMap<>();
        for (QueryPropertyChange qpc : changes)
        {
            queryNameChangeMap.put((String)qpc.getOldValue(), (String)qpc.getNewValue());
        }

        // passing in null for the user should get all reports (private and public, independent of the owner)
        for (Report report : ReportService.get().getReports(null, container))
        {
            try
            {
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
                    User reportOwner = UserManager.getUser(descriptor.getModifiedBy());
                    if (reportOwner != null)
                    {
                        ContainerUser rptContext = new DefaultContainerUser(container, reportOwner);
                        ReportService.get().saveReport(rptContext, descriptor.getReportKey(), report, true);
                    }
                    else
                    {
                        logger.warn("The owner of the '" + descriptor.getReportName() + "' report does not exist: UserId " + descriptor.getModifiedBy());
                    }
                }
            }
            catch (Exception e)
            {
                logger.error("An error occurred upgrading report properties: ", e);
            }
        }
    }
}
