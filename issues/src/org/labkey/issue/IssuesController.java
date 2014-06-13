/*
 * Copyright (c) 2004-2014 Fred Hutchinson Cancer Research Center
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

import org.apache.commons.collections15.BeanMap;
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
import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.LabkeyError;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleRedirectAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.AttachmentParentEntity;
import org.labkey.api.data.BeanViewForm;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.TSVGridWriter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.issues.IssuesUrls;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.search.SearchResultTemplate;
import org.labkey.api.search.SearchScope;
import org.labkey.api.search.SearchUrls;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.Group;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.OwnerRole;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.HString;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.MailHelper;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.ReturnURLString;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.emailTemplate.EmailTemplateService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.AjaxCompletion;
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
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;
import org.labkey.issue.model.Issue;
import org.labkey.issue.model.IssueManager;
import org.labkey.issue.model.IssueManager.CustomColumn;
import org.labkey.issue.model.IssueManager.CustomColumnConfiguration;
import org.labkey.issue.model.IssueManager.EntryTypeNames;
import org.labkey.issue.model.KeywordManager;
import org.labkey.issue.model.KeywordManager.Keyword;
import org.labkey.issue.query.IssuesQuerySchema;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.validation.MapBindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import javax.mail.Address;
import javax.mail.internet.AddressException;
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

public class IssuesController extends SpringActionController
{
    private static final Logger _log = Logger.getLogger(IssuesController.class);
    private static final String helpTopic = "issues";
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(IssuesController.class);
    private static final int MAX_STRING_FIELD_LENGTH = 200;

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
        Issue result = IssueManager.getIssue(redirect ? null : getContainer(), issueId);
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


    @RequiresPermissionClass(ReadPermission.class)
    public class BeginAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
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


    private ResultSet getIssuesResultSet() throws IOException, SQLException, ServletException
    {
        UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), IssuesQuerySchema.SCHEMA_NAME);
        QuerySettings settings = schema.getSettings(getViewContext(), IssuesQuerySchema.TableType.Issues.name());
        settings.setQueryName(IssuesQuerySchema.TableType.Issues.name());

        QueryView queryView = schema.createView(getViewContext(), settings, null);

        return queryView.getResultSet();
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class ListAction extends SimpleViewAction<ListForm>
    {
        public ListAction() {}

        public ListAction(ViewContext ctx)
        {
            setViewContext(ctx);
        }

        public ModelAndView getView(ListForm form, BindException errors) throws Exception
        {
            EntryTypeNames names = IssueManager.getEntryTypeNames(getContainer());

            // convert AssignedTo/Email to AssignedTo/DisplayName: old bookmarks
            // reference Email, which is no longer displayed.
            ActionURL url = getViewContext().cloneActionURL();
            String[] emailFilters = url.getKeysByPrefix(IssuesQuerySchema.TableType.Issues.name() + ".AssignedTo/Email");
            if (emailFilters != null && emailFilters.length > 0)
            {
                for (String emailFilter : emailFilters)
                    url.deleteParameter(emailFilter);
                return HttpView.redirect(url);
            }

            getPageConfig().setRssProperties(new RssAction().getUrl(), names.pluralName.toString());

            return new IssuesListView();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            EntryTypeNames names = IssueManager.getEntryTypeNames(getContainer());
            return root.addChild(names.pluralName.getSource() + " List", getURL());
        }

        public ActionURL getURL()
        {
            return issueURL(ListAction.class).addParameter(DataRegion.LAST_FILTER_PARAM, "true");
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
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
                    writer.setColumnHeaderType(TSVGridWriter.ColumnHeaderType.caption);
                    writer.write(getViewContext().getResponse());
                }
            };
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class DetailsAction extends SimpleViewAction<IssueIdForm>
    {
        Issue _issue = null;

        public DetailsAction()
        {
        }

        public DetailsAction(Issue issue, ViewContext context)
        {
            _issue = issue;
            setViewContext(context);
        }

        public ModelAndView getView(IssueIdForm form, BindException errors) throws Exception
        {
            int issueId = form.getIssueId();
            _issue = getIssue(issueId, true);

            EntryTypeNames names = IssueManager.getEntryTypeNames(getContainer());
            if (null == _issue)
            {
                throw new NotFoundException("Unable to find " + names.singularName.getSource() + " " + form.getIssueId());
            }

            IssuePage page = new IssuePage(getContainer(), getUser());
            page.setPrint(isPrint());
            page.setIssue(_issue);
            page.setCustomColumnConfiguration(getCustomColumnConfiguration());
            //pass user's update perms to jsp page to determine whether to show notify list
            page.setUserHasUpdatePermissions(hasUpdatePermission(getUser(), _issue));
            page.setUserHasAdminPermissions(hasAdminPermission(getUser(), _issue));
            page.setMoveDestinations(null != IssueManager.getMoveDestinationContainers(getContainer()) ? true : false);
            page.setRequiredFields(IssueManager.getRequiredIssueFields(getContainer()));

            return new JspView<>("/org/labkey/issue/detailView.jsp", page);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return new ListAction(getViewContext()).appendNavTrail(root)
                    .addChild(getSingularEntityName().getSource() + " " + _issue.getIssueId() + ": " + _issue.getTitle(), getURL());
        }

        public ActionURL getURL()
        {
            return issueURL(DetailsAction.class).addParameter("issueId", _issue.getIssueId());
        }
    }

    private HString getSingularEntityName()
    {
        return IssueManager.getEntryTypeNames(getContainer()).singularName;
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class DetailsListAction extends SimpleViewAction<ListForm>
    {
        public ModelAndView getView(ListForm listForm, BindException errors) throws Exception
        {
            // convert AssignedTo/Email to AssignedTo/DisplayName: old bookmarks
            // reference Email, which is no longer displayed.
            ActionURL url = getViewContext().cloneActionURL();
            String[] emailFilters = url.getKeysByPrefix(IssuesQuerySchema.TableType.Issues.name() + ".AssignedTo/Email");
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

                try (ResultSet rs = getIssuesResultSet())
                {
                    int issueColumnIndex = rs.findColumn("issueId");

                    while (rs.next())
                    {
                        issueIds.add(rs.getString(issueColumnIndex));
                    }
                }
            }

            IssuePage page = new IssuePage(getContainer(), getUser());
            page.setPrint(isPrint());
            JspView v = new JspView<>(IssuesController.class, "detailList.jsp", page);

            page.setIssueIds(issueIds);
            page.setCustomColumnConfiguration(getCustomColumnConfiguration());
            page.setRequiredFields(IssueManager.getRequiredIssueFields(getContainer()));
            page.setDataRegionSelectionKey(listForm.getQuerySettings().getSelectionKey());

            getPageConfig().setNoIndex(); // We want crawlers to index the single issue detail page, no the multiple page
            getPageConfig().setNoFollow();

            return v;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            EntryTypeNames names = IssueManager.getEntryTypeNames(getContainer());
            return new ListAction(getViewContext()).appendNavTrail(root).addChild(names.singularName.getSource() + " Details");
        }
    }


    @RequiresPermissionClass(InsertPermission.class)
    public class InsertAction extends FormViewAction<IssuesForm>
    {
        private Issue _issue = null;

        public ModelAndView getView(IssuesForm form, boolean reshow, BindException errors) throws Exception
        {
            // if we have errors, then form.getBean() is likely to throw, but try anyway
            if (errors.hasErrors())
            {
                try
                {
                    _issue = reshow ? form.getBean() : new Issue();
                }
                catch (Exception e)
                {
                    _issue = new Issue();
                }
            }
            else
            {
                _issue = reshow ? form.getBean() : new Issue();
            }

            if (_issue.getAssignedTo() != null)
            {
                User user = UserManager.getUser(_issue.getAssignedTo());

                if (user != null)
                {
                    _issue.setAssignedTo(user.getUserId());
                }
            }

            User defaultUser = IssueManager.getDefaultAssignedToUser(getContainer());
            if (defaultUser != null)
                _issue.setAssignedTo(defaultUser.getUserId());

            _issue.open(getContainer(), getUser());
            if (!reshow || form.getSkipPost())
            {
                // Set the defaults if we're not reshowing after an error, or if this is a request to open an issue
                // from a mothership which comes in as a POST and is therefore considered a reshow 
                setNewIssueDefaults(_issue);
            }

            IssuePage page = new IssuePage(getContainer(), getUser());
            JspView v = new JspView<>("/org/labkey/issue/updateView.jsp", page);

            CustomColumnConfiguration ccc = getCustomColumnConfiguration();

            page.setAction(InsertAction.class);
            page.setIssue(_issue);
            page.setPrevIssue(_issue);
            page.setCustomColumnConfiguration(ccc);
            page.setBody(form.getComment() == null ? form.getBody() : form.getComment());
            page.setCallbackURL(form.getCallbackURL());
            page.setEditable(getEditableFields(page.getAction(), ccc));
            page.setRequiredFields(IssueManager.getRequiredIssueFields(getContainer()));
            page.setErrors(errors);

            return v;
        }

        public void validateCommand(IssuesForm form, Errors errors)
        {
            if (!form.getSkipPost())
            {
                validateRequiredFields(form, errors);
                validateNotifyList(form, errors);
                validateAssignedTo(form, errors);
                validateStringFields(form, errors);
            }
        }

        public boolean handlePost(IssuesForm form, BindException errors) throws Exception
        {
            if (form.getSkipPost())
                return false;

            Container c = getContainer();
            User user = getUser();

            _issue = form.getBean();
            _issue.open(c, user);
            validateNotifyList(form, errors);
            // convert from email addresses & display names to userids before we hit the database

            if(_issue.getNotifyList() != null)
                _issue.parseNotifyList(_issue.getNotifyList().toString());


            // return errors from handler
            boolean ret = relatedIssueHandler(_issue, user, errors);
            if (!ret) return false;

            ChangeSummary changeSummary;

            try (DbScope.Transaction transaction = IssuesSchema.getInstance().getSchema().getScope().ensureTransaction())
            {
                // for new issues, the original is always the default.
                Issue orig = new Issue();
                orig.open(getContainer(), getUser());

                changeSummary = createChangeSummary(_issue, orig, null, user, form.getAction(), form.getComment(), getCustomColumnConfiguration(), getUser());
                IssueManager.saveIssue(user, c, _issue);
                AttachmentService.get().addAttachments(changeSummary.getComment(), getAttachmentFileList(), user);

                Collection<Integer> rels = _issue.getRelatedIssues();
                // handle comment changes to related issues
                if (rels != null)
                {
                    for (int curIssueId : rels)
                    {
                        Issue relatedIssue = relatedIssueCommentHandler(_issue.getIssueId(), curIssueId, user, false);
                        IssueManager.saveIssue(user, getContainer(), relatedIssue);
                    }
                }

                transaction.commit();
            }
            catch (Exception x)
            {
                Throwable ex = x.getCause() == null ? x : x.getCause();
                String error = ex.getMessage();
                _log.debug("IssuesController.doInsert", x);
                _issue.open(c, user);

                errors.addError(new LabkeyError(error));
                return false;
            }

            ActionURL url = new DetailsAction(_issue, getViewContext()).getURL();

            final String assignedTo = UserManager.getDisplayName(_issue.getAssignedTo(), user);
            if (assignedTo != null)
                sendUpdateEmail(_issue, null, changeSummary.getTextChanges(), changeSummary.getSummary(), form.getComment(), url, "opened and assigned to " + assignedTo, getAttachmentFileList(), form.getAction());
            else
                sendUpdateEmail(_issue, null, changeSummary.getTextChanges(), changeSummary.getSummary(), form.getComment(), url, "opened", getAttachmentFileList(), form.getAction());

            return true;
        }


        public ActionURL getSuccessURL(IssuesForm issuesForm)
        {
            if (!StringUtils.isEmpty(issuesForm.getCallbackURL()))
            {
                ActionURL url = new ActionURL(issuesForm.getCallbackURL());
                url.addParameter("issueId", _issue.getIssueId());
                url.addParameter("assignedTo", _issue.getAssignedTo());
                return url;
            }

            return new DetailsAction(_issue, getViewContext()).getURL();
        }


        public NavTree appendNavTrail(NavTree root)
        {
            EntryTypeNames names = IssueManager.getEntryTypeNames(getContainer());
            return new ListAction(getViewContext()).appendNavTrail(root).addChild("Insert New " + names.singularName.getSource());
        }
    }


    private Issue setNewIssueDefaults(Issue issue) throws SQLException, ServletException
    {
        Map<ColumnType, String> defaults = IssueManager.getAllDefaults(getContainer());

        ColumnType.AREA.setDefaultValue(issue, defaults);
        ColumnType.TYPE.setDefaultValue(issue, defaults);
        ColumnType.MILESTONE.setDefaultValue(issue, defaults);
        ColumnType.PRIORITY.setDefaultValue(issue, defaults);

        CustomColumnConfiguration config = getCustomColumnConfiguration();

        // For each of the string configurable columns,
        // only set the default if the column is currently configured as a pick list
        for (ColumnType stringColumn : ColumnType.getCustomStringColumns())
        {
            if (config.hasPickList(stringColumn.getColumnName()))
            {
                stringColumn.setDefaultValue(issue, defaults);
            }
        }

        return issue;
    }

    protected boolean relatedIssueHandler(Issue issue, User user, BindException errors)
    {
        String textInput = issue.getRelated();
        ArrayList<Integer> rels = new ArrayList<>();
        if (textInput != null)
        {
            String[] textValues = issue.getRelated().split("\\s*,\\s*");
            int relatedId;
            Issue related;
            // for each issue id we need to validate
            for (String relatedText : textValues)
            {
                relatedId = NumberUtils.toInt(relatedText.trim(), 0);
                if (relatedId == 0)
                {
                    errors.rejectValue("Related", ERROR_MSG, "Invalid issue id in related string.");
                    return false;
                }
                if (issue.getIssueId() == relatedId)
                {
                    errors.rejectValue("Related", ERROR_MSG, "As issue may not be related to itself");
                    return false;
                }

                related = IssueManager.getIssue(null, relatedId);
                if (related == null)
                {
                    errors.rejectValue("Related", ERROR_MSG, "Related issue '" + relatedId + "' not found");
                    return false;
                }
                if (!related.lookupContainer().hasPermission(user, ReadPermission.class))
                {
                    errors.rejectValue("Related", ERROR_MSG, "User does not have Read Permission for related issue'" + relatedId + "'");
                    return false;
                }
                if (rels.contains(relatedId))
                {
                    errors.rejectValue("Related", ERROR_MSG, "Related issues cannot contain duplicates");
                    return false;
                }
                rels.add(relatedId);
            }
        }
        // this sets the collection of interger ids for all related issues
        issue.setRelatedIssues(rels);
        return true;
    }
    
    protected Issue relatedIssueCommentHandler(int issueId, int relatedIssueId, User user, boolean drop)
    {
        StringBuilder sb = new StringBuilder();
        Issue relatedIssue = IssueManager.getIssue(null, relatedIssueId);
        ArrayList<Integer> prevRelated = relatedIssue.getRelatedIssues();
        ArrayList<Integer> newRelated = new ArrayList<>();
        newRelated.addAll(prevRelated);

        if (drop)
            newRelated.remove(new Integer(issueId));
        else
            newRelated.add(issueId);

        // make sure sorted order
        Collections.sort(newRelated);

        sb.append("<div class=\"wiki\"><table class=issues-Changes>");
        sb.append(String.format("<tr><td>Related</td><td>%s</td><td>&raquo;</td><td>%s</td></tr>", StringUtils.join(prevRelated, ", "), StringUtils.join(newRelated, ", ")));
        sb.append("<table></div>");

        relatedIssue.addComment(user, sb.toString());
        relatedIssue.setRelatedIssues(newRelated);
        return relatedIssue;
    }

    protected abstract class IssueUpdateAction extends FormViewAction<IssuesForm>
    {
        // NOTE: aaron this is used in the InsertAction but not the update (consider refactor)
        Issue _issue = null;

        public boolean handlePost(IssuesForm form, BindException errors) throws Exception
        {
            Container c = getContainer();
            User user = getUser();

            Issue issue = form.getBean();
            Issue prevIssue = (Issue)form.getOldValues();
            requiresUpdatePermission(user, issue);
            ActionURL detailsUrl;

            // clear resolution, resolvedBy, and duplicate fields
            if (ReopenAction.class.equals(form.getAction()))
                issue.beforeReOpen(getContainer());

            Issue duplicateOf = null;
            if (ResolveAction.class.equals(form.getAction()) &&
                    issue.getResolution().equals("Duplicate") &&
                    issue.getDuplicate() != null &&
                    !issue.getDuplicate().equals(prevIssue.getDuplicate()))
            {
                if (issue.getDuplicate() == issue.getIssueId())
                {
                    errors.rejectValue("Duplicate", ERROR_MSG, "An issue may not be a duplicate of itself");
                    return false;
                }
                duplicateOf = IssueManager.getIssue(null, issue.getDuplicate().intValue());
                if (duplicateOf == null)
                {
                    errors.rejectValue("Duplicate", ERROR_MSG, "Duplicate issue '" + issue.getDuplicate().intValue() + "' not found");
                    return false;
                }
                if (!duplicateOf.lookupContainer().hasPermission(user, ReadPermission.class))
                {
                    errors.rejectValue("Duplicate", ERROR_MSG, "User does not have Read permission for duplicate issue '" + issue.getDuplicate().intValue() + "'");
                    return false;
                }
            }

            // get previous related issue ids before updateing
            ArrayList<Integer> prevRelatedIds = issue.getRelatedIssues();

            boolean ret = relatedIssueHandler(issue, user, errors);
            if (!ret) return false;

            ChangeSummary changeSummary;
            try (DbScope.Transaction transaction = IssuesSchema.getInstance().getSchema().getScope().ensureTransaction())
            {
                detailsUrl = new DetailsAction(issue, getViewContext()).getURL();

                if (ResolveAction.class.equals(form.getAction()))
                    issue.resolve(user);
                else if (InsertAction.class.equals(form.getAction()) || ReopenAction.class.equals(form.getAction()))
                    issue.open(c, user);
                else if (CloseAction.class.equals(form.getAction()))
                    issue.close(user);
                else
                    issue.change(user);

                // convert from email addresses & display names to userids before we hit the database
                issue.parseNotifyList(issue.getNotifyList());

                changeSummary = createChangeSummary(issue, prevIssue, duplicateOf, user, form.getAction(), form.getComment(), getCustomColumnConfiguration(), getUser());
                IssueManager.saveIssue(user, c, issue);
                AttachmentService.get().addAttachments(changeSummary.getComment(), getAttachmentFileList(), user);

                if (duplicateOf != null)
                {
                    StringBuilder sb = new StringBuilder();
                    sb.append("<em>Issue ").append(issue.getIssueId()).append(" marked as duplicate of this issue.</em>");
                    Issue.Comment dupComment = duplicateOf.addComment(user, sb.toString());
                    IssueManager.saveIssue(user, c, duplicateOf);
                }

                ArrayList<Integer> newRelatedIds = issue.getRelatedIssues();

                // this list represents all the ids which will need related handling for a creating a relatedIssue entry
                Collection<Integer> newIssues = new ArrayList<>();
                newIssues.addAll(newRelatedIds);
                newIssues.removeAll(prevRelatedIds);
                for (int curIssueId : newIssues)
                {
                    Issue relatedIssue = relatedIssueCommentHandler(issue.getIssueId(), curIssueId, user, false);
                    IssueManager.saveIssue(user, getContainer(), relatedIssue);
                }

                // this list represents all the ids which will need related handling for a droping a relatedIssue entry
                prevRelatedIds.removeAll(newRelatedIds);
                for (int curIssueId : prevRelatedIds)
                {
                    Issue relatedIssue = relatedIssueCommentHandler(issue.getIssueId(), curIssueId, user, true);
                    IssueManager.saveIssue(user, getContainer(), relatedIssue);
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
            String change = ReopenAction.class.equals(form.getAction()) ? "reopened" : getActionName(form.getAction()) + "d";
            if ("resolved".equalsIgnoreCase(change) && issue.getResolution() != null)
            {
                change += " as " + issue.getResolution(); // Issue 12273
            }
            sendUpdateEmail(issue, prevIssue, changeSummary.getTextChanges(), changeSummary.getSummary(), form.getComment(), detailsUrl, change, getAttachmentFileList(), form.getAction());
            return true;
        }

        public void validateCommand(IssuesForm form, Errors errors)
        {
            validateRequiredFields(form, errors);
            validateNotifyList(form, errors);
            validateAssignedTo(form, errors);
            validateStringFields(form, errors);
        }

        public ActionURL getSuccessURL(IssuesForm form)
        {
            if(getIssue(form.getIssueId(), false).getStatus().equals("closed"))
                return issueURL(ListAction.class).addParameter(DataRegion.LAST_FILTER_PARAM, "true");
            
            return form.getForwardURL();
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


    @RequiresPermissionClass(ReadPermission.class)
    public class DownloadAction extends SimpleViewAction<_AttachmentForm>
    {
        public ModelAndView getView(final _AttachmentForm form, BindException errors) throws Exception
        {
            if (form.getEntityId() != null && form.getName() != null)
            {
                getPageConfig().setTemplate(PageConfig.Template.None);
                final AttachmentParent parent = new IssueAttachmentParent(getContainer(), form.getEntityId());

                return new HttpView()
                {
                    protected void renderInternal(Object model, HttpServletRequest request, HttpServletResponse response) throws Exception
                    {
                        AttachmentService.get().download(response, parent, form.getName());
                    }
                };
            }
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    public class IssueAttachmentParent extends AttachmentParentEntity
    {
        public IssueAttachmentParent(Container c, GUID entityId)
        {
            setContainer(c.getId());
            setEntityId(null==entityId?null:entityId.toString());
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class UpdateAction extends IssueUpdateAction
    {
        public ModelAndView getView(IssuesForm form, boolean reshow, BindException errors) throws Exception
        {
            int issueId = form.getIssueId();
            _issue = getIssue(issueId, true);
            if (_issue == null)
            {
                throw new NotFoundException();
            }

            Issue prevIssue = (Issue)_issue.clone();
            User user = getUser();
            requiresUpdatePermission(user, _issue);

            _issue.beforeUpdate(getContainer());

            IssuePage page = new IssuePage(getContainer(), user);
            JspView v = new JspView<>("/org/labkey/issue/updateView.jsp", page);

            CustomColumnConfiguration ccc = getCustomColumnConfiguration();

            page.setAction(UpdateAction.class);
            page.setIssue(_issue);
            page.setPrevIssue(prevIssue);
            page.setCustomColumnConfiguration(ccc);
            page.setBody(form.getComment());
            page.setEditable(getEditableFields(page.getAction(), ccc));
            page.setRequiredFields(IssueManager.getRequiredIssueFields(getContainer()));
            page.setErrors(errors);

            return v;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return new DetailsAction(_issue, getViewContext()).appendNavTrail(root)
                    .addChild("Update " + getSingularEntityName().getSource() + ": " + _issue.getTitle());
        }
    }


    private Set<String> getEditableFields(Class<? extends Controller> action, CustomColumnConfiguration ccc)
    {
        final Set<String> editable = new HashSet<>(20);

        editable.add("title");
        editable.add("assignedTo");
        editable.add("type");
        editable.add("area");
        editable.add("priority");
        editable.add("milestone");
        editable.add("comments");
        editable.add("attachments");

        // Add all the enabled custom fields
        for (CustomColumn cc : ccc.getCustomColumns())
        {
            ColumnType type = ColumnType.forName(cc.getName());

            if (null != type && type.isCustom())
                editable.add(cc.getName());
        }

        editable.add("notifyList");

        if (ResolveAction.class.equals(action))
        {
            editable.add("resolution");
            editable.add("duplicate");
        }

        editable.add("related");

        return editable;
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class ResolveAction extends IssueUpdateAction
    {
        public ModelAndView getView(IssuesForm form, boolean reshow, BindException errors) throws Exception
        {
            int issueId = form.getIssueId();
            _issue = getIssue(issueId, true);
            if (null == _issue)
            {
                throw new NotFoundException();
            }

            Issue prevIssue = (Issue)_issue.clone();
            User user = getUser();
            requiresUpdatePermission(user, _issue);

            _issue.beforeResolve(getContainer(), user);

            if (_issue.getResolution() == null || _issue.getResolution().isEmpty())
            {
                Map<ColumnType, String> defaults = IssueManager.getAllDefaults(getContainer());

                String resolution = defaults.get(ColumnType.RESOLUTION);

                if (resolution != null && !resolution.isEmpty() && form.get("resolution") == null)
                {
                    _issue.setResolution(resolution);
                }
                else if (form.get("resolution") != null)
                {
                    _issue.setResolution((String) form.get("resolution"));
                }
            }

            IssuePage page = new IssuePage(getContainer(), user);
            JspView v = new JspView<>("/org/labkey/issue/updateView.jsp", page);

            CustomColumnConfiguration ccc = getCustomColumnConfiguration();

            page.setAction(ResolveAction.class);
            page.setIssue(_issue);
            page.setPrevIssue(prevIssue);
            page.setCustomColumnConfiguration(ccc);
            page.setBody(form.getComment());
            page.setEditable(getEditableFields(page.getAction(), ccc));
            page.setRequiredFields(IssueManager.getRequiredIssueFields(getContainer()));
            page.setErrors(errors);

            return v;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            EntryTypeNames names = IssueManager.getEntryTypeNames(getContainer());
            return (new DetailsAction(_issue, getViewContext()).appendNavTrail(root)).addChild("Resolve " + names.singularName.getSource());
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class CloseAction extends IssueUpdateAction
    {
        public ModelAndView getView(IssuesForm form, boolean reshow, BindException errors) throws Exception
        {
            int issueId = form.getIssueId();
            _issue = getIssue(issueId, true);
            if (null == _issue)
            {
                throw new NotFoundException();
            }

            Issue prevIssue = (Issue)_issue.clone();
            User user = getUser();
            requiresUpdatePermission(user, _issue);

            _issue.close(user);

            IssuePage page = new IssuePage(getContainer(), user);
            JspView v = new JspView<>("/org/labkey/issue/updateView.jsp", page);

            CustomColumnConfiguration ccc = getCustomColumnConfiguration();

            page.setAction(CloseAction.class);
            page.setIssue(_issue);
            page.setPrevIssue(prevIssue);
            page.setCustomColumnConfiguration(ccc);
            page.setBody(form.getComment());
            page.setEditable(getEditableFields(page.getAction(), ccc));
            page.setRequiredFields(IssueManager.getRequiredIssueFields(getContainer()));
            page.setErrors(errors);

            return v;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            EntryTypeNames names = IssueManager.getEntryTypeNames(getContainer());
            return (new DetailsAction(_issue, getViewContext()).appendNavTrail(root)).addChild("Close " + names.singularName.getSource());
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class ReopenAction extends IssueUpdateAction
    {
        public ModelAndView getView(IssuesForm form, boolean reshow, BindException errors) throws Exception
        {
            int issueId = form.getIssueId();
            _issue = getIssue(issueId, true);
            if (_issue == null)
            {
                throw new NotFoundException();
            }

            Issue prevIssue = (Issue)_issue.clone();

            User user = getUser();
            requiresUpdatePermission(user, _issue);

            _issue.beforeReOpen(getContainer(), true);
            _issue.open(getContainer(), user);

            IssuePage page = new IssuePage(getContainer(), user);
            JspView v = new JspView<>("/org/labkey/issue/updateView.jsp", page);

            CustomColumnConfiguration ccc = getCustomColumnConfiguration();

            page.setAction(ReopenAction.class);
            page.setIssue(_issue);
            page.setPrevIssue(prevIssue);
            page.setCustomColumnConfiguration(ccc);
            page.setBody(form.getComment());
            page.setEditable(getEditableFields(page.getAction(), ccc));
            page.setRequiredFields(IssueManager.getRequiredIssueFields(getContainer()));
            page.setErrors(errors);

            return v;
            //return _renderInTemplate(v, "(open) " + issue.getTitle(), null);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            EntryTypeNames names = IssueManager.getEntryTypeNames(getContainer());
            return (new DetailsAction(_issue, getViewContext()).appendNavTrail(root)).addChild("Reopen " + names.singularName.getSource());
        }
    }

    private void validateStringFields(IssuesForm form, Errors errors)
    {
        final Map<String, String> fields = form.getStrings();
        final CustomColumnConfiguration ccc = getCustomColumnConfiguration();
        String lengthError = " cannot be longer than " + MAX_STRING_FIELD_LENGTH + " characters.";

        for (int i = 1; i <= 5; i++)
        {
            String name = "string" + i;

            if (fields.containsKey(name) && fields.get(name).length() > MAX_STRING_FIELD_LENGTH)
                errors.reject(ERROR_MSG, ccc.getCaption(name) + lengthError);
        }
    }
    
    private void validateRequiredFields(IssuesForm form, Errors errors)
    {
        String requiredFields = IssueManager.getRequiredIssueFields(getContainer());
        final Map<String, String> newFields = form.getStrings();
        if (!"0".equals(newFields.get("issueId")) && requiredFields.indexOf("comment") != -1)
        {
            // When updating an existing issue (which will have a unique IssueId), never require a comment
            requiredFields = requiredFields.replace("comment", "");
        }
        if (requiredFields.isEmpty())
            return;

        MapBindingResult requiredErrors = new MapBindingResult(newFields, errors.getObjectName());
        if (newFields.containsKey("title"))
            validateRequired("title", newFields.get("title"), requiredFields, requiredErrors);
        if (newFields.containsKey("assignedTo") && !(Issue.statusCLOSED.equals(form.getBean().getStatus())))
            validateRequired("assignedto", newFields.get("assignedTo"), requiredFields, requiredErrors);
        if (newFields.containsKey("type"))
            validateRequired("type", newFields.get("type"), requiredFields, requiredErrors);
        if (newFields.containsKey("area"))
            validateRequired("area", newFields.get("area"), requiredFields, requiredErrors);
        if (newFields.containsKey("priority"))
            validateRequired("priority", newFields.get("priority"), requiredFields, requiredErrors);
        if (newFields.containsKey("milestone"))
            validateRequired("milestone", newFields.get("milestone"), requiredFields, requiredErrors);
        if (newFields.containsKey("notifyList"))
            validateRequired("notifylist", newFields.get("notifyList"), requiredFields, requiredErrors);
        if (newFields.containsKey("int1"))
            validateRequired("int1", newFields.get("int1"), requiredFields, requiredErrors);
        if (newFields.containsKey("int2"))
            validateRequired("int2", newFields.get("int2"), requiredFields, requiredErrors);
        if (newFields.containsKey("string1"))
            validateRequired("string1", newFields.get("string1"), requiredFields, requiredErrors);
        if (newFields.containsKey("string2"))
            validateRequired("string2", newFields.get("string2"), requiredFields, requiredErrors);
        if (newFields.containsKey("string3"))
            validateRequired("string3", newFields.get("string3"), requiredFields, requiredErrors);
        if (newFields.containsKey("string4"))
            validateRequired("string4", newFields.get("string4"), requiredFields, requiredErrors);
        if (newFields.containsKey("string5"))
            validateRequired("string5", newFields.get("string5"), requiredFields, requiredErrors);
        if (newFields.containsKey("comment"))
            validateRequired("comment", newFields.get("comment"), requiredFields, requiredErrors);

        // When resolving Duplicate, the 'duplicate' field should be set.
        if ("Duplicate".equals(newFields.get("resolution")))
            validateRequired("duplicate", newFields.get("duplicate"), "duplicate", requiredErrors);

        errors.addAllErrors(requiredErrors);
    }


    private void validateRequired(String columnName, String value, String requiredFields, Errors errors)
    {
        if (requiredFields != null)
        {
            if (requiredFields.indexOf(columnName) != -1)
            {
                if (StringUtils.isEmpty(value))
                {
                    final CustomColumnConfiguration ccc = getCustomColumnConfiguration();
                    String name = null;

                    // TODO: Not sure what to do here
                    if (ccc.shouldDisplay(columnName))
                    {
                        name = ccc.getCaption(columnName);
                    }
                    else
                    {
                        ColumnInfo column = IssuesSchema.getInstance().getTableInfoIssues().getColumn(columnName);
                        if (column != null)
                            name = column.getName();
                    }

                    String display = name == null ? columnName : name;
                    errors.rejectValue(columnName, "NullError", new Object[] {display}, display + " is required.");
                }
            }
        }
    }
    

    private void validateNotifyList(IssuesForm form, Errors errors)
    {
        String[] rawEmails = StringUtils.split(StringUtils.trimToEmpty(form.getNotifyList()), ";\n");
        List<String> invalidEmails = new ArrayList<>();
        SecurityManager.normalizeEmails(rawEmails, invalidEmails);

        for (String rawEmail : invalidEmails)
        {
            rawEmail = rawEmail.trim();

            // Ignore lines of all whitespace, otherwise show an error.
            if (!"".equals(rawEmail))
            {
                // try to resolve by display names as well
                User user = UserManager.getUserByDisplayName(rawEmail);
                if (user == null)
                {
                    String message = "Failed to add user " + rawEmail + ": Invalid email address";
                    errors.rejectValue("notifyList","Error",new Object[] {message}, message);
                }
            }
        }
    }

    private void validateAssignedTo(IssuesForm form, Errors errors)
    {
        // here we check that the user is a valid assignee
        Integer userId = form.getBean().getAssignedTo();

        if (userId != null)
        {
            User user = UserManager.getUser(userId);
            // TODO: consider exposing IssueManager.canAssignTo
            if (!user.isActive() || !getContainer().hasPermission(user, UpdatePermission.class))
                errors.rejectValue("assignedTo", ERROR_MSG, "An invalid user was set for the Assigned To");
        }
    }

    public static class CompleteUserForm
    {
        private String _prefix;
        private String _issueId;

        public String getPrefix(){return _prefix;}
        public void setPrefix(String prefix){_prefix = prefix;}

        public String getIssueId(){return _issueId;}
        public void setIssueId(String issueId){_issueId = issueId;}
    }


    @RequiresPermissionClass(InsertPermission.class)
    public class CompleteUserAction extends ApiAction<CompleteUserForm>
    {
        @Override
        public ApiResponse execute(CompleteUserForm completeUserForm, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            List<JSONObject> completions = new ArrayList<>();

            List<User> possibleUsers = SecurityManager.getUsersWithPermissions(getContainer(), Collections.<Class<? extends Permission>>singleton(ReadPermission.class));
            for (AjaxCompletion completion : UserManager.getAjaxCompletions(possibleUsers, getUser(), getContainer()))
                    completions.add(completion.toJSON());

            response.put("completions", completions);

            return response;
        }
    }

    public class UpdateEmailPage
    {
        public UpdateEmailPage(String url, Issue issue, boolean isPlain)
        {
            this.url = url;
            this.issue = issue;
            this.isPlain = isPlain;
        }
        public String url;
        public Issue issue;
        public boolean isPlain;
    }


    private void sendUpdateEmail(Issue issue, Issue prevIssue, String fieldChanges, String summary, String comment, ActionURL detailsURL, String change, List<AttachmentFile> attachments, Class<? extends Controller> action) throws ServletException
    {
        // Skip the email if no comment and no public fields have changed, #17304
        if (fieldChanges.isEmpty() && comment.isEmpty())
            return;

        final Set<User> allAddresses = getUsersToEmail(issue, prevIssue, action);
        for (User user : allAddresses)
        {
            String to = user.getEmail();
            try
            {
                Issue.Comment lastComment = issue.getLastComment();
                String messageId = "<" + issue.getEntityId() + "." + lastComment.getCommentId() + "@" + AppProps.getInstance().getDefaultDomain() + ">";
                String references = messageId + " <" + issue.getEntityId() + "@" + AppProps.getInstance().getDefaultDomain() + ">";
                MailHelper.ViewMessage m = MailHelper.createMessage(LookAndFeelProperties.getInstance(getContainer()).getSystemEmailAddress(), to);
                Address[] addresses = m.getAllRecipients();
                if (addresses != null && addresses.length > 0)
                {
                    IssueUpdateEmailTemplate template = EmailTemplateService.get().getEmailTemplate(IssueUpdateEmailTemplate.class, getContainer());
                    template.init(issue, detailsURL, change, comment, fieldChanges, allAddresses, attachments);

                    m.setSubject(template.renderSubject(getContainer()));
                    m.setHeader("References", references);
                    String body = template.renderBody(getContainer());
                    m.setText(body);

                    MailHelper.send(m, getUser(), getContainer());
                }
            }
            catch (ConfigurationException e)
            {
                _log.error("error sending update email to " + to, e);
            }
            catch (AddressException e)
            {
                _log.error("error sending update email to " + to, e);
            }
            catch (Exception e)
            {
                _log.error("error sending update email to " + to, e);
                ExceptionUtil.logExceptionToMothership(null, e);
            }
        }
    }

    /**
     * Builds the list of email addresses for notification based on the user
     * preferences and the explicit notification list.
     */
    private Set<User> getUsersToEmail(Issue issue, Issue prevIssue, Class<? extends Controller> action) throws ServletException
    {
        final Set<User> emailUsers = new HashSet<>();
        final Container c = getContainer();
        int assignedToPref = IssueManager.getUserEmailPreferences(c, issue.getAssignedTo());
        int assignedToPrev = prevIssue != null && prevIssue.getAssignedTo() != null ? prevIssue.getAssignedTo() : 0;
        int assignedToPrevPref = assignedToPrev != 0 ? IssueManager.getUserEmailPreferences(c, prevIssue.getAssignedTo()) : 0;
        int createdByPref = IssueManager.getUserEmailPreferences(c, issue.getCreatedBy());

        if (InsertAction.class.equals(action))
        {
            if ((assignedToPref & IssueManager.NOTIFY_ASSIGNEDTO_OPEN) != 0)
                safeAddEmailUsers(emailUsers, UserManager.getUser(issue.getAssignedTo()));
        }
        else
        {
            if ((assignedToPref & IssueManager.NOTIFY_ASSIGNEDTO_UPDATE) != 0)
                safeAddEmailUsers(emailUsers, UserManager.getUser(issue.getAssignedTo()));

            if ((assignedToPrevPref & IssueManager.NOTIFY_ASSIGNEDTO_UPDATE) != 0)
                safeAddEmailUsers(emailUsers, UserManager.getUser(prevIssue.getAssignedTo()));

            if ((createdByPref & IssueManager.NOTIFY_CREATED_UPDATE) != 0)
                safeAddEmailUsers(emailUsers, UserManager.getUser(issue.getCreatedBy()));
        }

        // add any users subscribed to this forum
        List<ValidEmail> subscribedEmails = IssueManager.getSubscribedUserEmails(c);
        for (ValidEmail email : subscribedEmails)
            safeAddEmailUsers(emailUsers, UserManager.getUser(email));

        // add any explicit notification list addresses
        List<ValidEmail> emails = issue.getNotifyListEmail();
        for (ValidEmail email : emails)
            safeAddEmailUsers(emailUsers, UserManager.getUser(email));

        boolean selfSpam = !((IssueManager.NOTIFY_SELF_SPAM & IssueManager.getUserEmailPreferences(c, getUser().getUserId())) == 0);
        if (selfSpam)
            safeAddEmailUsers(emailUsers, getUser());
        else
            emailUsers.remove(getUser());

        return emailUsers;
    }

    private void safeAddEmailUsers(Set<User> users, User user)
    {
        if (user != null && user.isActive())
            users.add(user);
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class EmailPrefsAction extends FormViewAction<EmailPrefsForm>
    {
        String _message = null;

        public ModelAndView getView(EmailPrefsForm form, boolean reshow, BindException errors) throws Exception
        {
            if (getUser().isGuest())
            {
                throw new UnauthorizedException();
            }

            int emailPrefs = IssueManager.getUserEmailPreferences(getContainer(), getUser().getUserId());
            int issueId = form.getIssueId() == null ? 0 : form.getIssueId().intValue();
            return new JspView<>(IssuesController.class, "emailPreferences.jsp",
                new EmailPrefsBean(emailPrefs, errors, _message, issueId));
        }

        public boolean handlePost(EmailPrefsForm form, BindException errors) throws Exception
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

    @RequiresPermissionClass(AdminPermission.class)
    public class GetMoveDestinationsAction extends ApiAction
    {
        @Override
        public ApiResponse execute(Object object, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            Collection<Map<String, Object>> responseContainers = new LinkedList<>();
            for (Container container : IssueManager.getMoveDestinationContainers(getContainer()))
            {
                Map<String, Object> map = new HashMap<>();
                map.put("containerId", container.getId());
                map.put("containerPath", container.getPath());
                responseContainers.add(map);
            }

            response.put("containers", responseContainers);

            return response;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
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
                // remove containers that start with underscroll
                if (container.getName().startsWith("_")) continue;

                Map<String, Object> map = new HashMap<>();
                map.put("containerId", container.getId());
                map.put("containerPath", container.getPath());
                responseContainers.add(map);
            }

            response.put("containers", responseContainers);

            return response;
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
    @RequiresPermissionClass(AdminPermission.class) @CSRF
    public class MoveAction extends MutatingApiAction<MoveIssueForm>
    {

        @Override
        public ApiResponse execute(MoveIssueForm form, BindException errors) throws Exception
        {
            DbSchema schema = IssuesSchema.getInstance().getSchema();

            try (DbScope.Transaction transaction = schema.getScope().ensureTransaction())
            {
                List<AttachmentParent> attachmentParents = new ArrayList<>();
                for (int issueId : form.getIssueIds())
                    for (Issue.Comment comment : IssueManager.getIssue(null, issueId).getComments())
                        attachmentParents.add(comment);

                SQLFragment update = new SQLFragment("UPDATE issues.issues SET container = ? ", form.getContainerId());
                update.append("WHERE issueId ");
                schema.getSqlDialect().appendInClauseSql(update, Arrays.asList(form.getIssueIds()));
                new SqlExecutor(schema).execute(update);

                Container newContainer = ContainerManager.getForId(form.getContainerId());
                AttachmentService.get().moveAttachments(newContainer, attachmentParents, getUser());

                transaction.commit();
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


    @RequiresPermissionClass(AdminPermission.class)
    public class AdminAction extends FormViewAction<AdminForm>
    {
        public ModelAndView getView(AdminForm form, boolean reshow, BindException errors) throws Exception
        {
            return new AdminView(getContainer(), getCustomColumnConfiguration(), errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            EntryTypeNames names = IssueManager.getEntryTypeNames(getContainer());
            return (new ListAction(getViewContext())).appendNavTrail(root).addChild(names.pluralName.getSource() + " Admin Page", getUrl());
        }

        public ActionURL getUrl()
        {
            return issueURL(AdminAction.class);
        }


        @Override
        public void validateCommand(AdminForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(AdminForm adminForm, BindException errors) throws Exception
        {
            return false;
        }

        @Override
        public URLHelper getSuccessURL(AdminForm adminForm)
        {
            return getUrl();
        }
    }


    public abstract class AdminFormAction extends FormHandlerAction<AdminForm>
    {
        public void validateCommand(AdminForm adminForm, Errors errors)
        {
        }

        public ActionURL getSuccessURL(AdminForm adminForm)
        {
            return issueURL(AdminAction.class);
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class AddKeywordAction extends AdminAction
    {
        private ColumnType _type;

        @Override
        public void validateCommand(AdminForm form, Errors errors)
        {
            _type = ColumnType.forOrdinal(form.getType());
            String keyword = form.getKeyword();

            if (null == _type)
            {
                errors.reject(ERROR_MSG, "Unknown keyword type");
            }
            if (null == keyword || StringUtils.isBlank(keyword))
            {
                errors.reject(ERROR_MSG, "Enter a value in the text box before clicking any of the \"Add <Keyword>\" buttons");
            }
            else
            {
                if (ColumnType.PRIORITY == _type)
                {
                    try
                    {
                        Integer.parseInt(keyword);
                    }
                    catch (NumberFormatException e)
                    {
                        errors.reject(ERROR_MSG, "Priority must be an integer");
                    }
                }
                else
                {
                    if (keyword.length() > 200)
                        errors.reject(ERROR_MSG, "The keyword is too long, it must be under 200 characters.");

                    Collection<Keyword> keywords = KeywordManager.getKeywords(getContainer(), _type);

                    for (Keyword word : keywords)
                    {
                        if (word.getKeyword().compareToIgnoreCase(keyword)== 0)
                            errors.reject(ERROR_MSG, "\"" + word.getKeyword() + "\" already exists");
                    }
                }
            }
        }

        public boolean handlePost(AdminForm form, BindException errors) throws Exception
        {
            try
            {
                KeywordManager.addKeyword(getContainer(), _type, form.getKeyword());
            }
            catch (Exception e)
            {
                if (RuntimeSQLException.isConstraintException(null))
                {
                    errors.reject(ERROR_MSG, "\"" + form.getKeyword() + "\" already exists");
                    return false;
                }

                throw e;
            }

            return true;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class DeleteKeywordAction extends AdminFormAction
    {
        public boolean handlePost(AdminForm form, BindException errors) throws Exception
        {
            KeywordManager.deleteKeyword(getContainer(), ColumnType.forOrdinal(form.getType()), form.getKeyword());
            return true;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class SetKeywordDefaultAction extends AdminFormAction
    {
        public boolean handlePost(AdminForm form, BindException errors) throws Exception
        {
            KeywordManager.setKeywordDefault(getContainer(), ColumnType.forOrdinal(form.getType()), form.getKeyword());
            return true;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class ClearKeywordDefaultAction extends AdminFormAction
    {
        public boolean handlePost(AdminForm form, BindException errors) throws Exception
        {
            KeywordManager.clearKeywordDefault(getContainer(), ColumnType.forOrdinal(form.getType()));
            return true;
        }
    }

    public static class ConfigureIssuesForm
    {
        public static enum ParamNames
        {
            entrySingularName,
            entryPluralName,
            direction
        }

        private String _direction;
        private String _assignedToMethod = null;
        private int _assignedToGroup = 0;
        private String _assignedToUser = null;
        private int _defaultUser = 0;

        private String _moveToContainer = null;
        private String _moveToContainerSelect = null;

        private HString[] _requiredFields = new HString[0];

        private HString _entrySingularName;
        private HString _entryPluralName;

        public String getDirection()
        {
            return _direction;
        }

        public void setDirection(String direction)
        {
            _direction = direction;
        }

        public String getAssignedToMethod()
        {
            return _assignedToMethod;
        }

        public void setAssignedToMethod(String assignedToMethod)
        {
            _assignedToMethod = assignedToMethod;
        }

        public int getAssignedToGroup()
        {
            return _assignedToGroup;
        }

        public void setAssignedToGroup(int assignedToGroup)
        {
            _assignedToGroup = assignedToGroup;
        }

        public String getAssignedToUser()
        {
            return _assignedToUser;
        }

        public void setAssignedToUser(String assignedToUser)
        {
            _assignedToUser = assignedToUser;
        }

        public int getDefaultUser()
        {
            return _defaultUser;
        }

        public void setDefaultUser(int defaultUser)
        {
            _defaultUser = defaultUser;
        }

        public String getMoveToContainer()
        {
            return _moveToContainer;
        }

        public void setMoveToContainer(String moveToContainer)
        {
            _moveToContainer = moveToContainer;
        }

        public String getMoveToContainerSelect()
        {
            return _moveToContainerSelect;
        }

        public void setMoveToContainerSelect(String moveToContainerSelect)
        {
            _moveToContainerSelect = moveToContainerSelect;
        }

        public HString getEntrySingularName()
        {
            return _entrySingularName;
        }

        public void setEntrySingularName(HString entrySingularName)
        {
            _entrySingularName = entrySingularName;
        }

        public HString getEntryPluralName()
        {
            return _entryPluralName;
        }

        public void setEntryPluralName(HString entryPluralName)
        {
            _entryPluralName = entryPluralName;
        }

        public void setRequiredFields(HString[] requiredFields){_requiredFields = requiredFields;}
        public HString[] getRequiredFields(){return _requiredFields;}
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class ConfigureIssuesAction extends FormHandlerAction<ConfigureIssuesForm>
    {
        private Group _group = null;
        private User _user = null;
        private List<Container> _moveToContainers = new LinkedList<>();
        private Sort.SortDirection _direction = Sort.SortDirection.ASC;

        public void validateCommand(ConfigureIssuesForm form, Errors errors)
        {
            checkPickLists(form, errors);
            
            CustomColumnConfiguration ccc = new CustomColumnConfiguration(getViewContext());
            String defaultCols[] = {"Milestone", "Area", "Type", "Priority", "Resolution", "Status"};

            Map<String, String> captions = ccc.getColumnCaptions(); //All of the custom captions
            for (String column : defaultCols)
            {
                //Here we add the default captions if the user hasn't changed them.
                if (captions.get(column.toLowerCase()) == null)
                {
                    captions.put(column.toLowerCase(), column);
                }
            }

            CaseInsensitiveHashSet uniqueCaptions = new CaseInsensitiveHashSet(captions.values());
            if (captions.size() > uniqueCaptions.size())
            {
                errors.reject(ERROR_MSG, "Custom field names must be unique.");
            }

            if (form.getAssignedToMethod().equals("ProjectUsers"))
            {
                if (form.getAssignedToGroup() != 0)
                    errors.reject("assignedToGroup", "Project users setting shouldn't include a group!");
            }
            else if (form.getAssignedToMethod().equals("Group"))
            {
                int groupId = form.getAssignedToGroup();
                _group = SecurityManager.getGroup(groupId);

                if (null == _group)
                    errors.reject("assignedToGroup", "Group does not exist!");
            }
            else
            {
                errors.reject("assignedToGroup", "Invalid assigned to setting!");
            }

            if (form.getAssignedToUser().equals("NoDefaultUser"))
            {
                if (form.getDefaultUser() != 0)
                    errors.reject("assignedToUser", "No default user setting shouldn't include a default user!");
            }
            else if (form.getAssignedToUser().equals("SpecificUser"))
            {
                int userId = form.getDefaultUser();
                _user = UserManager.getUser(userId);

                if (null == _user)
                    errors.reject("assignedToUser", "User does not exist!");
            }
            else
            {
                errors.reject("assignedToUser", "Invalid assigned to setting!");
            }

            if (form.getMoveToContainer().equals("NoMoveToContainer"))
            {
                if (form.getMoveToContainerSelect() != null)
                    errors.reject("moveToContainer", "No move to container setting shouldn't include a default container!");
            }
            else if (form.getMoveToContainer().equals("SpecificMoveToContainer"))
            {
                String moveToContainers = form.getMoveToContainerSelect();
                if (moveToContainers != null)
                {
                    String[] containerPaths = StringUtils.split(moveToContainers, ';');

                    for (String containerPath : containerPaths)
                    {
                        Container container = ContainerManager.getForPath(containerPath);
                        if (null == container)
                        {
                            errors.reject("moveToContainer", "Container does not exist!");
                            break;
                        }
                        _moveToContainers.add(container);
                    }
                }
                else
                    errors.reject("moveToContainer", "The move to specific container option was selected with a blank.");
            }
            else
            {
                errors.reject("moveToContainer", "Invalid move to setting!");
            }

            if (form.getEntrySingularName() == null || form.getEntrySingularName().trimToEmpty().length() == 0)
                errors.reject(ConfigureIssuesForm.ParamNames.entrySingularName.name(), "You must specify a value for the entry type singular name!");
            if (form.getEntryPluralName()== null || form.getEntryPluralName().trimToEmpty().length() == 0)
                errors.reject(ConfigureIssuesForm.ParamNames.entryPluralName.name(), "You must specify a value for the entry type plural name!");

            try
            {
                if (form.getDirection() == null)
                {
                    errors.reject(ConfigureIssuesForm.ParamNames.direction.name(), "You must specify a comment sort direction!");
                }
                _direction = Sort.SortDirection.valueOf(form.getDirection());
            }
            catch (IllegalArgumentException e)
            {
                errors.reject(ConfigureIssuesForm.ParamNames.direction.name(), "You must specify a valid comment sort direction!");
            }
        }

        private void checkPickLists(ConfigureIssuesForm form, Errors errors)
        {
            ArrayList<HString> newRequiredFields = new ArrayList<>();
             /**
             * You have to make the required fields all lower case to compare them to the STRING_#_STRING constants.
             * I made the mistake of trying to make the field use lowercase names but it ruins the camelcasing when
             * you ouput the form on the JSP, which then breaks the tests.
             */
            for(HString required : form.getRequiredFields())
            {
                newRequiredFields.add(required.toLowerCase());
            }

            CustomColumnConfiguration newColumnConfiguration = new CustomColumnConfiguration(getViewContext());
            CustomColumnConfiguration oldColumnConfiguration = getCustomColumnConfiguration();

            for (HString required : form.getRequiredFields())
            {
                /**
                 * If the required field is one of the custom string fields, and it has no keywords, and it has just been
                 * selected (in the new picklist, but not old), then we remove it from the required fields. This way you
                 * don't have a required field with no keywords.
                 */
                ColumnType type = ColumnType.forName(required.toString());

                if (null != type && type.isCustomString() && KeywordManager.getKeywords(getContainer(), type).isEmpty())
                {
                    String name = type.getColumnName();

                    if (newColumnConfiguration.hasPickList(name) && !oldColumnConfiguration.hasPickList(name))
                        newRequiredFields.remove(new HString(name));
                }
            }

            form.setRequiredFields(newRequiredFields.toArray(new HString[newRequiredFields.size()]));
        }

        public boolean handlePost(ConfigureIssuesForm form, BindException errors)
        {
            EntryTypeNames names = new EntryTypeNames();

            names.singularName = form.getEntrySingularName();
            names.pluralName = form.getEntryPluralName();

            IssueManager.saveEntryTypeNames(getContainer(), names);
            IssueManager.saveAssignedToGroup(getContainer(), _group);
            IssueManager.saveCommentSortDirection(getContainer(), _direction);
            IssueManager.saveDefaultAssignedToUser(getContainer(), _user);
            IssueManager.saveMoveDestinationContainers(getContainer(), _moveToContainers);

            CustomColumnConfiguration nccc = new CustomColumnConfiguration(getViewContext());
            IssueManager.saveCustomColumnConfiguration(getContainer(), nccc);

            IssueManager.setRequiredIssueFields(getContainer(), form.getRequiredFields());
            return true;
        }

        public ActionURL getSuccessURL(ConfigureIssuesForm form)
        {
            return issueURL(AdminAction.class);
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class RssAction extends SimpleViewAction
    {
        @Override
        public void checkPermissions() throws UnauthorizedException
        {
            setUseBasicAuthentication(true);
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

                WebPartView v = new JspView<>("/org/labkey/issue/rss.jsp", new RssBean(issues, detailsURLString));
                v.setFrame(WebPartView.FrameType.NOT_HTML);

                return v;
            }
            catch (SQLException x)
            {
                x.printStackTrace();
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

    public static List<Issue.Comment> getRecentComments(Container container, User user, int limit)
    {
        // Defaults to the 20 most recent comments
        if (limit == 0)
            limit = 20;

        List<Issue.Comment> result = new ArrayList<>(limit);

        SimpleFilter filter = new SimpleFilter();
        ContainerFilter containerFilter = new ContainerFilter.CurrentAndSubfolders(user);
        FieldKey containerFieldKey = FieldKey.fromParts("IssueId_Container");
        filter.addCondition(containerFilter.createFilterClause(IssuesSchema.getInstance().getSchema(), containerFieldKey, container));

        Sort sort = new Sort("-Created");

        // Selecting comments as maps so we can get the issue id -- it's not on the Comment entity.
        List<FieldKey> fields = new ArrayList<>(IssuesSchema.getInstance().getTableInfoComments().getDefaultVisibleColumns());
        fields.add(containerFieldKey);

        Map<FieldKey, ColumnInfo> columnMap = QueryService.get().getColumns(IssuesSchema.getInstance().getTableInfoComments(), fields);

        TableSelector selector = new TableSelector(IssuesSchema.getInstance().getTableInfoComments(), columnMap.values(), filter, sort);
        selector.setMaxRows(limit);
        Collection<Map<String, Object>> comments = selector.getMapCollection();
        ObjectFactory<Issue.Comment> commentFactory = ObjectFactory.Registry.getFactory(Issue.Comment.class);
        Map<Integer, Issue> issuesIds = new HashMap<>();

        for (Map<String, Object> comment : comments)
        {
            Integer issueId = (Integer)comment.get("issueid");
            if (issueId == null)
                continue;

            Issue issue = issuesIds.get(issueId);
            if (issue == null)
            {
                issue = new TableSelector(IssuesSchema.getInstance().getTableInfoIssues()).getObject(issueId, Issue.class);
                issuesIds.put(issueId, issue);
            }

            Issue.Comment c = commentFactory.fromMap(comment);
            c.setIssue(issue);
            result.add(c);
        }

        return result;
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class PurgeAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            if (!getUser().isSiteAdmin())   // GLOBAL
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


    @RequiresPermissionClass(ReadPermission.class)
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
            }
            ActionURL url = getViewContext().cloneActionURL();
            url.deleteParameters();
            url.addParameter("error", "Invalid issue id '" + issueId + "'");
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
        public String getResultName()
        {
            return "issue";
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
                statusResearchURL.addParameter("_dc", (int)Math.round(1000 * Math.random()));

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
            String status = ctx.getActionURL().getParameter("status");
            root.addChild("Issues List", issueURL(ctx.getContainer(), ListAction.class).addParameter(DataRegion.LAST_FILTER_PARAM, "true"));
            root.addChild("Search " + (null != status ? status + " " : "") + "Issues");

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
    @RequiresPermissionClass(ReadPermission.class)
    public class SearchAction extends SimpleRedirectAction<SearchForm>
    {
        @Override
        public URLHelper getRedirectURL(SearchForm form) throws Exception
        {
            return PageFlowUtil.urlProvider(SearchUrls.class).getSearchURL(getContainer(), form.getQ(), IssueSearchResultTemplate.NAME);
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class GetIssueAction extends ApiAction<IssueIdForm>
    {
        @Override
        public ApiResponse execute(IssueIdForm issueIdForm, BindException errors) throws Exception
        {
            User user = getUser();
            Issue issue = getIssue(issueIdForm.getIssueId(), false);
            //IssuePage page = new IssuePage();

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
            for (Issue.Comment c : issue.getComments())
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


    @RequiresPermissionClass(ReadPermission.class)
    public class AppAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            getPageConfig().setTemplate(PageConfig.Template.Print);
            return new JspView(IssuesController.class, "extjs4.jsp", null);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }


    private static void _appendChange(StringBuilder sbHTML, StringBuilder sbText, String internalFieldName, String from, String to, CustomColumnConfiguration ccc, boolean newIssue)
    {
        // Use custom caption if one is configured
        CustomColumn cc = ccc.getCustomColumn(internalFieldName.toLowerCase());
        _appendChange(sbHTML, sbText, internalFieldName, cc, from, to, newIssue);
    }


    private static void _appendChange(StringBuilder sbHTML, StringBuilder sbText, String internalFieldName, @Nullable CustomColumn cc, String from, String to, boolean newIssue)
    {
        // Use custom caption if one is configured
        String encField = PageFlowUtil.filter(null != cc ? cc.getCaption() : internalFieldName);
        from = from == null ? "" : from;
        to = to == null ? "" : to;

        if (!from.equals(to))
        {
            sbText.append(encField);
            if (newIssue)
            {
                sbText.append(" set");
            }
            else
            {
                sbText.append(" changed from ");
                sbText.append(HString.EMPTY.equals(from) ? "blank" : "\"" + from + "\"");
            }
            sbText.append(" to ");
            sbText.append(HString.EMPTY.equals(to) ? "blank" : "\"" + to + "\"");
            sbText.append("\n");
            String encFrom = PageFlowUtil.filter(from);
            String encTo = PageFlowUtil.filter(to);
            sbHTML.append("<tr><td>").append(encField).append("</td><td>").append(encFrom).append("</td><td>&raquo;</td><td>").append(encTo).append("</td></tr>\n");
        }
    }

    private static class ChangeSummary
    {
        private Issue.Comment _comment;
        private String _textChanges;
        private String _summary;

        private ChangeSummary(Issue.Comment comment, String textChanges, String summary)
        {
            _comment = comment;
            _textChanges = textChanges;
            _summary = summary;
        }

        public Issue.Comment getComment()
        {
            return _comment;
        }

        public String getTextChanges()
        {
            return _textChanges;
        }

        public String getSummary()
        {
            return _summary;
        }
    }

    static ChangeSummary createChangeSummary(Issue issue, Issue previous, @Nullable Issue duplicateOf, User user, Class<? extends Controller> action, String comment, CustomColumnConfiguration ccc, User currentUser)
    {
        StringBuilder sbHTMLChanges = new StringBuilder();
        StringBuilder sbTextChanges = new StringBuilder();
        String summary = null;

        if (!action.equals(InsertAction.class) && !action.equals(UpdateAction.class))
        {
            summary = getActionName(action).toLowerCase();

            if (action.equals(ResolveAction.class))
            {
                // Add the resolution; e.g. "resolve as Fixed"
                summary += " as " + issue.getResolution();
                if (duplicateOf != null)
                    summary += " of " + duplicateOf.getIssueId();
            }

            sbHTMLChanges.append("<b>").append(summary);
            sbHTMLChanges.append("</b><br>\n");
        }
        
        // CONSIDER: write changes in wiki
        // CONSIDER: and postpone formatting until render
        if (null != previous)
        {
            // Keep track of whether this issue is new
            boolean newIssue = previous.getIssueId() == 0;
            String prevInt1StringVal = previous.getInt1() == null ? "" : String.valueOf(previous.getInt1());
            String prevInt2StringVal = previous.getInt2() == null ? "" : String.valueOf(previous.getInt2());
            String pevPriStringVal = previous.getPriority() == null ? "" : String.valueOf(previous.getPriority());

            String priStringVal = issue.getPriority() == null ? "" : String.valueOf(issue.getPriority());
            String int1StringVal = issue.getInt1() == null ? "" : String.valueOf(issue.getInt1());
            String int2StringVal = issue.getInt2() == null ? "" : String.valueOf(issue.getInt2());

            // issueChanges is not defined yet, but it leaves things flexible
            sbHTMLChanges.append("<table class=issues-Changes>");
            _appendChange(sbHTMLChanges, sbTextChanges, "Title", previous.getTitle(), issue.getTitle(), ccc, newIssue);
            _appendChange(sbHTMLChanges, sbTextChanges, "Status", previous.getStatus(), issue.getStatus(), ccc, newIssue);
            _appendChange(sbHTMLChanges, sbTextChanges, "Assigned To", previous.getAssignedToName(currentUser), issue.getAssignedToName(currentUser), ccc, newIssue);
            _appendChange(sbHTMLChanges, sbTextChanges, "Notify",
                    StringUtils.join(previous.getNotifyListDisplayNames(null),";"),
                    StringUtils.join(issue.getNotifyListDisplayNames(null),";"),
                    ccc, newIssue);
            _appendChange(sbHTMLChanges, sbTextChanges, "Type", previous.getType(), issue.getType(), ccc, newIssue);
            _appendChange(sbHTMLChanges, sbTextChanges, "Area", previous.getArea(), issue.getArea(), ccc, newIssue);
            _appendChange(sbHTMLChanges, sbTextChanges, "Priority", pevPriStringVal, priStringVal, ccc, newIssue);
            _appendChange(sbHTMLChanges, sbTextChanges, "Milestone", previous.getMilestone(), issue.getMilestone(), ccc, newIssue);
            _appendChange(sbHTMLChanges, sbTextChanges, "Related", StringUtils.join(previous.getRelatedIssues(), ", "), StringUtils.join(issue.getRelatedIssues(), ", "), ccc, newIssue);

            _appendCustomColumnChange(sbHTMLChanges, sbTextChanges, "int1", prevInt1StringVal, int1StringVal, ccc, newIssue);
            _appendCustomColumnChange(sbHTMLChanges, sbTextChanges, "int2", prevInt2StringVal, int2StringVal, ccc, newIssue);
            _appendCustomColumnChange(sbHTMLChanges, sbTextChanges, "string1", previous.getString1(), issue.getString1(), ccc, newIssue);
            _appendCustomColumnChange(sbHTMLChanges, sbTextChanges, "string2", previous.getString2(), issue.getString2(), ccc, newIssue);
            _appendCustomColumnChange(sbHTMLChanges, sbTextChanges, "string3", previous.getString3(), issue.getString3(), ccc, newIssue);
            _appendCustomColumnChange(sbHTMLChanges, sbTextChanges, "string4", previous.getString4(), issue.getString4(), ccc, newIssue);
            _appendCustomColumnChange(sbHTMLChanges, sbTextChanges, "string5", previous.getString5(), issue.getString5(), ccc, newIssue);

            sbHTMLChanges.append("</table>\n");
        }

        //why we are wrapping issue comments in divs???
        StringBuilder formattedComment = new StringBuilder();
        formattedComment.append("<div class=\"wiki\">");
        formattedComment.append(sbHTMLChanges);
        //render issues as plain text with links
        WikiService wikiService = ServiceRegistry.get().getService(WikiService.class);
        if (null != wikiService)
        {
            String html = wikiService.getFormattedHtml(WikiRendererType.TEXT_WITH_LINKS, comment);
            formattedComment.append(html);
        }
        else
            formattedComment.append(comment);

        formattedComment.append("</div>");

        return new ChangeSummary(issue.addComment(user, formattedComment.toString()), sbTextChanges.toString(), summary);
    }

    private static void _appendCustomColumnChange(StringBuilder sbHtml, StringBuilder sbText, String internalFieldName, String from, String to, CustomColumnConfiguration ccc, boolean newIssue)
    {
        CustomColumn cc = ccc.getCustomColumn(internalFieldName);

        // Record only fields with read permissions
        if (null != cc && cc.getPermission().equals(ReadPermission.class))
            _appendChange(sbHtml, sbText, internalFieldName, cc, from, to, newIssue);
    }


    //
    // VIEWS
    //
    public static class AdminView extends JspView<AdminBean>
    {
        public AdminView(Container c, CustomColumnConfiguration ccc, BindException errors)
        {
            super("/org/labkey/issue/adminView.jsp", null, errors);

            KeywordAdminView keywordView = new KeywordAdminView(c, ccc);
            keywordView.addKeywordPicker(ColumnType.TYPE);
            keywordView.addKeywordPicker(ColumnType.AREA);
            keywordView.addKeywordPicker(ColumnType.PRIORITY);
            keywordView.addKeywordPicker(ColumnType.MILESTONE);
            keywordView.addKeywordPicker(ColumnType.RESOLUTION);
            keywordView.addKeywordPicker(ColumnType.STRING1);
            keywordView.addKeywordPicker(ColumnType.STRING2);
            keywordView.addKeywordPicker(ColumnType.STRING3);
            keywordView.addKeywordPicker(ColumnType.STRING4);
            keywordView.addKeywordPicker(ColumnType.STRING5);

            Set<String> columnNames = new LinkedHashSet<>();
            columnNames.addAll(Arrays.asList(REQUIRED_FIELDS_COLUMNS.split(",")));

            for (CustomColumn cc : IssueManager.getCustomColumnConfiguration(c).getCustomColumns())
                columnNames.add(cc.getName());

            List<ColumnInfo> cols = IssuesSchema.getInstance().getTableInfoIssues().getColumns(columnNames.toArray(new String[columnNames.size()]));

            AdminBean bean = new AdminBean(cols, IssueManager.getRequiredIssueFields(c), IssueManager.getEntryTypeNames(c));

            bean.ccc = ccc;
            bean.keywordView = keywordView;
            bean.entryTypeNames = IssueManager.getEntryTypeNames(c);
            bean.assignedToGroup = IssueManager.getAssignedToGroup(c);
            bean.defaultUser = IssueManager.getDefaultAssignedToUser(c);
            bean.moveToContainers = IssueManager.getMoveDestinationContainers(c);
            bean.commentSort = IssueManager.getCommentSortDirection(c);
            setModelBean(bean);
        }
    }


    public static class AdminBean
    {
        private List<ColumnInfo> _columns;
        private String _requiredFields;
        private EntryTypeNames _entryTypeNames;

        public CustomColumnConfiguration ccc;
        public KeywordAdminView keywordView;
        public EntryTypeNames entryTypeNames;
        public Group assignedToGroup;
        public User defaultUser;
        public List<Container> moveToContainers;
        public Sort.SortDirection commentSort;

        public AdminBean(List<ColumnInfo> columns, String requiredFields, EntryTypeNames typeNames)
        {
            _columns = columns;
            _requiredFields = requiredFields;
            _entryTypeNames = typeNames;
        }

        public List<ColumnInfo> getColumns(){return _columns;}
        public String getRequiredFields(){return _requiredFields;}
        public EntryTypeNames getEntryTypeNames() {return _entryTypeNames;}
    }


    // Renders the pickers for all keywords; would be nice to render each picker independently, but that makes it hard to align
    // all the top and bottom sections with each other.
    public static class KeywordAdminView extends JspView<List<KeywordPicker>>
    {
        private Container _c;
        private List<KeywordPicker> _keywordPickers = new LinkedList<>();
        public CustomColumnConfiguration _ccc;

        public KeywordAdminView(Container c, CustomColumnConfiguration ccc)
        {
            super("/org/labkey/issue/keywordAdmin.jsp");
            setModelBean(_keywordPickers);
            _c = c;
            _ccc = ccc;
        }

        private void addKeywordPicker(ColumnType type)
        {
            String columnName = type.getColumnName();

            if (type.isCustomString() && !_ccc.hasPickList(columnName))
                return;

            String caption = _ccc.getCaption(type.getColumnName());

            if (caption == null)
                caption = StringUtils.capitalize(type.getColumnName());

            _keywordPickers.add(new KeywordPicker(_c, caption, type));
        }
    }


    public static class KeywordPicker
    {
        public String name;
        public ColumnType type;
        public Collection<Keyword> keywords;

        KeywordPicker(Container c, String name, ColumnType type)
        {
            this.name = name;
            this.type = type;
            this.keywords = KeywordManager.getKeywords(c, type);
        }
    }


    public static class EmailPrefsBean
    {
        private int _emailPrefs;
        private BindException _errors;
        private String _message;
        private Integer _issueId;

        public EmailPrefsBean(int emailPreference, BindException errors, String message, Integer issueId)
        {
            _emailPrefs = emailPreference;
            _errors = errors;
            _message = message;
            _issueId = issueId;
        }

        public int getEmailPreference()
        {
            return _emailPrefs;
        }

        public BindException getErrors()
        {
            return _errors;
        }

        public String getMessage()
        {
            return _message;
        }

        public int getIssueId()
        {
            return _issueId.intValue();
        }
    }


    public static class EmailPrefsForm
    {
        private Integer[] _emailPreference = new Integer[0];
        private Integer _issueId;

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
            map.put("action", HString.class);
            map.put("comment", HString.class);
            map.put("callbackURL", ReturnURLString.class);
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

        // XXX: change return value to typed HString
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
    }


    public static class SummaryWebPart extends JspView<SummaryBean>
    {
        public SummaryWebPart()
        {
            super("/org/labkey/issue/summaryWebpart.jsp", new SummaryBean());

            SummaryBean bean = getModelBean();

            ViewContext context = getViewContext();
            Container c = context.getContainer();

            //set specified web part title
            Object title = context.get("title");
            if (title == null)
                title = IssueManager.getEntryTypeNames(c).pluralName.getSource() + " Summary";
            setTitle(title.toString());

            User u = context.getUser();
            bean.hasPermission = c.hasPermission(u, ReadPermission.class);
            if (!bean.hasPermission)
                return;

            setTitleHref(getListURL(c));

            bean.listURL = getListURL(c).deleteParameters();

            bean.insertURL = IssuesController.issueURL(context.getContainer(), InsertAction.class);

            try
            {
                bean.bugs = IssueManager.getSummary(c);
            }
            catch (SQLException x)
            {
                setVisible(false);
            }
        }
    }


    public static class SummaryBean
    {
        public boolean hasPermission;
        public Map[] bugs;
        public ActionURL listURL;
        public ActionURL insertURL;
    }


    protected synchronized void afterAction(Throwable t)
    {
        super.afterAction(t);
    }

    /**
     * Does this user have permission to update this issue?
     */
    private boolean hasUpdatePermission(User user, Issue issue)
    {
        return getContainer().hasPermission(user, UpdatePermission.class,
                (issue.getCreatedBy() == user.getUserId() ? RoleManager.roleSet(OwnerRole.class) : null));
    }

    private boolean hasAdminPermission(User user, Issue issue)
    {
        return getContainer().hasPermission(user, AdminPermission.class,
                (issue.getCreatedBy() == user.getUserId() ? RoleManager.roleSet(OwnerRole.class) : null));
    }


    /**
     * Throw an exception if user does not have permission to update issue
     */
    private void requiresUpdatePermission(User user, Issue issue)
            throws ServletException
    {
        if (!hasUpdatePermission(user, issue))
        {
            throw new UnauthorizedException();
        }
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
