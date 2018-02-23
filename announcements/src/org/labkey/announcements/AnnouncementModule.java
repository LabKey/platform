/*
 * Copyright (c) 2005-2017 Fred Hutchinson Cancer Research Center
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

import org.jetbrains.annotations.NotNull;
import org.labkey.announcements.api.AnnouncementServiceImpl;
import org.labkey.announcements.api.TourServiceImpl;
import org.labkey.announcements.config.AnnouncementEmailConfig;
import org.labkey.announcements.config.MessageConfigServiceImpl;
import org.labkey.announcements.model.AnnouncementDigestProvider;
import org.labkey.announcements.model.AnnouncementManager;
import org.labkey.announcements.model.AnnouncementType;
import org.labkey.announcements.model.DiscussionServiceImpl;
import org.labkey.announcements.model.DiscussionWebPartFactory;
import org.labkey.announcements.model.InsertMessagePermission;
import org.labkey.announcements.model.MessageBoardContributorRole;
import org.labkey.announcements.model.SecureMessageBoardReadPermission;
import org.labkey.announcements.model.SecureMessageBoardRespondPermission;
import org.labkey.announcements.query.AnnouncementSchema;
import org.labkey.api.admin.FolderSerializationRegistry;
import org.labkey.api.announcements.CommSchema;
import org.labkey.api.announcements.DiscussionService;
import org.labkey.api.announcements.api.AnnouncementService;
import org.labkey.api.announcements.api.TourService;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.provider.MessageAuditProvider;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.message.digest.DailyMessageDigest;
import org.labkey.api.message.settings.MessageConfigService;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.notification.EmailService;
import org.labkey.api.rss.RSSService;
import org.labkey.api.rss.RSSServiceImpl;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.roles.EditorRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.emailTemplate.EmailTemplateService;
import org.labkey.api.view.AlwaysAvailableWebPartFactory;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: migra
 * Date: Jul 13, 2005
 * Time: 3:05:50 PM
 * <p/>
 * NOTE: Wiki handles some of the shared Communications module stuff.
 * e.g. it handles ContainerListener and Attachments
 * <p/>
 * TODO: merge announcementModel & wiki into one module?
 */
public class AnnouncementModule extends DefaultModule implements SearchService.DocumentProvider
{
    public static final String WEB_PART_NAME = "Messages";
    public static final String NAME = "Announcements";

    public AnnouncementModule()
    {
        setLabel("Message Board and Discussion Service");

        RSSServiceImpl i = new RSSServiceImpl();
        RSSService.set(i);
    }

    @Override
    public String getName()
    {
        return NAME;
    }

    @Override
    public double getVersion()
    {
        return 18.10;
    }

    protected void init()
    {
        addController("announcements", AnnouncementsController.class);
        AnnouncementService.setInstance(new AnnouncementServiceImpl());

        addController("tours", ToursController.class);

        AnnouncementSchema.register(this);
        DiscussionService.register(new DiscussionServiceImpl());
        EmailTemplateService.get().registerTemplate(AnnouncementManager.NotificationEmailTemplate.class);
        EmailTemplateService.get().registerTemplate(AnnouncementDigestProvider.DailyDigestEmailTemplate.class);

        AttachmentService.get().registerAttachmentType(AnnouncementType.get());
    }

    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        return new ArrayList<>(Arrays.asList(
            new AnnouncementsController.AnnoucementWebPartFactory(WEB_PART_NAME),
            new AlwaysAvailableWebPartFactory(WEB_PART_NAME + " List")
            {
                public WebPartView getWebPartView(@NotNull ViewContext parentCtx, @NotNull Portal.WebPart webPart)
                {
                    return new AnnouncementsController.AnnouncementListWebPart(parentCtx);
                }
            },
            new DiscussionWebPartFactory()));
    }

    public boolean hasScripts()
    {
        return true;
    }

    public String getTabName(ViewContext context)
    {
        return "Messages";
    }


    public void doStartup(ModuleContext moduleContext)
    {
        AnnouncementListener listener = new AnnouncementListener();
        ContainerManager.addContainerListener(listener);
        UserManager.addUserListener(listener);
        SecurityManager.addGroupListener(listener);
        AuditLogService.get().registerAuditType(new MessageAuditProvider());
        ServiceRegistry.get().registerService(EmailService.class, new EmailServiceImpl());

        TourListener tourListener = new TourListener();
        ContainerManager.addContainerListener(tourListener);

        // Editors can read and respond to secure message boards
        RoleManager.registerPermission(new SecureMessageBoardReadPermission());
        RoleManager.registerPermission(new SecureMessageBoardRespondPermission());
        Role editor = RoleManager.getRole(EditorRole.class);
        editor.addPermission(SecureMessageBoardReadPermission.class);
        editor.addPermission(SecureMessageBoardRespondPermission.class);

        RoleManager.registerPermission(new InsertMessagePermission(),true);
        RoleManager.registerRole(new MessageBoardContributorRole());

        // initialize message digests
        DailyMessageDigest.getInstance().addProvider(new AnnouncementDigestProvider());

        // initialize message config service and add a config provider for announcements
        ServiceRegistry.get().registerService(MessageConfigService.class, new MessageConfigServiceImpl());
        MessageConfigService.get().registerConfigType(new AnnouncementEmailConfig());
        
        SearchService ss = SearchService.get();

        if (null != ss)
        {
            ss.addSearchCategory(AnnouncementManager.searchCategory);
            ss.addDocumentProvider(this);
        }

        FolderSerializationRegistry fsr = ServiceRegistry.get().getService(FolderSerializationRegistry.class);
        if (null != fsr)
        {
            fsr.addFactories(new NotificationSettingsWriterFactory(), new NotificationSettingsImporterFactory());
        }

        TourService.setInstance(new TourServiceImpl());
    }


    @Override
    public void startBackgroundThreads()
    {
        DailyMessageDigest.getInstance().initializeTimer();
    }

    @Override
    @NotNull
    public Set<Class> getIntegrationTests()
    {
        return new HashSet<>(Arrays.asList(
                AnnouncementManager.TestCase.class,
                EmailServiceImpl.TestCase.class
        ));
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return PageFlowUtil.set(CommSchema.getInstance().getSchemaName());
    }

    @NotNull
    public Collection<String> getSummary(Container c)
    {
        List<String> list = new ArrayList<>(1);
        long count = AnnouncementManager.getMessageCount(c);

        if (count > 0)
            list.add("" + count + " " + (count > 1 ? "Messages/Responses" : "Message"));

        return list;
    }


    public void enumerateDocuments(final SearchService.IndexTask task, final @NotNull Container c, final Date modifiedSince)
    {
        Runnable r = () -> AnnouncementManager.indexMessages(task, c, modifiedSince);
        task.addRunnable(r, SearchService.PRIORITY.bulk);
    }

    public void indexDeleted()
    {
        new SqlExecutor(CommSchema.getInstance().getSchema()).execute("UPDATE comm.announcements SET LastIndexed=NULL");
    }
}
