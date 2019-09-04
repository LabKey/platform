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
package org.labkey.api.reports.report;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.HttpView;
import org.labkey.api.writer.ContainerUser;

import java.util.List;
import java.util.stream.Collectors;

/*
* User: Dave
* Date: Dec 11, 2008
* Time: 3:40:47 PM
*/
public class DbReportIdentifier extends AbstractReportIdentifier
{
    protected static final String PREFIX = "db:";

    private static Logger LOG = Logger.getLogger(DbReportIdentifier.class);

    private final int _id;

    // No-args constructor to support de-serialization in Java 7
    @SuppressWarnings({"UnusedDeclaration"})
    private DbReportIdentifier()
    {
        this(-1);
    }

    public DbReportIdentifier(int id)
    {
        _id = id;
    }

    public DbReportIdentifier(String id, @Nullable User user, @Nullable Container container) throws IllegalArgumentException
    {
        if(!id.startsWith(PREFIX))
            throw new IllegalArgumentException("Not a valid identifier");
        String suffix = id.substring(PREFIX.length());
        int resolvedId;
        try
        {
            resolvedId = Integer.parseInt(suffix);
        }
        catch (NumberFormatException e)
        {
            LOG.debug("Failed to parse reportId as a number: '" + id + "'. Attempting to resolve by name");
            // See if we can resolve the report by name instead of id

            // If no user or container was supplied, see if there's a ViewContext we can use. This is important
            // because key code paths are using Spring form bean binding, which goes through ReportIdentifierConverter
            // and doesn't have direct knowledge of the user or container itself
            if (user == null && HttpView.hasCurrentView() && HttpView.currentContext() != null)
            {
                user = HttpView.currentContext().getUser();
            }

            if (container == null && HttpView.hasCurrentView() && HttpView.currentContext() != null)
            {
                container = HttpView.currentContext().getContainer();
            }

            if (user != null && container != null)
            {
                // Filter all available reports by name to see if we get a single match
                List<Report> matchingReports = ReportService.get().getReports(user, container).stream().filter((r) -> suffix.equalsIgnoreCase(r.getDescriptor().getReportName())).collect(Collectors.toList());

                LOG.debug("Found " + matchingReports.size() + " matching DB-based reports for id '" + id + "' for user " + user.getEmail() + " in " + container.getPath());

                if (matchingReports.size() == 1)
                {
                    resolvedId = matchingReports.get(0).getDescriptor().getReportId().getRowId();
                }
                else
                {
                    throw e;
                }
            }
            else
            {
                LOG.debug("No user or container available, unable to try resolving by report name for '" + id + "'");
                throw e;
            }
        }
        _id = resolvedId;
    }

    @Override
    public String toString()
    {
        return PREFIX + String.valueOf(getRowId());
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DbReportIdentifier that = (DbReportIdentifier) o;

        return _id == that._id;
    }

    @Override
    public int hashCode()
    {
        return _id;
    }

    @Override
    public int getRowId()
    {
        return _id;
    }

    @Override
    public Report getReport(ContainerUser cu)
    {
        if(_id == -1)
            return null;

        Report report = ReportService.get().getReport(cu.getContainer(), getRowId());
        return (report != null && report.hasPermission(cu.getUser(), cu.getContainer(), ReadPermission.class)) ? report : null;
    }
}
