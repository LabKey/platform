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
package org.labkey.issue;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.notification.NotificationService;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.issues.IssuesListDefService;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.search.SearchService;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.emailTemplate.EmailTemplateService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;
import org.labkey.issue.model.GeneralIssuesListDefProvider;
import org.labkey.issue.model.Issue;
import org.labkey.issue.model.IssueManager;
import org.labkey.issue.model.IssueCommentType;
import org.labkey.issue.model.IssuesListDefServiceImpl;
import org.labkey.issue.query.IssueDefDomainKind;
import org.labkey.issue.query.IssuesQuerySchema;
import org.labkey.issue.view.IssuesSummaryWebPartFactory;
import org.labkey.issue.view.IssuesWebPartFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.Set;

/**
 * User: migra
 * Date: Jul 18, 2005
 * Time: 3:48:21 PM
 */
public class IssuesModule extends DefaultModule implements SearchService.DocumentProvider
{
    public static final String NAME = "Issues";

    public String getName()
    {
        return NAME;
    }

    public double getVersion()
    {
        return 18.10;
    }

    protected void init()
    {
        addController("issues", IssuesController.class);
        IssuesQuerySchema.register(this);

        EmailTemplateService.get().registerTemplate(IssueUpdateEmailTemplate.class);
        PropertyService.get().registerDomainKind(new IssueDefDomainKind());

        IssuesListDefService.setInstance(new IssuesListDefServiceImpl());
        IssuesListDefService.get().registerIssuesListDefProvider(new GeneralIssuesListDefProvider());

        NotificationService.get().registerNotificationType(Issue.class.getName(), "Issues", "fa-bug");
        AttachmentService.get().registerAttachmentType(IssueCommentType.get());
    }

    @NotNull
    protected Collection<WebPartFactory> createWebPartFactories()
    {
        ArrayList<WebPartFactory> result = new ArrayList<>();

        result.add(new BaseWebPartFactory("Issue Definitions")
        {
            @Override
            public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
            {
                UserSchema schema = QueryService.get().getUserSchema(portalCtx.getUser(), portalCtx.getContainer(), IssuesQuerySchema.SCHEMA_NAME);
                QuerySettings settings = schema.getSettings(portalCtx, IssuesQuerySchema.TableType.IssueListDef.name(), IssuesQuerySchema.TableType.IssueListDef.name());

                QueryView view = schema.createView(portalCtx, settings, null);
                view.setFrame(WebPartView.FrameType.PORTAL);
                view.setTitle("Issue Definitions");

                return view;
            }
        });

        result.add(new IssuesWebPartFactory());
        result.add(new IssuesSummaryWebPartFactory());

        return result;
    }

    public boolean hasScripts()
    {
        return true;
    }


    public void doStartup(ModuleContext moduleContext)
    {
        ContainerManager.addContainerListener(new IssueContainerListener());
        SecurityManager.addGroupListener(new IssueGroupListener());
        UserManager.addUserListener(new IssueUserListener());

        SearchService ss = SearchService.get();

        if (null != ss)
        {
            ss.addSearchCategory(IssueManager.searchCategory);
            ss.addResourceResolver("issue", IssueManager.getSearchResolver());
            ss.addDocumentProvider(this);
            ss.addSearchResultTemplate(new IssuesController.IssueSearchResultTemplate());
        }
    }

    @NotNull
    @Override
    public Collection<String> getSummary(Container c)
    {
        Collection<String> list = new LinkedList<>();
        long count = IssueManager.getIssueCount(c);
        if (count > 0)
            list.add("" + count + " Issue" + (count > 1 ? "s" : ""));
        return list;
    }

    @Override
    public TabDisplayMode getTabDisplayMode()
    {
        return Module.TabDisplayMode.DISPLAY_USER_PREFERENCE;
    }

    @Override
    public ActionURL getTabURL(Container c, User user)
    {
        return new ActionURL(IssuesController.BeginAction.class, c).addParameter(DataRegion.LAST_FILTER_PARAM, true);
    }

    @NotNull
    public Set<Class> getIntegrationTests()
    {
        return Collections.singleton(org.labkey.issue.model.IssueManager.TestCase.class);
    }

    @Override
    @NotNull
    public Set<String> getSchemaNames()
    {
        return PageFlowUtil.set(IssuesSchema.getInstance().getSchemaName());
    }

    public void enumerateDocuments(final SearchService.IndexTask task, final @NotNull Container c, final Date modifiedSince)
    {
        Runnable r = () -> IssueManager.indexIssues(task, c, modifiedSince);
        task.addRunnable(r, SearchService.PRIORITY.bulk);
    }

    public void indexDeleted()
    {
        new SqlExecutor(IssuesSchema.getInstance().getSchema()).execute("UPDATE issues.issues SET lastIndexed=NULL");
    }

    @Nullable
    @Override
    public UpgradeCode getUpgradeCode()
    {
        return new IssuesUpgradeCode();
    }
}
