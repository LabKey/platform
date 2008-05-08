/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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

import junit.framework.TestCase;
import org.apache.log4j.Logger;
import org.labkey.announcements.AnnouncementsController;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Search;
import org.labkey.api.util.SearchHit;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;

import javax.servlet.ServletException;
import java.beans.PropertyChangeEvent;
import java.sql.SQLException;
import java.util.*;

/**
 * User: migra
 * Date: Jul 13, 2005
 * Time: 3:05:50 PM
 * <p/>
 * NOTE: Wiki handles some of the shared Communications module stuff.
 * e.g. it handles ContainerListener and Attachments
 * <p/>
 * TODO: merge announcement & wiki into one module?
 */
public class AnnouncementModule extends DefaultModule implements Search.Searchable, ContainerManager.ContainerListener, UserManager.UserListener, SecurityManager.GroupListener
{
    public static final String NAME = "Announcements";

    public static final String WEB_PART_NAME = "Messages";
    public static final String SEARCH_DOMAIN = "messages";
    public static final String SEARCH_RESULT_TYPE = "labkey/message";
    public static final String SEARCH_RESULT_TYPE_DESCR = "Message";

    private static Logger _log = Logger.getLogger(AnnouncementModule.class);

    private boolean _newInstall = false;
    private User _installerUser = null;

    public AnnouncementModule()
    {
        super(NAME, 8.10, "/org/labkey/announcements", true,
            new WebPartFactory(WEB_PART_NAME)
            {
                public WebPartView getWebPartView(ViewContext parentCtx, Portal.WebPart webPart)
                {
                    try
                    {
                        return new AnnouncementsController.AnnouncementWebPart(parentCtx);
                    }
                    catch(Exception e)
                    {
                        throw new RuntimeException(e); // TODO: getWebPartView should throw Exception?
                    }
                }

                public boolean isAvailable(Container c, String location)
                {
                    return location.equals(getDefaultLocation());
                }
            },
            new WebPartFactory(WEB_PART_NAME + " List")
            {
                public WebPartView getWebPartView(ViewContext parentCtx, Portal.WebPart webPart)
                {
                    try
                    {
                        return new AnnouncementsController.AnnouncementListWebPart(parentCtx);
                    }
                    catch (ServletException e)
                    {
                        throw new RuntimeException(e); // TODO: getWebPartView should throw Exception?
                    }
                }

                public boolean isAvailable(Container c, String location)
                {
                    return location.equals(getDefaultLocation());
                }
            }
        );
        addController("announcements", AnnouncementsController.class);
    }

    public String getTabName(ViewContext context)
    {
        return "Messages";
    }

    @Override
    public void startup(ModuleContext moduleContext)
    {
        super.startup(moduleContext);
        Search.register(this);
        DiscussionService.register(new DiscussionServiceImpl());

        ContainerManager.addContainerListener(this);
        UserManager.addUserListener(this);
        SecurityManager.addGroupListener(this);

        if (_newInstall)
        {
            try
            {
                Container supportContainer = ContainerManager.getDefaultSupportContainer();
                addWebPart(WEB_PART_NAME, supportContainer, null);

                if (_installerUser != null && !_installerUser.isGuest())
                    AnnouncementManager.saveEmailPreference(_installerUser, supportContainer, AnnouncementManager.EMAIL_PREFERENCE_ALL);
            }
            catch (SQLException e)
            {
                _log.error("Unable to set up support folder", e);
            }

            _newInstall = false;
            _installerUser = null;
        }

        DailyDigest.setTimer();
    }

    @Override
    public void afterSchemaUpdate(ModuleContext moduleContext, ViewContext viewContext)
    {
        if (moduleContext.getInstalledVersion() == 0.0)
        {
            _newInstall = true;
            _installerUser = viewContext.getUser();
        }
    }

    public void containerCreated(Container c)
    {
    }

    public void propertyChange(PropertyChangeEvent evt)
    {
    }

    // Note: Attachments are purged by AttachmentServiceImpl.containerDeleted()
    public void containerDeleted(Container c, User user)
    {
        try
        {
            AnnouncementManager.purgeContainer(c);
        }
        catch (Throwable t)
        {
            _log.error(t);
        }
    }


    public void userAddedToSite(User user)
    {
    }

    public void userDeletedFromSite(User user)
    {
        //when user is deleted from site, remove any corresponding record from EmailPrefs table.
        try
        {
            AnnouncementManager.deleteUserEmailPref(user, null);
            AnnouncementManager.deleteUserFromAllMemberLists(user);
        }
        catch (SQLException e)
        {
            _log.error(e);
        }
    }

    public void principalAddedToGroup(Group group, UserPrincipal user)
    {
    }

    public void principalDeletedFromGroup(Group g, UserPrincipal p)
    {
        if (g.isProjectGroup() && p instanceof User)
        {
            User user = (User)p;
            Container cProject = ContainerManager.getForId(g.getContainer());
            List<User> memberList = SecurityManager.getProjectMembers(cProject, false);

            //if user is no longer a member of any project group, delete any EmailPrefs records
            if (!memberList.contains(user))
            {
                //find all containers for which this user could have an entry in EmailPrefs
                List<Container> containerList = cProject.getChildren();
                //add project container to list
                containerList.add(cProject);
                try
                {

                    AnnouncementManager.deleteUserEmailPref(user, containerList);
                }
                catch (SQLException e)
                {
                    //is this the preferred way to handle any such errors?
                    _log.error(e);
                }
            }
        }
    }

    public void search(Search.SearchTermParser parser, Set<Container> containers, List<SearchHit> hits)
    {
        AnnouncementManager.search(parser, containers, hits);
    }

    public String getSearchResultName()
    {
        return SEARCH_RESULT_TYPE_DESCR;
    }

    public String getDomainName()
    {
        return SEARCH_DOMAIN;
    }


    @Override
    public Set<Class<? extends TestCase>> getJUnitTests()
    {
        return new HashSet<Class<? extends TestCase>>(Arrays.asList(
            AnnouncementManager.TestCase.class));
    }

    @Override
    public Set<DbSchema> getSchemasToTest()
    {
        return PageFlowUtil.set(CommSchema.getInstance().getSchema());
    }

    @Override
    public Set<String> getSchemaNames()
    {
        return PageFlowUtil.set(CommSchema.getInstance().getSchemaName());
    }

    @Override
    public Set<String> getModuleDependencies()
    {
        Set<String> result = new HashSet<String>();
        result.add("Wiki");
        return result;
    }


    public Collection<String> getSummary(Container c)
    {
        List<String> list = new ArrayList<String>(1);
        try
        {
            long count = AnnouncementManager.getMessageCount(c);
            if (count > 0)
                list.add("" + count + " " + (count > 1 ? "Messages/Responses" : "Message"));
        }
        catch (SQLException x)
        {
            list.add(x.toString());
        }
        return list;
    }
}
