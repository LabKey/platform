/*
 * Copyright (c) 2004-2017 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.beanutils.BeanMap;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleApiJsonForm;
import org.labkey.api.action.SimpleRedirectAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.admin.notification.NotificationService;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.BaseDownloadAction;
import org.labkey.api.attachments.SpringAttachmentFile;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.BeanViewForm;
import org.labkey.api.data.ColumnHeaderType;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.data.PHI;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TSVGridWriter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.issues.AbstractIssuesListDefDomainKind;
import org.labkey.api.issues.IssueDetailHeaderLinkProvider;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.issues.IssuesUrls;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.search.SearchResultTemplate;
import org.labkey.api.search.SearchScope;
import org.labkey.api.search.SearchUrls;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.OwnerRole;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.util.Button;
import org.labkey.api.util.GUID;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.GWTView;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.writer.ContainerUser;
import org.labkey.issue.actions.ChangeSummary;
import org.labkey.issue.actions.DeleteIssueListAction;
import org.labkey.issue.actions.GetRelatedFolder;
import org.labkey.issue.actions.InsertIssueDefAction;
import org.labkey.issue.actions.IssueServiceAction;
import org.labkey.issue.actions.IssueValidation;
import org.labkey.issue.actions.RepairIssueLookupsAction;
import org.labkey.issue.actions.UpgradeIssuesAction;
import org.labkey.issue.actions.ValidateIssueDefNameAction;
import org.labkey.issue.model.CommentAttachmentParent;
import org.labkey.issue.model.CustomColumn;
import org.labkey.issue.model.Issue;
import org.labkey.issue.model.Issue.Comment;
import org.labkey.issue.model.IssueListDef;
import org.labkey.issue.model.IssueManager;
import org.labkey.issue.model.IssuePage;
import org.labkey.issue.query.IssuesQuerySchema;
import org.labkey.issue.view.IssuesListView;
import org.springframework.util.LinkedCaseInsensitiveMap;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.validation.ObjectError;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public class IssuesController extends SpringActionController
{
    private static final Logger _log = Logger.getLogger(IssuesController.class);
    private static final String helpTopic = "issues";
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(
            IssuesController.class,
            IssueServiceAction.class,
            GetRelatedFolder.class,
            InsertIssueDefAction.class,
            ValidateIssueDefNameAction.class,
            UpgradeIssuesAction.class,
            DeleteIssueListAction.class,
            RepairIssueLookupsAction.class);

    public IssuesController() throws Exception
    {
        setActionResolver(_actionResolver);
    }

    public static class IssuesUrlsImpl implements IssuesUrls
    {
        @Override
        public ActionURL getDetailsURL(Container c)
        {
            return new ActionURL(DetailsAction.class, c);
        }

        @Override
        public ActionURL getInsertURL(Container c, String issueDefName)
        {
            ActionURL url = new ActionURL(InsertAction.class, c);
            url.addParameter("issueDefName", issueDefName);
            return url;
        }

        @Override
        public ActionURL getListURL(Container c, String issueDefName)
        {
            ActionURL url = new ActionURL(ListAction.class, c);
            url.addParameter("issueDefName", issueDefName);
            return url;
        }
    }


    public static ActionURL getDetailsURL(Container c, Integer issueId, boolean print)
    {
        ActionURL url = new ActionURL(DetailsAction.class, c);

        if (print)
            url.addParameter("_print", "1");

        if (null != issueId)
            url.addParameter("issueId", issueId.toString());

        return url;
    }


    @Override
    public PageConfig defaultPageConfig()
    {
        PageConfig config = super.defaultPageConfig();
        config.setHelpTopic(new HelpTopic(helpTopic));

        String templateHeader = getViewContext().getRequest().getHeader("X-TEMPLATE");
        if (!StringUtils.isEmpty(templateHeader))
        {
            try { config.setTemplate(PageConfig.Template.valueOf(templateHeader)); } catch (IllegalArgumentException x) { /* */ }
        }

        return config;
    }

    /**
     * @param redirect if the issue isn't in this container, whether to redirect the browser to same URL except in the
     * issue's parent container
     * @throws RedirectException if the issue lives in another container and the user has at least read permission to it
     */
    private Issue getIssue(int issueId, boolean redirect) throws RedirectException
    {
        Issue result = IssueManager.getIssue(redirect ? null : getContainer(), getUser(), issueId);
        // See if it's from a different container
        if (result != null && redirect && !result.getContainerId().equals(getContainer().getId()))
        {
            Container issueContainer = ContainerManager.getForId(result.getContainerId());
            // Make sure the user has read permission before redirecting
            if (issueContainer.hasPermission(getUser(), ReadPermission.class))
            {
                ActionURL url = getViewContext().getActionURL().clone();
                url.setContainer(issueContainer);
                throw new RedirectException(url);
            }
            return null;
        }
        return result;
    }


    private ActionURL issueURL(Class<? extends Controller> action)
    {
        return new ActionURL(action, getContainer());
    }


    public static ActionURL issueURL(Container c, Class<? extends Controller> action)
    {
        return new ActionURL(action, c);
    }


    @RequiresPermission(ReadPermission.class)
    public class BeginAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            if (IssueManager.getIssueListDefs(getContainer()).size() > 1)
            {
                ActionURL url = QueryService.get().urlFor(getUser(), getContainer(), QueryAction.executeQuery, "issues", IssuesQuerySchema.TableType.IssueListDef.name());
                return HttpView.redirect(url);
            }
            else
                return HttpView.redirect(getListURL(getContainer()));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Issues", getListURL(getContainer()));
        }
    }


    private CustomColumnConfiguration getCustomColumnConfiguration()
    {
        return IssueManager.getCustomColumnConfiguration(getContainer());
    }


    public static ActionURL getListURL(Container c)
    {
        ActionURL url = new ActionURL(ListAction.class, c);
        url.addParameter(DataRegion.LAST_FILTER_PARAM, "true");
        return url;
    }


    @RequiresPermission(ReadPermission.class)
    public class ListAction extends SimpleViewAction<ListForm>
    {
        public ListAction() {}

        public ListAction(ViewContext ctx)
        {
            setViewContext(ctx);
        }

        @Override
        public ModelAndView getView(IssuesController.ListForm form, BindException errors) throws Exception
        {
            String issueDefName = getViewContext().getActionURL().getParameter(IssuesListView.ISSUE_LIST_DEF_NAME);
            if (issueDefName == null)
                issueDefName = IssueManager.getDefaultIssueListDefName(getContainer());

            IssueListDef issueListDef = IssueManager.getIssueListDef(getContainer(), issueDefName);
            if (issueListDef != null)
            {
                IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(getContainer(), issueDefName);

                // convert AssignedTo/Email to AssignedTo/DisplayName: old bookmarks
                // reference Email, which is no longer displayed.
                ActionURL url = getViewContext().cloneActionURL();
                String[] emailFilters = url.getKeysByPrefix(issueDefName + ".AssignedTo/Email");
                if (emailFilters != null && emailFilters.length > 0)
                {
                    for (String emailFilter : emailFilters)
                        url.deleteParameter(emailFilter);
                    return HttpView.redirect(url);
                }

                getPageConfig().setRssProperties(new IssuesController.RssAction().getUrl(), names.pluralName);

                return new IssuesListView(issueDefName);
            }
            return new HtmlView(getUndefinedIssueListMessage(getViewContext(), issueDefName));
        }

        @Nullable
        private String getIssueDefName()
        {
            String issueDefName = getViewContext().getActionURL().getParameter(IssuesListView.ISSUE_LIST_DEF_NAME);
            if (issueDefName == null)
            {
                String issueId = getViewContext().getActionURL().getParameter("issueId");
                if (issueId != null)
                {
                    Issue issue = IssueManager.getIssue(getContainer(), getUser(), NumberUtils.toInt(issueId));
                    if (issue != null)
                    {
                        IssueListDef def = IssueManager.getIssueListDef(issue);
                        return def != null ? def.getName() : null;
                    }
                }
            }
            return issueDefName;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            String issueDefName = getViewContext().getActionURL().getParameter(IssuesListView.ISSUE_LIST_DEF_NAME);
            if (issueDefName == null)
                issueDefName = IssueManager.getDefaultIssueListDefName(getContainer());

            IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(getContainer(), issueDefName != null ? issueDefName : IssueListDef.DEFAULT_ISSUE_LIST_NAME);

            ActionURL url = new ActionURL(ListAction.class, getContainer()).
                    addParameter(IssuesListView.ISSUE_LIST_DEF_NAME, getIssueDefName()).
                    addParameter(DataRegion.LAST_FILTER_PARAM, "true");

            return root.addChild(names.pluralName + " List", url);
        }
    }


    @RequiresPermission(ReadPermission.class)
    public class ExportTsvAction extends SimpleViewAction<QueryForm>
    {
        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            getPageConfig().setTemplate(PageConfig.Template.None);
            QueryView view = QueryView.create(form, errors);
            HttpServletResponse response = getViewContext().getResponse();
            response.setHeader("X-Robots-Tag", "noindex");
            final TSVGridWriter writer = view.getTsvWriter();
            return new HttpView()
            {
                @Override
                protected void renderInternal(Object model, HttpServletRequest request, HttpServletResponse response) throws Exception
                {
                    writer.setColumnHeaderType(ColumnHeaderType.Caption);
                    writer.write(getViewContext().getResponse());
                }
            };
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class DetailsAction extends AbstractIssueAction
    {
        public DetailsAction()
        {
        }

        public DetailsAction(Issue issue, ViewContext context)
        {
            _issue = issue;
            setViewContext(context);
        }

        @Override
        public ModelAndView getView(IssuesController.IssuesForm form, boolean reshow, BindException errors) throws Exception
        {
            int issueId = form.getIssueId();
            _issue = getIssue(issueId, true);

            IssueManager.EntryTypeNames names =  getEntryTypeNames();
            if (null == _issue)
            {
                throw new NotFoundException("Unable to find " + names.singularName + " " + form.getIssueId());
            }

            IssuePage page = new IssuePage(getContainer(), getUser());
            page.setMode(DataRegion.MODE_DETAILS);
            page.setPrint(isPrint());
            page.setIssue(_issue);
            page.setCustomColumnConfiguration(getColumnConfiguration());
            //pass user's update perms to jsp page to determine whether to show notify list
            page.setUserHasUpdatePermissions(hasUpdatePermission(getUser(), _issue));
            page.setUserHasAdminPermissions(hasAdminPermission(getUser(), _issue));
            page.setMoveDestinations(IssueManager.getMoveDestinationContainers(getContainer(), getUser(), getIssueListDef().getName()).size() != 0);
            page.setRequiredFields(IssueManager.getRequiredIssueFields(getContainer()));
            page.setIssueListDef(getIssueListDef());

            IssuesQuerySchema schema = new IssuesQuerySchema(getUser(), getContainer());
            TableInfo issueTable = schema.createTable(getIssueListDef().getName());
            page.setAdditionalDetailInfo(getIssueListDef().getDomainKind().getAdditionalDetailInfo(issueTable, issueId));

            // remove any notifications related to this user/objectid/type
            NotificationService.get().removeNotifications(getContainer(), "issue:" + _issue.getIssueId(), Arrays.asList(Issue.class.getName()), getUser().getUserId());

            return new JspView<>("/org/labkey/issue/view/detailView.jsp", page);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return new ListAction(getViewContext()).appendNavTrail(root).
                    addChild(getSingularEntityName() + " " + _issue.getIssueId() + ": " + StringUtils.trimToEmpty(_issue.getTitle()), getURL());
        }

        public ActionURL getURL()
        {
            return new ActionURL(DetailsAction.class, getContainer()).addParameter("issueId", _issue.getIssueId());
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class DetailsListAction extends AbstractIssueAction
    {
        @Override
        public ModelAndView getView(IssuesController.IssuesForm form, boolean reshow, BindException errors) throws Exception
        {
            IssueListDef issueListDef = IssueManager.getIssueListDef(getContainer(), form.getIssueDefName());
            HttpView view;

            if (issueListDef != null)
            {
                _issue = new Issue();
                _issue.setIssueDefName(form.getIssueDefName());

                // convert AssignedTo/Email to AssignedTo/DisplayName: old bookmarks
                // reference Email, which is no longer displayed.
                ActionURL url = getViewContext().cloneActionURL();
                String[] emailFilters = url.getKeysByPrefix(form.getIssueDefName() + ".AssignedTo/Email");
                if (emailFilters != null && emailFilters.length > 0)
                {
                    for (String emailFilter : emailFilters)
                        url.deleteParameter(emailFilter);
                    return HttpView.redirect(url);
                }

                Set<String> issueIds = DataRegionSelection.getSelected(getViewContext(), false);
                if (issueIds.isEmpty())
                {
                    issueIds = new LinkedHashSet<>();

                    UserSchema userSchema = QueryService.get().getUserSchema(getUser(), getContainer(), IssuesQuerySchema.SCHEMA_NAME);
                    TableInfo table = userSchema.getTable(form.getIssueDefName());
                    if (table != null)
                    {
                        List<String> ids = new TableSelector(table, Collections.singleton(table.getColumn(FieldKey.fromParts("issueId"))),
                                null, null).getArrayList(String.class);

                        issueIds.addAll(ids);
                    }
                }

                IssuePage page = new IssuePage(getContainer(), getUser());
                page.setPrint(isPrint());
                view = new JspView<>("/org/labkey/issue/view/detailList.jsp", page);

                page.setMode(DataRegion.MODE_DETAILS);
                page.setIssueIds(issueIds);
                page.setCustomColumnConfiguration(getColumnConfiguration());
                page.setRequiredFields(IssueManager.getRequiredIssueFields(getContainer()));
                page.setDataRegionSelectionKey(getViewContext().getRequest().getParameter(DataRegionSelection.DATA_REGION_SELECTION_KEY));
                page.setIssueListDef(getIssueListDef());

                getPageConfig().setNoIndex(); // We want crawlers to index the single issue detail page, no the multiple page
                getPageConfig().setNoFollow();
            }
            else
            {
                view = new HtmlView("<span class='labkey-error'>Invalid or missing issue list name specified</span><p>");
            }

            return view;
        }

        @Override
        public boolean handlePost(IssuesController.IssuesForm form, BindException errors) throws Exception
        {
            return true;
        }

        @Override
        public ActionURL getSuccessURL(IssuesController.IssuesForm form)
        {
            return null;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            IssueManager.EntryTypeNames names = getEntryTypeNames();
            return new ListAction(getViewContext()).appendNavTrail(root).addChild(names.singularName + " Details");
        }
    }


    @RequiresPermission(InsertPermission.class)
    public class InsertAction extends AbstractIssueAction
    {
        @Override
        public ModelAndView getView(IssuesController.IssuesForm form, boolean reshow, BindException errors) throws Exception
        {
            _issue = reshow ? form.getBean() : new Issue();
            _issue.setIssueDefName(form.getIssueDefName() != null ? form.getIssueDefName() : IssueManager.getDefaultIssueListDefName(getContainer()));

            IssueListDef issueListDef = IssueManager.getIssueListDef(getContainer(), _issue.getIssueDefName());
            if (issueListDef == null)
            {
                return new HtmlView(getUndefinedIssueListMessage(getViewContext(), _issue.getIssueDefName()));
            }

            if (_issue.getAssignedTo() != null)
            {
                User user = UserManager.getUser(_issue.getAssignedTo());

                if (user != null)
                {
                    _issue.setAssignedTo(user.getUserId());
                }
            }

            User defaultUser = IssueManager.getDefaultAssignedToUser(getContainer(), getIssueListDef().getName());
            if (defaultUser != null)
                _issue.setAssignedTo(defaultUser.getUserId());

            _issue.open(getContainer(), getUser());

            if (NumberUtils.isNumber(form.getPriority()))
                _issue.setPriority(Integer.parseInt(form.getPriority()));

            // add any of the default values from the URL for the custom/extra properties
            CustomColumnConfiguration customColumnConfig = getColumnConfiguration();
            for (DomainProperty property : customColumnConfig.getCustomProperties())
            {
                String paramName = IssueDetailHeaderLinkProvider.PARAM_PREFIX + "." + property.getName();
                String paramVal = getViewContext().getRequest().getParameter(paramName);
                if (paramVal != null)
                    _issue.setProperty(property.getName(), paramVal);
            }

            beforeReshow(reshow, form, _issue, issueListDef);

            IssuePage page = new IssuePage(getContainer(), getUser());
            page.setAction(InsertAction.class);
            page.setMode(DataRegion.MODE_UPDATE);
            page.setIssue(_issue);
            page.setPrevIssue(_issue);
            page.setCustomColumnConfiguration(customColumnConfig);
            page.setBody(form.getComment() == null ? form.getBody() : form.getComment());
            page.setCallbackURL(form.getCallbackURL());
            page.setReturnURL(form.getReturnActionURL());
            page.setVisibleFields(getVisibleFields(page.getAction(), customColumnConfig));
            page.setReadOnlyFields(getReadOnlyFields(page.getAction()));
            page.setRequiredFields(IssueManager.getRequiredIssueFields(getContainer()));
            page.setErrors(errors);
            page.setIssueListDef(getIssueListDef());
            page.setDirty(form.isDirty());

            return new JspView<>("/org/labkey/issue/view/updateView.jsp", page);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            IssueManager.EntryTypeNames names = getEntryTypeNames();
            return new ListAction(getViewContext()).appendNavTrail(root).addChild("Insert New " + names.singularName);
        }

        @Override
        public ActionURL getSuccessURL(IssuesController.IssuesForm form)
        {
            if (!StringUtils.isEmpty(form.getCallbackURL()))
            {
                ActionURL url = new ActionURL(form.getCallbackURL());
                url.addParameter("issueId", _issue.getIssueId());
                url.addParameter("assignedTo", _issue.getAssignedTo());
                return url;
            }

            return form.getReturnActionURL(getDetailsURL(getContainer(), _issue.getIssueId(), false));
        }
    }

    /**
     * Generates a standard message if no issue list is available in the current folder (plus a link to create a list)
     */
    public static String getUndefinedIssueListMessage(ContainerUser context, String issueDefName)
    {
        String warningMessage =
                issueDefName == null ?
                (IssueManager.getIssueListDefs(context.getContainer()).isEmpty() ?
                        "There are no issues lists defined for this folder." :
                        String.format("'%s' not specified.", IssuesListView.ISSUE_LIST_DEF_NAME)) :
                String.format("There is no issues list '%s' defined in this folder.", issueDefName);
        StringBuilder sb = new StringBuilder().append("<span class='labkey-error'>").append(PageFlowUtil.filter(warningMessage)).append("</span><p>");
        boolean userHasAdmin = context.getContainer().hasPermission(context.getUser(), AdminPermission.class);
        Button button = PageFlowUtil.button(userHasAdmin ? "Manage Issue List Definitions" : "Show Available Issue Lists").href(QueryService.get().urlFor(context.getUser(),
                context.getContainer(),
                QueryAction.executeQuery,
                "issues",
                IssuesQuerySchema.TableType.IssueListDef.name())).build();
        sb.append(button.toString());

        return sb.toString();
    }

    public static class IssuesApiForm extends SimpleApiJsonForm
    {
        private JSONArray _issues;
        private List<IssuesForm> _issueForms;

        public void setIssues(JSONArray issues)
        {
            _issues = issues;
        }

        /**
         * Parse out the issues forms from the JSON data
         */
        public List<IssuesForm> getIssueForms()
        {
            if (_issueForms == null)
            {
                _issueForms = new ArrayList<>();
                if (_issues != null)
                {
                    for (JSONObject rec : _issues.toJSONObjectArray())
                    {
                        IssuesForm form = new IssuesForm();
                        Map<String, String> stringMap = new CaseInsensitiveHashMap<>();
                        for (String prop : rec.keySet())
                        {
                            stringMap.put(prop, rec.getString(prop));
                        }
                        form.setStrings(stringMap);
                        _issueForms.add(form);
                    }
                }
            }
            return _issueForms;
        }
    }

    @RequiresPermission(InsertPermission.class)
    public class InsertIssuesAction extends ApiAction<IssuesApiForm>
    {
        @Override
        public void validateForm(IssuesApiForm form, Errors errors)
        {
            if (form.getIssueForms().isEmpty())
            {
                errors.reject(SpringActionController.ERROR_MSG, "At least one issue record is required");
                return;
            }

            for (IssuesForm issuesForm : form.getIssueForms())
            {
                IssueListDef issueListDef = IssueManager.getIssueListDef(getContainer(), NumberUtils.toInt(issuesForm.getIssueDefId()));
                if (issueListDef == null)
                {
                    errors.reject(ERROR_MSG, "IssueListDef not found: " + issuesForm.getIssueDefId());
                    continue;
                }

                AbstractIssuesListDefDomainKind kind = issueListDef.getDomainKind();
                if (kind != null)
                {
                    Map<String, String> stringMap = new CaseInsensitiveHashMap<>(issuesForm.getStrings());
                    for (PropertyStorageSpec prop : kind.getRequiredProperties())
                    {
                        if (!"resolution".equalsIgnoreCase(prop.getName()))
                            stringMap.putIfAbsent(prop.getName(), null);
                    }
                    issuesForm.setStrings(stringMap);
                }
                CustomColumnConfiguration ccc = new NewCustomColumnConfiguration(getContainer(), getUser(), issueListDef);
                IssueValidation.validateRequiredFields(issueListDef, ccc, issuesForm, getUser(), errors);
                IssueValidation.validateNotifyList(issuesForm, errors);
                IssueValidation.validateAssignedTo(issuesForm, getContainer(), errors);
                IssueValidation.validateStringFields(issuesForm, ccc, errors);
            }
        }

        @Override
        public ApiResponse execute(IssuesApiForm form, BindException errors) throws Exception
        {
            Map<String, MultipartFile> attachmentMap = getFileMap();

            // need to track issues that should be related together
            List<Issue> newIssues = new ArrayList<>();
            List<Integer> newIssueIds = new ArrayList<>();
            try (DbScope.Transaction transaction = IssuesSchema.getInstance().getSchema().getScope().ensureTransaction())
            {
                for (IssuesForm issuesForm : form.getIssueForms())
                {
                    IssueListDef issueListDef = IssueManager.getIssueListDef(getContainer(), NumberUtils.toInt(issuesForm.getIssueDefId()));
                    if (issueListDef != null)
                    {
                        CustomColumnConfiguration ccc = new NewCustomColumnConfiguration(getContainer(), getUser(), issueListDef);
                        Issue issue = issuesForm.getBean();

                        issue.open(getContainer(), getUser());
                        Issue prevIssue = new Issue();
                        prevIssue.open(getContainer(), getUser());

                        // validate any related issue values
                        IssueValidation.relatedIssueHandler(issue, getUser(), errors);
                        if (errors.hasErrors())
                            return null;

                        // bind the user schema table to the form bean so we can get typed properties
                        UserSchema userSchema = QueryService.get().getUserSchema(getUser(), getContainer(), IssuesQuerySchema.SCHEMA_NAME);
                        TableInfo table = userSchema.getTable(issueListDef.getName());
                        issuesForm.setTable(table);
                        issue.setProperties(issuesForm.getTypedColumns());

                        // convert from email addresses & display names to userids before we hit the database
                        issue.parseNotifyList(issue.getNotifyList());

                        ChangeSummary changeSummary = ChangeSummary.createChangeSummary(getViewContext(), issueListDef,
                                issue, prevIssue, null, getContainer(), getUser(), InsertAction.class, issuesForm.getComment(), ccc);
                        IssueManager.saveIssue(getUser(), getContainer(), issue);
                        newIssues.add(issue);
                        newIssueIds.add(issue.getIssueId());

                        // handle attachments, the attachment value is a | delimited array of file names
                        String attachments = (String)issuesForm.get("attachment");
                        if (!StringUtils.isBlank(attachments))
                        {
                            List<AttachmentFile> attachmentFiles = new ArrayList<>();
                            for (String name : attachments.split("\\|"))
                            {
                                if (attachmentMap.containsKey(name.trim()))
                                {
                                    MultipartFile file = attachmentMap.get(name.trim());
                                    if (!file.isEmpty())
                                    {
                                        attachmentFiles.add(new SpringAttachmentFile(file));
                                    }
                                }
                            }

                            if (!attachmentFiles.isEmpty())
                                AttachmentService.get().addAttachments(new CommentAttachmentParent(changeSummary.getComment()), attachmentFiles, getUser());
                        }
                    }
                }

                // link up any related issues
                int idx = 0;
                Map<Integer, Issue> relatedIssuesToSave = new HashMap<>();
                for (IssuesForm issuesForm : form.getIssueForms())
                {
                    int related = NumberUtils.toInt((String)issuesForm.get("relatedIssue"), -1);
                    if (related != -1)
                    {
                        // stash everything away so we only have to do one save
                        if (!relatedIssuesToSave.containsKey(idx))
                            relatedIssuesToSave.put(idx, newIssues.get(idx));
                        if (!relatedIssuesToSave.containsKey(related))
                            relatedIssuesToSave.put(related, newIssues.get(related));

                        Issue current = relatedIssuesToSave.get(idx);
                        Issue relatedIssue = relatedIssuesToSave.get(related);

                        if (current != null && relatedIssue != null)
                        {
                            addRelatedIssue(current, relatedIssue.getIssueId());
                            addRelatedIssue(relatedIssue, current.getIssueId());
                        }
                    }
                    idx++;
                }

                // save any related issues
                for (Issue issue : relatedIssuesToSave.values())
                {
                    IssueManager.saveIssue(getUser(), getContainer(), issue);
                }
                transaction.commit();

                ApiSimpleResponse response = new ApiSimpleResponse();
                response.put("issues", newIssueIds);
                response.put("success", true);
                return response;
            }
        }

        private void addRelatedIssue(Issue issue, int relatedIssueId)
        {
            Set<Integer> newRelated = new TreeSet<>();
            newRelated.addAll(issue.getRelatedIssues());
            newRelated.add(relatedIssueId);

            issue.setRelatedIssues(newRelated);
        }
    }

    abstract class AbstractIssueAction extends FormViewAction<IssuesForm>
    {
        protected Issue _issue = null;
        private IssueListDef _issueListDef;
        private CustomColumnConfiguration _columnConfiguration;

        public boolean handlePost(IssuesForm form, BindException errors) throws Exception
        {
            if (form.getSkipPost())
                return false;

            Container c = getContainer();
            User user = getUser();

            Issue issue = form.getBean();
            setIssue(issue);

            // validate any related issue values
            IssueValidation.relatedIssueHandler(issue, user, errors);
            if (errors.hasErrors())
                return false;

            IssueListDef issueListDef = getIssueListDef();
            if (issueListDef == null)
                return false;

            // bind the user schema table to the form bean so we can get typed properties
            UserSchema userSchema = QueryService.get().getUserSchema(getUser(), getContainer(), IssuesQuerySchema.SCHEMA_NAME);
            TableInfo table = userSchema.getTable(issueListDef.getName());
            form.setTable(table);
            form.isValid();
            issue.setProperties(form.getTypedColumns());

            Issue prevIssue = (Issue)form.getOldValues();
            requiresUpdatePermission(user, issue);
            ActionURL detailsUrl;

            // check for no op
            if (UpdateAction.class.equals(form.getAction()) && form.getComment().equals("") && issue.equals(prevIssue))
                return true;

            // clear resolution, resolvedBy, and duplicate fields
            if (ReopenAction.class.equals(form.getAction()))
                issue.beforeReOpen(getContainer());

            Issue duplicateOf = null;
            if (ResolveAction.class.equals(form.getAction()) &&
                    issue.getResolution() != null &&
                    issue.getResolution().equals("Duplicate") &&
                    issue.getDuplicate() != null &&
                    !issue.getDuplicate().equals(prevIssue.getDuplicate()))
            {
                if (issue.getDuplicate() == issue.getIssueId())
                {
                    errors.reject(SpringActionController.ERROR_MSG, "An issue may not be a duplicate of itself");
                    return false;
                }
                duplicateOf = IssueManager.getIssue(null, getUser(), issue.getDuplicate().intValue());
                if (duplicateOf == null || duplicateOf.lookupContainer() == null)
                {
                    errors.reject(SpringActionController.ERROR_MSG, "Duplicate issue '" + issue.getDuplicate().intValue() + "' not found");
                    return false;
                }
                if (!duplicateOf.lookupContainer().hasPermission(user, ReadPermission.class))
                {
                    errors.reject(SpringActionController.ERROR_MSG, "User does not have Read permission for duplicate issue '" + issue.getDuplicate().intValue() + "'");
                    return false;
                }
            }

            // get previous related issue ids before updating
            Set<Integer> prevRelatedIds = new HashSet<>();
            if (prevIssue != null)
                prevRelatedIds = prevIssue.getRelatedIssues();

            ChangeSummary changeSummary;
            try (DbScope.Transaction transaction = IssuesSchema.getInstance().getSchema().getScope().ensureTransaction())
            {
                if (InsertAction.class.equals(form.getAction()))
                {
                    // for new issues, the original is always the default.
                    issue.open(c, user);
                    prevIssue = new Issue();
                    prevIssue.open(getContainer(), getUser());
                }
                else if (ResolveAction.class.equals(form.getAction()))
                    issue.resolve(user);
                else if (ReopenAction.class.equals(form.getAction()))
                    issue.open(c, user);
                else if (CloseAction.class.equals(form.getAction()))
                    issue.close(user);
                else
                    issue.change(user);

                // convert from email addresses & display names to userids before we hit the database
                issue.parseNotifyList(issue.getNotifyList());

                changeSummary = ChangeSummary.createChangeSummary(getViewContext(), getIssueListDef(), issue, prevIssue, duplicateOf, c, user, form.getAction(), form.getComment(), getColumnConfiguration());
                IssueManager.saveIssue(user, c, issue);
                detailsUrl = new DetailsAction(issue, getViewContext()).getURL();
                AttachmentService.get().addAttachments(new CommentAttachmentParent(changeSummary.getComment()), getAttachmentFileList(), user);

                if (duplicateOf != null)
                {
                    StringBuilder sb = new StringBuilder();
                    sb.append("<em>Issue ").append(issue.getIssueId()).append(" marked as duplicate of this issue.</em>");
                    duplicateOf.addComment(user, sb.toString());
                    IssueManager.saveIssue(user, c, duplicateOf);
                }

                Set<Integer> newRelatedIds = issue.getRelatedIssues();

                // this list represents all the ids which will need related handling for a creating a relatedIssue entry
                Collection<Integer> newIssues = new ArrayList<>();
                newIssues.addAll(newRelatedIds);
                newIssues.removeAll(prevRelatedIds);

                for (int curIssueId : newIssues)
                {
                    Issue relatedIssue = ChangeSummary.relatedIssueCommentHandler(issue.getIssueId(), curIssueId, user, false);
                    IssueManager.saveIssue(user, getContainer(), relatedIssue);
                }

                // this list represents all the ids which will need related handling for dropping a relatedIssue entry
                if (!prevRelatedIds.equals(newRelatedIds))
                {
                    Collection<Integer> prevIssues = new ArrayList<>();
                    prevIssues.addAll(prevRelatedIds);
                    prevIssues.removeAll(newRelatedIds);
                    for (int curIssueId : prevIssues)
                    {
                        Issue relatedIssue = ChangeSummary.relatedIssueCommentHandler(issue.getIssueId(), curIssueId, user, true);
                        IssueManager.saveIssue(user, getContainer(), relatedIssue);
                    }
                }
                transaction.commit();
            }
            catch (IOException x)
            {
                String message = x.getMessage() == null ? x.toString() : x.getMessage();
                errors.addError(new ObjectError("main", new String[] {"Error"}, new Object[] {message}, message));
                return false;
            }

            // Send update email...
            //    ...if someone other than "created by" is closing a bug
            //    ...if someone other than "assigned to" is updating, reopening, or resolving a bug
            final String assignedTo = UserManager.getDisplayName(_issue.getAssignedTo(), user);
            String change;
            if (InsertAction.class.equals(form.getAction()))
            {
                if (assignedTo != null)
                    change = "opened and assigned to " + assignedTo;
                else
                    change = "opened";
            }
            else
                change = ReopenAction.class.equals(form.getAction()) ? "reopened" : getActionName(form.getAction()) + "d";

            if ("resolved".equalsIgnoreCase(change) && issue.getResolution() != null)
            {
                change += " as " + issue.getResolution(); // Issue 12273
            }
            changeSummary.sendUpdateEmail(c, user, form.getComment(), detailsUrl, change, getAttachmentFileList());
            return true;
        }

        @Override
        public void validateCommand(IssuesController.IssuesForm form, Errors errors)
        {
            if (!form.getSkipPost())
            {
                setIssue(form.getBean());

                IssueValidation.validateRequiredFields(getIssueListDef(), getCustomColumnConfiguration(), form, getUser(), errors);
                IssueValidation.validateNotifyList(form, errors);
                // don't validate the assigned to field if the issue is either closed or we are in the process
                // of closing it
                if (form.getStrings().containsKey("action") && !IssuesController.CloseAction.class.equals(form.getAction()))
                    IssueValidation.validateAssignedTo(form, getContainer(), errors);
                IssueValidation.validateStringFields(form, getColumnConfiguration(), errors);
            }
        }

        public ActionURL getSuccessURL(IssuesController.IssuesForm form)
        {
            if (getIssue(form.getIssueId(), false).getStatus().equals("closed"))
            {
                // redirect to the issue list only when closing an issue, all other updates redirect to the details view
                if (IssuesController.CloseAction.class.equals(form.getAction()))
                {
                    ActionURL url = new ActionURL(ListAction.class, getContainer()).addParameter(DataRegion.LAST_FILTER_PARAM, "true");
                    IssueListDef issueListDef = getIssueListDef();

                    if (issueListDef != null)
                        url.addParameter(IssuesListView.ISSUE_LIST_DEF_NAME, issueListDef.getName());

                    return url;
                }
            }

            return form.getForwardURL();
        }

        /**
         * @param redirect if the issue isn't in this container, whether to redirect the browser to same URL except in the
         * issue's parent container
         * @throws RedirectException if the issue lives in another container and the user has at least read permission to it
         */
        protected Issue getIssue(int issueId, boolean redirect) throws RedirectException
        {
            Issue result = IssueManager.getIssue(redirect ? null : getContainer(), getUser(), issueId);
            // See if it's from a different container
            if (result != null && redirect && !result.getContainerId().equals(getContainer().getId()))
            {
                Container issueContainer = ContainerManager.getForId(result.getContainerId());
                // Make sure the user has read permission before redirecting
                if (issueContainer.hasPermission(getUser(), ReadPermission.class))
                {
                    ActionURL url = getViewContext().getActionURL().clone();
                    url.setContainer(issueContainer);
                    throw new RedirectException(url);
                }
                return null;
            }
            return result;
        }

        public void setIssue(Issue issue)
        {
            _issue = issue;
        }

        public Issue getIssue()
        {
            return _issue;
        }

        protected String getSingularEntityName()
        {
            return getEntryTypeNames().singularName;
        }

        /**
         * Specifies which fields have visible values
         */
        protected Set<String> getVisibleFields(Class<? extends Controller> action, CustomColumnConfiguration ccc)
        {
            final Set<String> visible = new HashSet<>(20);

            visible.add("title");
            visible.add("assignedTo");
            visible.add("type");
            visible.add("area");
            visible.add("priority");
            visible.add("milestone");
            visible.add("comments");
            visible.add("attachments");

            // Add all the enabled custom fields
            for (CustomColumn cc : ccc.getCustomColumns())
            {
                visible.add(cc.getName());
            }
            visible.add("notifyList");

            if (ResolveAction.class.equals(action))
            {
                visible.add("resolution");
                visible.add("duplicate");
            }
            visible.add("related");

            return visible;
        }

        /**
         * Specifies which fields have read only values
         */
        protected Set<String> getReadOnlyFields(Class<? extends Controller> action)
        {
            final Set<String> readOnly = new HashSet<>(20);

            if (CloseAction.class == action)
                readOnly.add("assignedTo");

            return readOnly;
        }

        /**
         * Throw an exception if user does not have permission to update issue
         */
        protected void requiresUpdatePermission(User user, Issue issue) throws ServletException
        {
            if (!hasUpdatePermission(user, issue))
            {
                throw new UnauthorizedException();
            }
        }

        /**
         * Does this user have permission to update this issue?
         */
        protected boolean hasUpdatePermission(User user, Issue issue)
        {
            return getContainer().hasPermission(user, UpdatePermission.class,
                    (issue.getCreatedBy() == user.getUserId() ? RoleManager.roleSet(OwnerRole.class) : null));
        }

        protected boolean hasAdminPermission(User user, Issue issue)
        {
            return getContainer().hasPermission(user, AdminPermission.class,
                    (issue.getCreatedBy() == user.getUserId() ? RoleManager.roleSet(OwnerRole.class) : null));
        }

        public CustomColumnConfiguration getColumnConfiguration()
        {
            if (_columnConfiguration == null)
            {
                _columnConfiguration = new NewCustomColumnConfiguration(getContainer(), getUser(), getIssueListDef());
            }
            return _columnConfiguration;
        }

        @Nullable
        protected IssueListDef getIssueListDef()
        {
            if (_issueListDef == null)
            {
                Issue issue = getIssue();
                if (issue != null)
                {
                    String issueDefName = issue.getIssueDefName();
                    if (issueDefName != null)
                    {
                        _issueListDef = IssueManager.getIssueListDef(getContainer(), issueDefName);
                    }
                    else
                    {
                        _issueListDef = IssueManager.getIssueListDef(getIssue());
                    }
                }
            }
            return _issueListDef;
        }

        protected IssueManager.EntryTypeNames getEntryTypeNames()
        {
            IssueListDef issueListDef = getIssueListDef();
            return IssueManager.getEntryTypeNames(getContainer(), issueListDef != null ? issueListDef.getName() : IssueListDef.DEFAULT_ISSUE_LIST_NAME);
        }

        /**
         * Prior to reshowing the form, we want to propagate any custom field values that may
         * have been set before submitting.
         */
        protected void beforeReshow(boolean reshow, IssuesForm form, Issue issue, IssueListDef issueListDef)
        {
            if (reshow && issueListDef != null)
            {
                // bind the user schema table to the form bean so we can get typed properties
                UserSchema userSchema = QueryService.get().getUserSchema(getUser(), getContainer(), IssuesQuerySchema.SCHEMA_NAME);
                TableInfo table = userSchema.getTable(issueListDef.getName());
                form.setTable(table);
                issue.setProperties(form.getTypedColumns());
            }
        }
    }

    public static class NewCustomColumnConfiguration implements CustomColumnConfiguration
    {
        private Map<String, CustomColumn> _columnMap = new LinkedCaseInsensitiveMap<>();
        private Map<String, String> _captionMap = new LinkedCaseInsensitiveMap<>();
        private Set<String> _baseNames = new CaseInsensitiveHashSet();
        private Map<String, DomainProperty> _propertyMap = new CaseInsensitiveHashMap<>();
        private List<DomainProperty> _customProperties = new ArrayList<>();

        public NewCustomColumnConfiguration(Container c, User user, IssueListDef issueDef)
        {
            if (issueDef != null)
            {
                Domain domain = issueDef.getDomain(user);
                if (domain.getDomainKind() instanceof AbstractIssuesListDefDomainKind)
                {
                    _baseNames.addAll(Arrays.asList("title", "type", "area", "notifylist", "priority", "milestone",
                            "assignedto", "resolution"));
                }

                if (domain != null)
                {
                    for (DomainProperty prop : domain.getProperties())
                    {
                        _propertyMap.put(prop.getName(), prop);
                        if (!_baseNames.contains(prop.getName()))
                        {
                            _customProperties.add(prop);
                            // treat anything higher than NotPHI as needing special permission
                            CustomColumn col = new CustomColumn(c,
                                    prop.getName().toLowerCase(),
                                    prop.getLabel() != null ? prop.getLabel() : ColumnInfo.labelFromName(prop.getName()),
                                    prop.getLookup() != null,
                                    prop.getPHI().isExportLevelAllowed(PHI.NotPHI) ? ReadPermission.class : InsertPermission.class);

                            _columnMap.put(col.getName(), col);
                            _captionMap.put(col.getName(), col.getCaption());
                        }
                    }
                }
            }
        }

        @Override
        public Map<String, DomainProperty> getPropertyMap()
        {
            return _propertyMap;
        }

        @Override
        public Collection<DomainProperty> getCustomProperties()
        {
            return _customProperties;
        }

        @Override
        public CustomColumn getCustomColumn(String name)
        {
            return _columnMap.get(name);
        }

        @Override
        public Collection<CustomColumn> getCustomColumns()
        {
            return _columnMap.values();
        }

        @Override
        public Collection<CustomColumn> getCustomColumns(User user)
        {
            return _columnMap.values();
        }

        @Override
        public boolean shouldDisplay(String name)
        {
            return true;
        }

        @Override
        public boolean shouldDisplay(User user, String name)
        {
            CustomColumn col = _columnMap.get(name);
            if (col != null)
            {
                return col.getContainer().hasPermission(user, col.getPermission());
            }
            else if (_baseNames.contains(name))
            {
                // short term hack
                return true;
            }
            return false;
        }

        @Nullable
        @Override
        public String getCaption(String name)
        {
            CustomColumn col = _columnMap.get(name);
            return col != null ? col.getCaption() : ColumnInfo.labelFromName(name);
        }
    }

    // SAME as AttachmentForm, just to demonstrate GuidString
    public static class _AttachmentForm
    {
        private GUID _entityId = null;
        private String _name = null;


        public GUID getEntityId()
        {
            return _entityId;
        }


        public void setEntityId(GUID entityId)
        {
            _entityId = entityId;
        }


        public String getName()
        {
            return _name;
        }


        public void setName(String name)
        {
            _name = name;
        }
    }

    public static class IssueAttachmentForm extends _AttachmentForm
    {
        private Integer _issueId;

        public Integer getIssueId()
        {
            return _issueId;
        }

        public void setIssueId(Integer issueId)
        {
            _issueId = issueId;
        }
    }

    public static ActionURL getDownloadURL(Issue issue, Comment comment, Attachment attachment)
    {
        return new ActionURL(DownloadAction.class, issue.lookupContainer())
            .addParameter("issueId", issue.getIssueId())
            .addParameter("entityId", comment.getEntityId())
            .addParameter("name", attachment.getName());
    }

    @RequiresPermission(ReadPermission.class)
    public class DownloadAction extends BaseDownloadAction<IssueAttachmentForm>
    {
        @Nullable
        @Override
        public Pair<AttachmentParent, String> getAttachment(IssueAttachmentForm form)
        {
            if (form.getIssueId() == null || form.getEntityId() == null)
                throw new NotFoundException();

            Container c = getContainer();

            SimpleFilter filter = new SimpleFilter();
            filter.addCondition(FieldKey.fromParts("IssueId"), form.getIssueId());
            filter.addCondition(FieldKey.fromParts("EntityId"), form.getEntityId());
            filter.addCondition(FieldKey.fromString("IssueId/Container"), c);

            TableInfo table = IssuesSchema.getInstance().getTableInfoComments();
            TableSelector ts = new TableSelector(table, table.getColumns("EntityId"), filter, null);

            // Verifies that IssueId, EntityId, and Container are all correct
            final Comment comment = ts.getObject(Comment.class);
            if (comment == null)
                throw new NotFoundException("Issue comment not found");

            // I don't see a good way to select the Container column (which is in the Issues table, not Comments) above,
            // so push it in here. The Comment select already verified that c is the correct container.
            comment.setContainerId(c.getId());

            return new Pair<>(new CommentAttachmentParent(comment), form.getName());
        }
    }


    @RequiresPermission(ReadPermission.class)
    public class UpdateAction extends AbstractIssueAction
    {
        public ModelAndView getView(IssuesController.IssuesForm form, boolean reshow, BindException errors) throws Exception
        {
            int issueId = form.getIssueId();
            _issue = reshow ? form.getBean() : getIssue(issueId, true);
            if (_issue == null)
            {
                throw new NotFoundException();
            }

            Issue prevIssue = _issue.clone();
            User user = getUser();
            requiresUpdatePermission(user, _issue);

            _issue.beforeUpdate(getContainer());
            beforeReshow(reshow, form, _issue, getIssueListDef());

            IssuePage page = new IssuePage(getContainer(), user);
            page.setAction(UpdateAction.class);
            page.setMode(DataRegion.MODE_UPDATE);
            page.setIssue(_issue);
            page.setPrevIssue(prevIssue);
            page.setCustomColumnConfiguration(getColumnConfiguration());
            page.setBody(form.getComment());
            page.setReturnURL(form.getReturnActionURL());
            page.setVisibleFields(getVisibleFields(page.getAction(), getColumnConfiguration()));
            page.setReadOnlyFields(getReadOnlyFields(page.getAction()));
            page.setRequiredFields(IssueManager.getRequiredIssueFields(getContainer()));
            page.setErrors(errors);
            page.setIssueListDef(getIssueListDef());
            page.setDirty(form.isDirty());

            return new JspView<>("/org/labkey/issue/view/updateView.jsp", page);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return new DetailsAction(_issue, getViewContext()).appendNavTrail(root)
                    .addChild("Update " + getSingularEntityName() + ": " + StringUtils.trimToEmpty(_issue.getTitle()));
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ResolveAction extends AbstractIssueAction
    {
        @Override
        public ModelAndView getView(IssuesController.IssuesForm form, boolean reshow, BindException errors) throws Exception
        {
            int issueId = form.getIssueId();
            _issue = reshow ? form.getBean() : getIssue(issueId, true);
            if (null == _issue)
            {
                throw new NotFoundException();
            }

            Issue prevIssue = _issue.clone();
            User user = getUser();
            requiresUpdatePermission(user, _issue);

            _issue.beforeResolve(getContainer(), user);

            if (_issue.getResolution() == null || _issue.getResolution().isEmpty())
            {
                Map<ColumnTypeEnum, String> defaults = IssueManager.getAllDefaults(getContainer());

                String resolution = defaults.get(ColumnTypeEnum.RESOLUTION);

                if (resolution != null && !resolution.isEmpty() && form.get("resolution") == null)
                {
                    _issue.setResolution(resolution);
                }
                else if (form.get("resolution") != null)
                {
                    _issue.setResolution((String) form.get("resolution"));
                }
            }
            beforeReshow(reshow, form, _issue, getIssueListDef());

            IssuePage page = new IssuePage(getContainer(), user);
            page.setAction(ResolveAction.class);
            page.setMode(DataRegion.MODE_UPDATE);
            page.setIssue(_issue);
            page.setPrevIssue(prevIssue);
            page.setCustomColumnConfiguration(getColumnConfiguration());
            page.setBody(form.getComment());
            page.setVisibleFields(getVisibleFields(page.getAction(), getColumnConfiguration()));
            page.setReadOnlyFields(getReadOnlyFields(page.getAction()));
            page.setRequiredFields(IssueManager.getRequiredIssueFields(getContainer()));
            page.setErrors(errors);
            page.setIssueListDef(getIssueListDef());
            page.setDirty(form.isDirty());

            return new JspView<>("/org/labkey/issue/view/updateView.jsp", page);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            IssueManager.EntryTypeNames names = getEntryTypeNames();
            return (new DetailsAction(_issue, getViewContext()).appendNavTrail(root)).addChild("Resolve " + names.singularName);
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class CloseAction extends AbstractIssueAction
    {
        @Override
        public ModelAndView getView(IssuesController.IssuesForm form, boolean reshow, BindException errors) throws Exception
        {
            int issueId = form.getIssueId();
            _issue = reshow ? form.getBean() : getIssue(issueId, true);
            if (null == _issue)
            {
                throw new NotFoundException();
            }

            Issue prevIssue = _issue.clone();
            User user = getUser();
            requiresUpdatePermission(user, _issue);

            _issue.close(user);
            beforeReshow(reshow, form, _issue, getIssueListDef());

            IssuePage page = new IssuePage(getContainer(), user);
            page.setAction(CloseAction.class);
            page.setMode(DataRegion.MODE_UPDATE);
            page.setIssue(_issue);
            page.setPrevIssue(prevIssue);
            page.setCustomColumnConfiguration(getColumnConfiguration());
            page.setBody(form.getComment());
            page.setVisibleFields(getVisibleFields(page.getAction(), getColumnConfiguration()));
            page.setReadOnlyFields(getReadOnlyFields(page.getAction()));
            page.setRequiredFields(IssueManager.getRequiredIssueFields(getContainer()));
            page.setErrors(errors);
            page.setIssueListDef(getIssueListDef());
            page.setDirty(form.isDirty());

            return new JspView<>("/org/labkey/issue/view/updateView.jsp", page);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            IssueManager.EntryTypeNames names = getEntryTypeNames();
            return (new DetailsAction(_issue, getViewContext()).appendNavTrail(root)).addChild("Close " + names.singularName);
        }
    }


    @RequiresPermission(ReadPermission.class)
    public class ReopenAction extends AbstractIssueAction
    {
        @Override
        public ModelAndView getView(IssuesController.IssuesForm form, boolean reshow, BindException errors) throws Exception
        {
            int issueId = form.getIssueId();
            _issue = reshow ? form.getBean() : getIssue(issueId, true);
            if (_issue == null)
            {
                throw new NotFoundException();
            }

            Issue prevIssue = _issue.clone();

            User user = getUser();
            requiresUpdatePermission(user, _issue);

            _issue.beforeReOpen(getContainer(), true);
            _issue.open(getContainer(), user);
            beforeReshow(reshow, form, _issue, getIssueListDef());

            IssuePage page = new IssuePage(getContainer(), user);
            page.setAction(ReopenAction.class);
            page.setMode(DataRegion.MODE_UPDATE);
            page.setIssue(_issue);
            page.setPrevIssue(prevIssue);
            page.setCustomColumnConfiguration(getColumnConfiguration());
            page.setBody(form.getComment());
            page.setVisibleFields(getVisibleFields(page.getAction(), getColumnConfiguration()));
            page.setReadOnlyFields(getReadOnlyFields(page.getAction()));
            page.setRequiredFields(IssueManager.getRequiredIssueFields(getContainer()));
            page.setErrors(errors);
            page.setIssueListDef(getIssueListDef());
            page.setDirty(form.isDirty());

            return new JspView<>("/org/labkey/issue/view/updateView.jsp", page);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            IssueManager.EntryTypeNames names = getEntryTypeNames();
            return (new DetailsAction(_issue, getViewContext()).appendNavTrail(root)).addChild("Reopen " + names.singularName);
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class EmailPrefsAction extends FormViewAction<EmailPrefsForm>
    {
        String _message = null;

        public ModelAndView getView(EmailPrefsForm form, boolean reshow, BindException errors) throws Exception
        {
            if (getUser().isGuest())
            {
                throw new UnauthorizedException();
            }

            form.setSavedPrefs(IssueManager.getUserEmailPreferences(getContainer(), getUser().getUserId()));
            form.setIssueId(form.getIssueId() == null ? 0 : form.getIssueId().intValue());
            form.setMessage(_message);
            return new JspView<>("/org/labkey/issue/view/emailPreferences.jsp", form, errors);
        }

        public boolean handlePost(EmailPrefsForm form, BindException errors)
        {
            int emailPref = 0;
            for (int pref : form.getEmailPreference())
            {
                emailPref |= pref;
            }
            IssueManager.setUserEmailPreferences(getContainer(), getUser().getUserId(),
                    emailPref, getUser().getUserId());
            _message = "Settings updated successfully";
            return true;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return (new ListAction(getViewContext())).appendNavTrail(root).addChild("Email preferences");
        }


        public void validateCommand(EmailPrefsForm emailPrefsForm, Errors errors)
        {
        }

        public ActionURL getSuccessURL(EmailPrefsForm emailPrefsForm)
        {
            return null;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class GetMoveDestinationAction extends ApiAction<IssuesController.IssuesForm>
    {
        @Override
        public ApiResponse execute(IssuesController.IssuesForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            Collection<Map<String, String>> containers = new LinkedList<>();

            for (Container c : IssueManager.getMoveDestinationContainers(getContainer(), getUser(), form.getIssueDefName()))
            {
                containers.add(PageFlowUtil.map(
                        "containerId", c.getId(),
                        "containerPath", c.getPath()));
            }
            response.put("containers", containers);

            return response;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class GetContainersAction extends ApiAction
    {
        @Override
        public ApiResponse execute(Object object, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            Collection<Map<String, Object>> responseContainers = new LinkedList<>();
            Container root = ContainerManager.getRoot();
            List<Container> allContainers = ContainerManager.getAllChildren(root, getUser(), AdminPermission.class, false);

            // remove current container
            allContainers.remove(getContainer());
            allContainers.remove(root);

            for (Container container : allContainers)
            {
                // remove containers that start with underscore
                if (container.getName().startsWith("_")) continue;

                if(includeContainer(container))
                {
                    Map<String, Object> map = new HashMap<>();
                    map.put("containerId", container.getId());
                    map.put("containerPath", container.getPath());
                    responseContainers.add(map);
                }
            }

            response.put("containers", responseContainers);

            return response;
        }
        protected boolean includeContainer(Container c)
        {
           return true;
        }
    }

    public static class MoveIssueForm extends ReturnUrlForm
    {
        private String _containerId = null;
        private Integer[] _issueIds = null;

        public String getContainerId()
        {
            return _containerId;
        }

        public void setContainerId(String containerId)
        {
            _containerId = containerId;
        }

        public Integer[] getIssueIds()
        {
            return _issueIds;
        }

        public void setIssueIds(Integer[] issueIds)
        {
            _issueIds = issueIds;
        }

    }

    // All three impersonate API actions have the same form
    @RequiresPermission(AdminPermission.class) @CSRF
    public class MoveAction extends MutatingApiAction<MoveIssueForm>
    {

        @Override
        public ApiResponse execute(MoveIssueForm form, BindException errors) throws Exception
        {
            try
            {
                IssueManager.moveIssues(getUser(), Arrays.asList(form.getIssueIds()), ContainerManager.getForId(form.getContainerId()));
            }
            catch (IOException x)
            {
                // TODO: do we want to do anything with the message here?
                return new ApiSimpleResponse("success", false);
            }
            return new ApiSimpleResponse("success", true);
        }
    }

    public static final String REQUIRED_FIELDS_COLUMNS = "title,assignedto,type,area,priority,milestone,notifylist";
    public static final String DEFAULT_REQUIRED_FIELDS = "title;assignedto";

    @RequiresPermission(AdminPermission.class)
    public class AdminAction extends SimpleViewAction<AdminForm>
    {
        @Override
        public ModelAndView getView(IssuesController.AdminForm adminForm, BindException errors) throws Exception
        {
            String issueDefName = getViewContext().getActionURL().getParameter(IssuesListView.ISSUE_LIST_DEF_NAME);
            IssueListDef issueListDef = IssueManager.getIssueListDef(getContainer(), issueDefName);
            if (issueListDef == null)
            {
                return new HtmlView(getUndefinedIssueListMessage(getViewContext(), issueDefName));
            }
            Domain domain = issueListDef.getDomain(getUser());

            Map<String, String> props = new HashMap<>();
            props.put("typeURI", domain.getTypeURI());
            props.put("defName", issueDefName);
            props.put("issueListUrl", new ActionURL(ListAction.class, getContainer()).addParameter(IssuesListView.ISSUE_LIST_DEF_NAME, issueDefName).getLocalURIString());
            props.put("customizeEmailUrl", PageFlowUtil.urlProvider(AdminUrls.class).getCustomizeEmailURL(getContainer(), IssueUpdateEmailTemplate.class, getViewContext().getActionURL()).getLocalURIString());
            props.put("instructions", domain.getDomainKind().getDomainEditorInstructions());
            props.put("canEditDomain", String.valueOf(domain.getDomainKind().canEditDefinition(getUser(), domain)));

            return new GWTView("org.labkey.issues.Designer", props);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            String issueDefName = getViewContext().getActionURL().getParameter(IssuesListView.ISSUE_LIST_DEF_NAME);
            IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(getContainer(), issueDefName != null ? issueDefName : IssueListDef.DEFAULT_ISSUE_LIST_NAME);
            return (new ListAction(getViewContext())).appendNavTrail(root).addChild(names.pluralName + " Admin Page", new ActionURL(AdminAction.class, getContainer()));
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class RssAction extends SimpleViewAction
    {
        @Override
        public void checkPermissions() throws UnauthorizedException
        {
            setUnauthorizedType(UnauthorizedException.Type.sendBasicAuth);
            super.checkPermissions();
        }

        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            getPageConfig().setTemplate(PageConfig.Template.None);
            DataRegion r = new DataRegion();
            TableInfo tinfo = IssuesSchema.getInstance().getTableInfoIssues();
            List<ColumnInfo> cols = tinfo.getColumns("IssueId,Created,CreatedBy,Area,Type,Title,AssignedTo,Priority,Status,Milestone");
            r.addColumns(cols);

            try (ResultSet rs = r.getResultSet(new RenderContext(getViewContext())))
            {
                ObjectFactory f = ObjectFactory.Registry.getFactory(Issue.class);
                Issue[] issues = (Issue[]) f.handleArray(rs);

                ActionURL url = getDetailsURL(getContainer(), 1, isPrint());
                String filteredURLString = PageFlowUtil.filter(url);
                String detailsURLString = filteredURLString.substring(0, filteredURLString.length() - 1);

                WebPartView v = new JspView<>("/org/labkey/issue/view/rss.jsp", new RssBean(issues, detailsURLString));
                v.setFrame(WebPartView.FrameType.NOT_HTML);

                return v;
            }
            catch (SQLException x)
            {
                throw new ServletException(x);
            }
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }

        private ActionURL getUrl()
        {
            return issueURL(RssAction.class);
        }
    }


    public static class RssBean
    {
        public Issue[] issues;
        public String filteredURLString;

        private RssBean(Issue[] issues, String filteredURLString)
        {
            this.issues = issues;
            this.filteredURLString = filteredURLString;
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class PurgeAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            if (!getUser().hasRootAdminPermission())   // GLOBAL
            {
                throw new UnauthorizedException();
            }
            String message = IssueManager.purge();
            return new HtmlView(message);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermission(ReadPermission.class)
    public class JumpToIssueAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            String issueId = (String)getProperty("issueId");
            if (issueId != null)
            {
                issueId = issueId.trim();
                try
                {
                    int id = Integer.parseInt(issueId);
                    Issue issue = getIssue(id, true);
                    if (issue != null)
                    {
                        ActionURL url = getDetailsURL(getContainer(), issue.getIssueId(), false);
                        return HttpView.redirect(url);
                    }
                }
                catch (NumberFormatException e)
                {
                    // fall through
                }

                //Search for the query term instead
                return HttpView.redirect(PageFlowUtil.urlProvider(SearchUrls.class).getSearchURL(getContainer(), issueId, IssueSearchResultTemplate.NAME));
            }
            ActionURL url = getViewContext().cloneActionURL();
            url.deleteParameters();
            url.addParameter("error", "Invalid issue ID or search term.");
            url.setAction(ListAction.class);
            url.addParameter(DataRegion.LAST_FILTER_PARAM, "true");
            return HttpView.redirect(url);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    public static class IssueSearchResultTemplate implements SearchResultTemplate
    {
        public static final String NAME = "issue";

        @Override
        public String getName()
        {
            return NAME;
        }

        @Override
        public String getCategories()
        {
            return "issue";
        }

        @Override
        public SearchScope getSearchScope()
        {
            return SearchScope.FolderAndSubfolders;
        }

        @NotNull
        @Override
        public String getResultNameSingular()
        {
            return "issue";
        }

        @NotNull
        @Override
        public String getResultNamePlural()
        {
            return "issues";
        }

        @Override
        public boolean includeNavigationLinks()
        {
            return false;
        }

        @Override
        public boolean includeAdvanceUI()
        {
            return false;
        }

        @Override
        public String getExtraHtml(ViewContext ctx)
        {
            String q = ctx.getActionURL().getParameter("q");

            if (StringUtils.isNotBlank(q))
            {
                String status = ctx.getActionURL().getParameter("status");
                ActionURL statusResearchURL = ctx.cloneActionURL().deleteParameter("status");
                statusResearchURL.replaceParameter("_dc", String.valueOf((int)Math.round(1000 * Math.random())));

                StringBuilder html = new StringBuilder("<table width=100% cellpadding=\"0\" cellspacing=\"0\"><tr>\n");
                html.append("<td class=\"labkey-search-filter\">&nbsp;");

                appendStatus(html, null, status, "All", false, statusResearchURL);
                appendStatus(html, "Open", status, "Open", true, statusResearchURL);
                appendStatus(html, "Resolved", status, "Resolved", true, statusResearchURL);
                appendStatus(html, "Closed", status, "Closed", true, statusResearchURL);

                html.append("</td></tr></table>");
                return html.toString();
            }
            else
            {
                return null;
            }
        }

        @Nullable
        @Override
        public String getHiddenInputsHtml(ViewContext ctx)
        {
            String status = ctx.getActionURL().getParameter("status");
            if (status != null)
            {
                return "<input type='hidden' id='search-type' name='status' value='" + PageFlowUtil.filter(status) + "'>";
            }

            return null;
        }

        public String reviseQuery(ViewContext ctx, String q)
        {
            String status = ctx.getActionURL().getParameter("status");

            if (null != status)
                return "+(" + q + ") +status:" + status;
            else
                return q;
        }

        private void appendStatus(StringBuilder sb, @Nullable String status, @Nullable String currentStatus, @NotNull String label, boolean addParam, ActionURL statusResearchURL)
        {
            sb.append("<span>");

            if (!Objects.equals(status, currentStatus))
            {
                sb.append("<a href=\"");

                if (addParam)
                    statusResearchURL = statusResearchURL.clone().addParameter("status", status);

                sb.append(PageFlowUtil.filter(statusResearchURL));
                sb.append("\">");
                sb.append(label);
                sb.append("</a>");
            }
            else
            {
                sb.append(label);
            }

            sb.append("</span>&nbsp;");
        }

        @Override
        public NavTree appendNavTrail(NavTree root, ViewContext ctx, @NotNull SearchScope scope, @Nullable String category)
        {
            String issueListDefName = IssueManager.getDefaultIssueListDefName(ctx.getContainer());
            String status = ctx.getActionURL().getParameter("status");
            String pluralName = IssueManager.getEntryTypeNames(ctx.getContainer(), issueListDefName != null ? issueListDefName : IssueListDef.DEFAULT_ISSUE_LIST_NAME).pluralName;
            root.addChild(pluralName + " List", issueURL(ctx.getContainer(), ListAction.class).addParameter(DataRegion.LAST_FILTER_PARAM, "true"));
            root.addChild("Search " + (null != status ? status + " " : "") + pluralName);

            return root;
        }
    }


    // SearchForm and SearchAction are left for backward compatibility (e.g., firefox search plugins)
    public static class SearchForm
    {
        private String _q = null;

        public String getQ()
        {
            return _q;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setQ(String q)
        {
            _q = q;
        }
    }

    @SuppressWarnings({"UnusedDeclaration"})
    @RequiresPermission(ReadPermission.class)
    public class SearchAction extends SimpleRedirectAction<SearchForm>
    {
        @Override
        public URLHelper getRedirectURL(SearchForm form) throws Exception
        {
            return PageFlowUtil.urlProvider(SearchUrls.class).getSearchURL(getContainer(), form.getQ(), IssueSearchResultTemplate.NAME);
        }
    }


    @RequiresPermission(ReadPermission.class)
    public class GetIssueAction extends ApiAction<IssueIdForm>
    {
        @Override
        public ApiResponse execute(IssueIdForm issueIdForm, BindException errors) throws Exception
        {
            User user = getUser();
            Issue issue = getIssue(issueIdForm.getIssueId(), false);

            BeanMap wrapper = new BeanMap(issue);
            JSONObject jsonIssue = new JSONObject(wrapper);
            jsonIssue.remove("lastComment");
            jsonIssue.remove("class");

            for (CustomColumn cc : getCustomColumnConfiguration().getCustomColumns(user))
            {
                jsonIssue.remove(cc.getName());
                jsonIssue.put(cc.getCaption(), wrapper.get(cc.getName()));
            }

            JSONArray comments = new JSONArray();
            jsonIssue.put("comments", comments);
            for (Comment c : issue.getComments())
            {
                JSONObject jsonComment = new JSONObject(new BeanMap(c));
                jsonComment.put("createdByName", c.getCreatedByName(user));
                jsonComment.put("comment", c.getComment());
                comments.put(comments.length(),  jsonComment);
                // ATTACHMENTS
            }
            jsonIssue.put("success", Boolean.TRUE);
            return new ApiSimpleResponse(jsonIssue);
        }
    }

    public static class EmailPrefsForm
    {
        private Integer[] _emailPreference = new Integer[0];
        private Integer _issueId;
        private String issueDefName;
        private int _savedPrefs;
        private String _message;

        public Integer[] getEmailPreference()
        {
            return _emailPreference;
        }

        public void setEmailPreference(Integer[] emailPreference)
        {
            _emailPreference = emailPreference;
        }

        public Integer getIssueId()
        {
            return _issueId;
        }

        public void setIssueId(Integer issueId)
        {
            _issueId = issueId;
        }

        public String getIssueDefName()
        {
            return issueDefName;
        }

        public void setIssueDefName(String issueDefName)
        {
            this.issueDefName = issueDefName;
        }

        public int getSavedPrefs()
        {
            return _savedPrefs;
        }

        public void setSavedPrefs(int savedPrefs)
        {
            _savedPrefs = savedPrefs;
        }

        public String getMessage()
        {
            return _message;
        }

        public void setMessage(String message)
        {
            _message = message;
        }
    }

    public static class AdminForm
    {
        private int type;
        private String keyword;


        public int getType()
        {
            return type;
        }


        public void setType(int type)
        {
            this.type = type;
        }


        public String getKeyword()
        {
            return keyword;
        }


        public void setKeyword(String keyword)
        {
            this.keyword = keyword;
        }
    }


    public static class IssuesForm extends BeanViewForm<Issue>
    {
        public IssuesForm()
        {
            super(Issue.class, IssuesSchema.getInstance().getTableInfoIssues(), extraProps());
            setValidateRequired(false);
        }

        private static Map<String, Class> extraProps()
        {
            Map<String, Class> map = new LinkedHashMap<>();
            map.put("action", String.class);
            map.put("comment", String.class);
            map.put("callbackURL", String.class);
            return map;
        }

        public Class<? extends Controller> getAction()
        {
            String className = _stringValues.get("action");
            if (className == null)
            {
                throw new NotFoundException("No action specified");
            }
            try
            {
                Class result = Class.forName(className);
                if (Controller.class.isAssignableFrom(result))
                {
                    return result;
                }
                throw new NotFoundException("Resolved class but it was not an action: " + className);
            }
            catch (ClassNotFoundException e)
            {
                throw new NotFoundException("Could not find action " + className);
            }
        }

        public String getComment()
        {
            return _stringValues.get("comment");
        }

        public String getNotifyList()
        {
            return _stringValues.get("notifyList");
        }

        // XXX: change return value to typed ReturnURLString
        public String getCallbackURL()
        {
            return _stringValues.get("callbackURL");
        }

        public String getBody()
        {
            return _stringValues.get("body");
        }

        public String getPriority()
        {
            return _stringValues.get("priority");
        }

        public String getIssueDefName()
        {
            return _stringValues.get(IssuesListView.ISSUE_LIST_DEF_NAME);
        }

        public String getIssueDefId()
        {
            return _stringValues.get(IssuesListView.ISSUE_LIST_DEF_ID);
        }

        public void setTable(TableInfo table)
        {
            super.setTable(table);
        }

        /**
         * A bit of a hack but to allow the mothership controller to continue to create issues
         * in the way that it previously did, we need to be able to tell the issues controller
         * to not handle the post, and just get the view.
         */
        public boolean getSkipPost()
        {
            return BooleanUtils.toBoolean(_stringValues.get("skipPost"));
        }

        public ActionURL getForwardURL()
        {
            ActionURL url;
            String callbackURL = getCallbackURL();
            if (callbackURL != null)
            {
                url = new ActionURL(callbackURL).addParameter("issueId", "" + getBean().getIssueId());
                return url;
            }
            else
            {
                return getDetailsURL(getViewContext().getContainer(), getBean().getIssueId(), false);
            }
        }

        public int getIssueId()
        {
            return NumberUtils.toInt(_stringValues.get("issueId"));
        }

        public boolean isDirty()
        {
            return BooleanUtils.toBoolean(_stringValues.get("dirty"));
        }
    }


    public static class SummaryBean
    {
        public boolean hasPermission;
        public Collection<Map<String, Object>> bugs;
        public ActionURL insertURL;
        public String issueDefName;
    }


    protected synchronized void afterAction(Throwable t)
    {
        super.afterAction(t);
    }

    public static class ListForm extends QueryForm
    {
        @NotNull
        @Override
        public String getSchemaName()
        {
            return IssuesQuerySchema.SCHEMA_NAME;
        }

        @Override
        protected UserSchema createSchema()
        {
            return new IssuesQuerySchema(getUser(), getContainer());
        }

    }

    public static class IssueIdForm
    {
        private int issueId = -1;

        public int getIssueId()
        {
            return issueId;
        }

        public void setIssueId(int issueId)
        {
            this.issueId = issueId;
        }
    }
}
