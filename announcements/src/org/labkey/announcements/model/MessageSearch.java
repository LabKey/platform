/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.announcements.model;

import org.labkey.api.util.*;
import org.labkey.api.util.Search.*;
import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.announcements.CommSchema;
import org.labkey.announcements.AnnouncementsController;

import java.util.Set;
import java.util.List;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * User: adam
 * Date: Aug 25, 2008
 * Time: 3:03:30 PM
 */
public class MessageSearch implements Searchable
{
    private static final String SEARCH_DOMAIN = "messages";
    private static final String SEARCH_RESULT_TYPE = "labkey/message";
    private static final String SEARCH_RESULT_TYPE_DESCR = "Messages";

    public void search(SearchTermParser parser, Set<Container> containers, List<SearchHit> hits, User user)
    {
        CommSchema _comm = CommSchema.getInstance();
        SqlDialect dialect = _comm.getSchema().getSqlDialect();
        String from = _comm.getTableInfoThreads() + " t LEFT OUTER JOIN " + _comm.getTableInfoAnnouncements() + " a ON ((a.parent IS NULL AND t.RowId = a.RowId) OR (t.EntityId = a.Parent))";
        SQLFragment searchSql = Search.getSQLFragment("Container, Title, RowId", "t.Container, t.Title, t.RowId", from, "t.Container", null, containers, parser, dialect, "a.Title", "a.Body");
        ResultSet rs = null;

        try
        {
            rs = Table.executeQuery(_comm.getSchema(), searchSql);

            while(rs.next())
            {
                String containerId = rs.getString(1);
                Container c = ContainerManager.getForId(containerId);
                int rowId = rs.getInt(3);

                // Need to check that user has permission to see this message.
                Permissions perm = AnnouncementsController.getPermissions(c, user, AnnouncementsController.getSettings(c));
                Announcement ann = AnnouncementManager.getAnnouncement(c, rowId, AnnouncementManager.INCLUDE_MEMBERLIST);

                if (perm.allowRead(ann))
                {
                    ActionURL url = new ActionURL(AnnouncementsController.ThreadAction.class, c);
                    url.addParameter("rowId", rowId);

                    SimpleSearchHit hit = new SimpleSearchHit(SEARCH_DOMAIN, c.getPath(),
                            rs.getString(2), url.getLocalURIString(), SEARCH_RESULT_TYPE,
                            SEARCH_RESULT_TYPE_DESCR);
                    hits.add(hit);
                }
            }
        }
        catch(SQLException e)
        {
            ExceptionUtil.logExceptionToMothership(HttpView.currentRequest(), e);
        }
        finally
        {
            ResultSetUtil.close(rs);
        }
    }

    public String getSearchResultNamePlural()
    {
        return SEARCH_RESULT_TYPE_DESCR;
    }

    public String getDomainName()
    {
        return SEARCH_DOMAIN;
    }
}
