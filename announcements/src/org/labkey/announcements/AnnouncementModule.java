/*
 * Copyright (c) 2005-2009 Fred Hutchinson Cancer Research Center
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
package org.labkey.announcements;

import junit.framework.TestCase;
import org.apache.log4j.Logger;
import org.labkey.announcements.model.*;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Search;
import org.labkey.api.view.*;

import javax.servlet.ServletException;
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
public class AnnouncementModule extends DefaultModule
{
    public static final String WEB_PART_NAME = "Messages";

    private static Logger _log = Logger.getLogger(AnnouncementModule.class);

    public String getName()
    {
        return "Announcements";
    }

    public double getVersion()
    {
        return 9.10;
    }

    protected void init()
    {
        addController("announcements", AnnouncementsController.class);
    }

    protected Collection<? extends WebPartFactory> createWebPartFactories()
    {
        return Arrays.asList(new AlwaysAvailableWebPartFactory(WEB_PART_NAME)
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
            },
            new AlwaysAvailableWebPartFactory(WEB_PART_NAME + " List")
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
            },
            new DiscussionWebPartFactory());
    }

    public boolean hasScripts()
    {
        return true;
    }

    public String getTabName(ViewContext context)
    {
        return "Messages";
    }

    public void startup(ModuleContext moduleContext)
    {
        Search.register(new MessageSearch());
        DiscussionService.register(new DiscussionServiceImpl());

        AnnouncementListener listener = new AnnouncementListener();
        ContainerManager.addContainerListener(listener);
        UserManager.addUserListener(listener);
        SecurityManager.addGroupListener(listener);

        DailyDigest.setTimer();
    }

    @Override
    public void afterUpdate(ModuleContext moduleContext)
    {
        super.afterUpdate(moduleContext);

        if (moduleContext.isNewInstall())
            bootstrap(moduleContext);
    }

    private void bootstrap(ModuleContext moduleContext)
    {
        try
        {
            Container supportContainer = ContainerManager.getDefaultSupportContainer();
            addWebPart(WEB_PART_NAME, supportContainer, null);

            User installerUser = moduleContext.getUpgradeUser();

            if (installerUser != null && !installerUser.isGuest())
                AnnouncementManager.saveEmailPreference(installerUser, supportContainer, AnnouncementManager.EMAIL_PREFERENCE_ALL);
        }
        catch (SQLException e)
        {
            _log.error("Unable to set up support folder", e);
        }
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
