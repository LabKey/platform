/*
 * Copyright (c) 2005-2008 Fred Hutchinson Cancer Research Center
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

import junit.framework.TestCase;
import org.apache.log4j.Logger;
import org.labkey.api.data.*;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.util.*;
import org.labkey.api.view.*;
import org.labkey.issue.model.IssueManager;
import org.labkey.issue.query.IssuesQuerySchema;

import javax.servlet.http.HttpServletResponse;
import java.beans.PropertyChangeEvent;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.*;

/**
 * User: migra
 * Date: Jul 18, 2005
 * Time: 3:48:21 PM
 */
public class IssuesModule extends DefaultModule implements ContainerManager.ContainerListener, SecurityManager.GroupListener, Search.Searchable, UserManager.UserListener
{
    private static final Logger _log = Logger.getLogger(IssuesModule.class);

    public static final String NAME = "Issues";
    public static final String SEARCH_DOMAIN = "issues";
    public static final String SEARCH_RESULT_TYPE = "labkey/issue";
    public static final String SEARCH_RESULT_TYPE_DESCR = "Issues";

    public IssuesModule()
    {
        super(NAME, 8.20, "/org/labkey/issue", true, new IssuesWebPartFactory());
        addController("issues", IssuesController.class);

        IssuesQuerySchema.register();
    }

    static class IssuesWebPartFactory extends WebPartFactory
    {
        public IssuesWebPartFactory()
        {
            this(NAME, null);
        }

        public IssuesWebPartFactory(String name, String location)
        {
            super(name, location, true, false);
        }

        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
        {
            WebPartView v = new IssuesController.SummaryWebPart();
            populateProperties(v, webPart.getPropertyMap());
            return v;
        }

        public boolean isAvailable(Container c, String location)
        {
            return location.equals(getDefaultLocation());
        }

        @Override
        public HttpView getEditView(Portal.WebPart webPart)
        {
            WebPartView v = new IssuesController.CustomizeIssuesPartView();
            v.addObject("webPart", webPart);
            return v;
        }
    }



    @Override
    public void startup(ModuleContext moduleContext)
    {
        super.startup(moduleContext);
        ContainerManager.addContainerListener(this);
        SecurityManager.addGroupListener(this);
        UserManager.addUserListener(this);

        Search.register(this);
    }

    public void containerCreated(Container c)
    {
    }

    public void containerDeleted(Container c, User user)
    {
        IssueManager.purgeContainer(c);
    }

    public void propertyChange(PropertyChangeEvent event)
    {
    }

    public void principalAddedToGroup(Group g, UserPrincipal user)
    {
        IssueManager.uncache(ContainerManager.getForId(g.getContainer()));
    }

    public void principalDeletedFromGroup(Group g, UserPrincipal user)
    {
        IssueManager.uncache(ContainerManager.getForId(g.getContainer()));
    }


    @Override
    public Collection<String> getSummary(Container c)
    {
        Collection<String> list = new LinkedList<String>();
        try
        {
            long count = IssueManager.getIssueCount(c);
            if (count > 0)
                list.add("" + count + " Issue" + (count > 1 ? "s" : ""));
        }
        catch (SQLException x)
        {
            list.add(x.getMessage());
        }
        return list;
    }

    public TabDisplayMode getTabDisplayMode()
    {
        return Module.TabDisplayMode.DISPLAY_USER_PREFERENCE;
    }

    public ActionURL getTabURL(Container c, User user)
    {
        ActionURL url = new ActionURL(getName().toLowerCase(), "list", c == null ? null : c.getPath());
        url.addParameter(".lastFilter", "true");
        return url;
    }

    public void search(Search.SearchTermParser parser, Set<Container> containers, List<SearchHit> hits)
    {
        IssueManager.search(parser, containers, hits);
    }


    public String getSearchResultNamePlural()
    {
        return SEARCH_RESULT_TYPE_DESCR;
    }

    public String getDomainName()
    {
        return SEARCH_DOMAIN;
    }


    public Set<Class<? extends TestCase>> getJUnitTests()
    {
        return new HashSet<Class<? extends TestCase>>(Arrays.asList(
            org.labkey.issue.IssuesController.TestCase.class,
            org.labkey.issue.model.IssueManager.TestCase.class ));
    }

    public Set<DbSchema> getSchemasToTest()
    {
        return PageFlowUtil.set(IssuesSchema.getInstance().getSchema());
    }

    public Set<String> getSchemaNames()
    {
        return PageFlowUtil.set(IssuesSchema.getInstance().getSchemaName());
    }

    public Set<String> getModuleDependencies()
    {
        Set<String> result = new HashSet<String>();
        result.add("Wiki");
        return result;
    }

    public void userAddedToSite(User user)
    {
    }

    public void userDeletedFromSite(User user)
    {
        try {
            IssueManager.deleteUserEmailPreferences(user);
        }
        catch (SQLException e)
        {
            _log.error(e);            
        }
    }

    public void userAccountDisabled(User user)
    {
        IssueManager.uncache(null);
    }

    public void userAccountEnabled(User user)
    {
        IssueManager.uncache(null);
    }

    public void afterSchemaUpdate(ModuleContext moduleContext, ViewContext viewContext)
    {
        double version = moduleContext.getInstalledVersion();
        if (version > 0 && version < 8.11)
        {
            try {
                doPopulateCommentEntityIds();
            }
            catch (Exception e)
            {
                String msg = "Error running afterSchemaUpdate doPopulateCommentEntityIds on IssueModule, upgrade from version " + String.valueOf(version);
                _log.error(msg + " \n Caused by " + e);
                ExperimentException ex = new ExperimentException(msg, e);
                //following sends an exception report to mothership if site is configured to do so, but doesn't abort schema upgrade
                ExceptionUtil.getErrorRenderer(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg, ex, viewContext.getRequest(), false, false);
            }
        }
        super.afterSchemaUpdate(moduleContext, viewContext);
    }

    private void doPopulateCommentEntityIds() throws SQLException
    {
        TableInfo tinfo = IssuesSchema.getInstance().getTableInfoComments();
        Table.TableResultSet rs = Table.select(tinfo, tinfo.getColumns(), null, null);
        String sql = "UPDATE " + tinfo + " SET EntityId = ? WHERE CommentId = ? AND IssueId = ?";

        try {
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
