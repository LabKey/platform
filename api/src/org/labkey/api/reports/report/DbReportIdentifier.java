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

import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.writer.ContainerUser;

/*
* User: Dave
* Date: Dec 11, 2008
* Time: 3:40:47 PM
*/
public class DbReportIdentifier extends AbstractReportIdentifier
{
    protected static final String PREFIX = "db:";

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

    public DbReportIdentifier(String id) throws IllegalArgumentException
    {
        if(!id.startsWith(PREFIX))
            throw new IllegalArgumentException("Not a valid identifier");
        _id = Integer.parseInt(id.substring(PREFIX.length()));
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

        if (_id != that._id) return false;

        return true;
    }

    @Override
    public int hashCode()
    {
        return _id;
    }

    public int getRowId()
    {
        return _id;
    }

    public Report getReport(ContainerUser cu)
    {
        if(_id == -1)
            return null;

        Report report = ReportService.get().getReport(cu.getContainer(), getRowId());
        return (report != null && report.hasPermission(cu.getUser(), cu.getContainer(), ReadPermission.class)) ? report : null;
    }
}
