/*
 * Copyright (c) 2008-2010 LabKey Corporation
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
package org.labkey.issue;

import org.labkey.api.module.ModuleContext;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.Table;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.issues.IssuesSchema;
import org.apache.log4j.Logger;

import javax.servlet.http.HttpServletResponse;
import java.sql.SQLException;
import java.util.Map;

/**
 * User: adam
 * Date: Nov 25, 2008
 * Time: 1:12:32 PM
 */
public class IssueUpgradeCode implements UpgradeCode
{
    private static final Logger _log = Logger.getLogger(IssueUpgradeCode.class);

    // Invoked from issues-8.10-8.20.sql
    @SuppressWarnings({"UnusedDeclaration"})
    public void populateCommentEntityIds(ModuleContext moduleContext)
    {
        if (!moduleContext.isNewInstall())
        {
            try
            {
                doPopulateCommentEntityIds();
            }
            catch (Exception e)
            {
                String msg = "Error running afterSchemaUpdate doPopulateCommentEntityIds on IssueModule, upgrade from version " + String.valueOf(moduleContext.getInstalledVersion());
                _log.error(msg + " \n Caused by " + e);
                ExperimentException ex = new ExperimentException(msg, e);
                //following sends an exception report to mothership if site is configured to do so, but doesn't abort schema upgrade
                ExceptionUtil.getErrorRenderer(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, ex, null, false, false);
            }
        }
    }

    private void doPopulateCommentEntityIds() throws SQLException
    {
        TableInfo tinfo = IssuesSchema.getInstance().getTableInfoComments();
        Table.TableResultSet rs = Table.select(tinfo, tinfo.getColumns(), null, null);
        String sql = "UPDATE " + tinfo + " SET EntityId = ? WHERE CommentId = ? AND IssueId = ?";

        try
        {
            while (rs.next())
            {
                Map<String, Object> row = rs.getRowMap();
                Table.execute(tinfo.getSchema(), sql, new Object[]{GUID.makeGUID(), row.get("CommentId"), row.get("IssueId")});
            }
        }
        finally
        {
            ResultSetUtil.close(rs);
        }
    }
}
