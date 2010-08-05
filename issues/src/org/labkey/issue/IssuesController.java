/*
 * Copyright (c) 2004-2010 Fred Hutchinson Cancer Research Center
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

import junit.framework.Test;
import junit.framework.TestSuite;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;
import org.labkey.api.action.*;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.*;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.query.*;
import org.labkey.api.security.*;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.OwnerRole;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.*;
import org.labkey.api.util.emailTemplate.EmailTemplateService;
import org.labkey.api.view.*;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.wiki.WikiRenderer;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;
import org.labkey.issue.model.Issue;
import org.labkey.issue.model.IssueManager;
import org.labkey.issue.query.IssuesQuerySchema;
import org.labkey.issue.query.IssuesQueryView;
import org.labkey.issue.query.IssuesTable;
import org.springframework.validation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import javax.mail.Address;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class IssuesController extends SpringActionController
{
    private static Logger _log = Logger.getLogger(IssuesController.class);

    private static String helpTopic = "issues";

    // keywords enum
    public static final int ISSUE_NONE = 0;
    public static final int ISSUE_AREA = 1;
    public static final int ISSUE_TYPE = 2;
    public static final int ISSUE_MILESTONE = 3;
    public static final int ISSUE_STRING1 = 4;
    public static final int ISSUE_STRING2 = 5;
    public static final int ISSUE_PRIORITY = 6;
    public static final int ISSUE_RESOLUTION = 7;
    public static final int ISSUE_STRING3 = 8;
    public static final int ISSUE_STRING4 = 9;
    public static final int ISSUE_STRING5 = 10;


    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(IssuesController.class);

    public IssuesController() throws Exception
    {
        setActionResolver(_actionResolver);
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
        return config;
    }


    private Issue getIssue(int issueId) throws SQLException
    {
        return IssueManager.getIssue(getContainer(), issueId);
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


    private IssueManager.CustomColumnConfiguration getCustomColumnConfiguration() throws SQLException, ServletException
    {
        return IssueManager.getCustomColumnConfiguration(getContainer());
    }


    private Map<String, String> getColumnCaptions() throws SQLException, ServletException
    {
        return getCustomColumnConfiguration().getColumnCaptions();
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class SetCustomColumnConfigurationAction extends FormHandlerAction
    {
        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            IssueManager.CustomColumnConfiguration ccc = new IssueManager.CustomColumnConfiguration(getViewContext());
            IssueManager.saveCustomColumnConfiguration(getContainer(), ccc);
            return true;
        }

        public void validateCommand(Object o, Errors errors)
        {
        }

        public ActionURL getSuccessURL(Object o)
        {
            return (new AdminAction()).getUrl();
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class UpdateRequiredFieldsAction extends FormHandlerAction<IssuePreferenceForm>
    {
        public boolean handlePost(IssuePreferenceForm form, BindException errors) throws Exception
        {
            final StringBuilder sb = new StringBuilder();
            if (form.getRequiredFields().length > 0)
            {
                String sep = "";
                for (HString field : form.getRequiredFields())
                {
                    sb.append(sep);
                    sb.append(field);
                    sep = ";";
                }
            }
            IssueManager.setRequiredIssueFields(getContainer(), sb.toString());
            return true;
        }

        public void validateCommand(IssuePreferenceForm issuePreferenceForm, Errors errors)
        {
        }

        public ActionURL getSuccessURL(IssuePreferenceForm issuePreferenceForm)
        {
            return (new AdminAction()).getUrl();
        }
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

        IssuesQueryView queryView = new IssuesQueryView(getViewContext(), schema, settings);

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
            IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(getViewContext().getContainer());

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
            IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(getViewContext().getContainer());
            return root.addChild(names.pluralName + " List", getURL());
        }

        public ActionURL getURL()
        {
            return issueURL(ListAction.class).addParameter(".lastFilter","true");
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class ExportTsvAction extends SimpleViewAction<QueryForm>
    {
        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            getPageConfig().setTemplate(PageConfig.Template.None);
            QueryView view = QueryView.create(form);
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
            _issue = getIssue(issueId);

            IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(getViewContext().getContainer());
            if (null == _issue)
            {
                HttpView.throwNotFound("Unable to find " + names.singularName + " " + form.getIssueId());
                return null;
            }

            IssuePage page = new IssuePage();
            page.setPrint(isPrint());
            page.setIssue(_issue);
            page.setCustomColumnConfiguration(getCustomColumnConfiguration());
            //pass user's update perms to jsp page to determine whether to show notify list
            page.setUserHasUpdatePermissions(hasUpdatePermission(getUser(), _issue));
            page.setRequiredFields(IssueManager.getRequiredIssueFields(getContainer()));

            getPageConfig().setTitle("" + _issue.getIssueId() + " : " + _issue.getTitle().getSource());

            return new JspView<IssuePage>(IssuesController.class, "detailView.jsp", page);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return new ListAction(getViewContext()).appendNavTrail(root)
                    .addChild("Detail -- " + _issue.getIssueId(), getURL());
        }

        public ActionURL getURL()
        {
            return issueURL(DetailsAction.class).addParameter("issueId", _issue.getIssueId());
        }
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
            ArrayList<Issue> issueList = new ArrayList<Issue>();

            if (!issueIds.isEmpty())
            {
                for (String issueId : issueIds)
                {
                    issueList.add(getIssue(Integer.parseInt(issueId)));
                }
            }
            else
            {
                ResultSet rs = null;

                try
                {
                    rs = getIssuesResultSet();
                    int issueColumnIndex = rs.findColumn("issueId");

                    while (rs.next())
                    {
                        issueList.add(getIssue(rs.getInt(issueColumnIndex)));
                    }
                }
                finally
                {
                    ResultSetUtil.close(rs);
                }
            }

            IssuePage page = new IssuePage();
            JspView v = new JspView<IssuePage>(IssuesController.class, "detailList.jsp", page);

            page.setIssueList(issueList);
            page.setCustomColumnConfiguration(getCustomColumnConfiguration());
            page.setRequiredFields(IssueManager.getRequiredIssueFields(getContainer()));
            page.setDataRegionSelectionKey(listForm.getDataRegionSelectionKey());

            return v;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(getViewContext().getContainer());
            return new ListAction(getViewContext()).appendNavTrail(root).addChild(names.singularName + " Details");
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
                User user = UserManager.getUser(_issue.getAssignedTo().intValue());

                if (user != null)
                {
                    _issue.setAssignedTo(user.getUserId());
                }
            }

            _issue.open(getContainer(), getUser());
            if (!reshow)
            {
                setNewIssueDefaults(_issue);
            }

            IssuePage page = new IssuePage();
            JspView v = new JspView<IssuePage>(IssuesController.class, "updateView.jsp", page);

            IssueManager.CustomColumnConfiguration ccc = getCustomColumnConfiguration();

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
                validateNotifyList(form.getBean(), form, errors);
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
            validateNotifyList(_issue, form, errors);

            ChangeSummary changeSummary;

            DbScope scope = IssuesSchema.getInstance().getSchema().getScope();
            boolean ownsTransaction = !scope.isTransactionActive();
            try
            {
                if (ownsTransaction)
                    scope.beginTransaction();
                // for new issues, the original is always the default.
                Issue orig = new Issue();
                orig.open(getContainer(), getUser());

                changeSummary = createChangeSummary(_issue, orig, null, user, form.getAction(), form.getComment(), getColumnCaptions(), getViewContext());
                IssueManager.saveIssue(user, c, _issue);
                AttachmentService.get().addAttachments(user, changeSummary.getComment(), getAttachmentFileList());
                if (ownsTransaction)
                    scope.commitTransaction();
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
            finally
            {
                if (ownsTransaction)
                    scope.closeConnection();
            }

            ActionURL url = new DetailsAction(_issue, getViewContext()).getURL();

            final String assignedTo = UserManager.getDisplayName(_issue.getAssignedTo(), getViewContext());
            if (assignedTo != null)
                sendUpdateEmail(_issue, null, changeSummary.getTextChanges(), changeSummary.getSummary(), form.getComment(), url, "opened and assigned to " + assignedTo, form.getAction());
            else
                sendUpdateEmail(_issue, null, changeSummary.getTextChanges(), changeSummary.getSummary(), form.getComment(), url, "opened", form.getAction());

            return true;
        }


        public ActionURL getSuccessURL(IssuesForm issuesForm)
        {
            if (!StringUtils.isEmpty(issuesForm.getCallbackURL()))
            {
                ActionURL url = new ActionURL(issuesForm.getCallbackURL());
                url.addParameter("issueId", _issue.getIssueId());
                return url;
            }

            return new DetailsAction(_issue, getViewContext()).getURL();
        }


        public NavTree appendNavTrail(NavTree root)
        {
            IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(getViewContext().getContainer());
            return new ListAction(getViewContext()).appendNavTrail(root).addChild("Insert New " + names.singularName);
        }
    }


    private Issue setNewIssueDefaults(Issue issue) throws SQLException, ServletException
    {
        Map<Integer, HString> defaults = IssueManager.getAllDefaults(getContainer());

        issue.setArea(defaults.get(ISSUE_AREA));
        issue.setType(defaults.get(ISSUE_TYPE));
        issue.setMilestone(defaults.get(ISSUE_MILESTONE));
        issue.setString1(defaults.get(ISSUE_STRING1));
        issue.setString2(defaults.get(ISSUE_STRING2));
        issue.setString3(defaults.get(ISSUE_STRING3));
        issue.setString4(defaults.get(ISSUE_STRING4));
        issue.setString5(defaults.get(ISSUE_STRING5));

        HString priority = defaults.get(ISSUE_PRIORITY);
        issue.setPriority(null != priority ? priority.parseInt() : 3);

        return issue;
    }


    protected abstract class IssueUpdateAction extends FormViewAction<IssuesForm>
    {
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
                issue.beforeReOpen();

            Issue duplicateOf = null;
            if (ResolveAction.class.equals(form.getAction()) &&
                    issue.getResolution().getSource().equals("Duplicate") &&
                    issue.getDuplicate() != null &&
                    !issue.getDuplicate().equals(prevIssue.getDuplicate()))
            {
                if (issue.getDuplicate().intValue() == issue.getIssueId())
                {
                    errors.rejectValue("Duplicate", ERROR_MSG, "An issue may not be a duplicate of itself");
                    return false;
                }
                duplicateOf = IssueManager.getIssue(c, issue.getDuplicate().intValue());
                if (duplicateOf == null)
                {
                    errors.rejectValue("Duplicate", ERROR_MSG, "Duplicate issue '" + issue.getDuplicate().intValue() + "' not found");
                    return false;
                }
            }

            ChangeSummary changeSummary;

            DbScope scope = IssuesSchema.getInstance().getSchema().getScope();
            boolean ownsTransaction = !scope.isTransactionActive();
            try
            {
                if (ownsTransaction)
                    scope.beginTransaction();
                detailsUrl = new DetailsAction(issue, getViewContext()).getURL();

                if (ResolveAction.class.equals(form.getAction()))
                    issue.resolve(user);
                else if (InsertAction.class.equals(form.getAction()) || ReopenAction.class.equals(form.getAction()))
                    issue.open(c, user);
                else if (CloseAction.class.equals(form.getAction()))
                    issue.close(user);
                else
                    issue.change(user);

                changeSummary = createChangeSummary(issue, prevIssue, duplicateOf, user, form.getAction(), form.getComment(), getColumnCaptions(), getViewContext());
                IssueManager.saveIssue(user, c, issue);
                AttachmentService.get().addAttachments(user, changeSummary.getComment(), getAttachmentFileList());

                if (duplicateOf != null)
                {
                    HStringBuilder hsb = new HStringBuilder();
                    hsb.append("<em>Issue ").append(issue.getIssueId()).append(" marked as duplicate of this bug.</em>");
                    Issue.Comment dupComment = duplicateOf.addComment(user, hsb.toHString());
                    IssueManager.saveIssue(user, c, duplicateOf);
                }

                if (ownsTransaction)
                    scope.commitTransaction();
            }
            catch (IOException x)
            {
                String message = x.getMessage() == null ? x.toString() : x.getMessage();
                errors.addError(new ObjectError("main", new String[] {"Error"}, new Object[] {message}, message));
                return false;
            }
            finally
            {
                if (ownsTransaction)
                    scope.closeConnection();
            }

            // Send update email...
            //    ...if someone other than "created by" is closing a bug
            //    ...if someone other than "assigned to" is updating, reopening, or resolving a bug
            String change = ReopenAction.class.equals(form.getAction()) ? "reopened" : getActionName(form.getAction()) + "d";
            sendUpdateEmail(issue, prevIssue, changeSummary.getTextChanges(), changeSummary.getSummary(), form.getComment(), detailsUrl, change, form.getAction());
            return true;
        }

        public void validateCommand(IssuesForm form, Errors errors)
        {
            validateRequiredFields(form, errors);
            validateNotifyList(form.getBean(), form, errors);
        }

        public ActionURL getSuccessURL(IssuesForm form)
        {
            return form.getForwardURL();
        }
    }



    // SAME as AttachmentForm, just to demonstrate GuidString
    public static class _AttachmentForm
    {
        private GuidString _entityId = null;
        private String _name = null;


        public GuidString getEntityId()
        {
            return _entityId;
        }


        public void setEntityId(GuidString entityId)
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
        public IssueAttachmentParent(Container c, GuidString entityId)
        {
            setContainer(c.getId());
            setEntityId(entityId.toString());
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class UpdateAction extends IssueUpdateAction
    {
        public ModelAndView getView(IssuesForm form, boolean reshow, BindException errors) throws Exception
        {
            int issueId = form.getIssueId();
            _issue = getIssue(issueId);
            if (_issue == null)
                HttpView.throwNotFound();

            Issue prevIssue = (Issue)_issue.clone();
            User user = getUser();
            requiresUpdatePermission(user, _issue);

            IssuePage page = new IssuePage();
            JspView v = new JspView<IssuePage>(IssuesController.class, "updateView.jsp", page);

            IssueManager.CustomColumnConfiguration ccc = getCustomColumnConfiguration();

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
                    .addChild("(update) " + _issue.getTitle().getSource());
        }
    }


    private Set<String> getEditableFields(Class<? extends Controller> action, IssueManager.CustomColumnConfiguration ccc)
    {
        final Set<String> editable = new HashSet<String>(20);

        editable.add("title");
        editable.add("assignedTo");
        editable.add("type");
        editable.add("area");
        editable.add("priority");
        editable.add("milestone");
        editable.add("comments");
        editable.add("attachments");

        for (String columnName : ccc.getColumnCaptions().keySet())
            editable.add(columnName);

        //if (!"insert".equals(action))
        editable.add("notifyList");

        if (ResolveAction.class.equals(action))
        {
            editable.add("resolution");
            editable.add("duplicate");
        }

        return editable;
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class ResolveAction extends IssueUpdateAction
    {
        public ModelAndView getView(IssuesForm form, boolean reshow, BindException errors) throws Exception
        {
            int issueId = form.getIssueId();
            _issue = getIssue(issueId);
            if (null == _issue)
                HttpView.throwNotFound();

            Issue prevIssue = (Issue)_issue.clone();
            User user = getUser();
            requiresUpdatePermission(user, _issue);

            _issue.beforeResolve(user);

            if (_issue.getResolution().isEmpty())
            {
                Map<Integer, HString> defaults = IssueManager.getAllDefaults(getContainer());

                HString resolution = defaults.get(ISSUE_RESOLUTION);

                if (resolution != null && !resolution.isEmpty())
                    _issue.setResolution(resolution);
            }

            IssuePage page = new IssuePage();
            JspView v = new JspView<IssuePage>(IssuesController.class, "updateView.jsp", page);

            IssueManager.CustomColumnConfiguration ccc = getCustomColumnConfiguration();

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
            IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(getViewContext().getContainer());
            return (new DetailsAction(_issue, getViewContext()).appendNavTrail(root)).addChild("Resolve " + names.singularName);
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class CloseAction extends IssueUpdateAction
    {
        public ModelAndView getView(IssuesForm form, boolean reshow, BindException errors) throws Exception
        {
            int issueId = form.getIssueId();
            _issue = getIssue(issueId);
            if (null == _issue)
                HttpView.throwNotFound();

            Issue prevIssue = (Issue)_issue.clone();
            User user = getUser();
            requiresUpdatePermission(user, _issue);

            _issue.close(user);

            IssuePage page = new IssuePage();
            JspView v = new JspView<IssuePage>(IssuesController.class, "updateView.jsp",page);

            IssueManager.CustomColumnConfiguration ccc = getCustomColumnConfiguration();

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
            IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(getViewContext().getContainer());
            return (new DetailsAction(_issue, getViewContext()).appendNavTrail(root)).addChild("Close " + names.singularName);
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class ReopenAction extends IssueUpdateAction
    {
        public ModelAndView getView(IssuesForm form, boolean reshow, BindException errors) throws Exception
        {
            int issueId = form.getIssueId();
            _issue = getIssue(issueId);
            if (_issue == null)
                HttpView.throwNotFound();

            Issue prevIssue = (Issue)_issue.clone();

            User user = getUser();
            requiresUpdatePermission(user, _issue);

            _issue.beforeReOpen();
            _issue.open(getContainer(), user);

            IssuePage page = new IssuePage();
            JspView v = new JspView<IssuePage>(IssuesController.class, "updateView.jsp",page);

            IssueManager.CustomColumnConfiguration ccc = getCustomColumnConfiguration();

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
            IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(getViewContext().getContainer());
            return (new DetailsAction(_issue, getViewContext()).appendNavTrail(root)).addChild("Reopen " + names.singularName);
        }
    }
    
    private void validateRequiredFields(IssuesForm form, Errors errors)
    {
        HString requiredFields = IssueManager.getRequiredIssueFields(getContainer());
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
        if (newFields.containsKey("comment"))
            validateRequired("comment", newFields.get("comment"), requiredFields, requiredErrors);

        // When resolving Duplicate, the 'duplicate' field should be set.
        if ("Duplicate".equals(newFields.get("resolution")))
            validateRequired("duplicate", newFields.get("duplicate"), new HString("duplicate"), requiredErrors);

        errors.addAllErrors(requiredErrors);
    }


    private void validateRequired(String columnName, String value, HString requiredFields, Errors errors)
    {
        if (requiredFields != null)
        {
            if (requiredFields.indexOf(columnName) != -1)
            {
                if (StringUtils.isEmpty(value))
                {
                    final IssueManager.CustomColumnConfiguration ccc = IssueManager.getCustomColumnConfiguration(getContainer());
                    String name = null;
                    if (ccc.getColumnCaptions().containsKey(columnName))
                        name = ccc.getColumnCaptions().get(columnName);
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
    

    private void validateNotifyList(Issue issue, IssuesForm form, Errors errors)
    {
        String[] rawEmails = _toString(form.getNotifyList()).split("\n");
        List<String> invalidEmails = new ArrayList<String>();
        List<ValidEmail> emails = org.labkey.api.security.SecurityManager.normalizeEmails(rawEmails, invalidEmails);

        StringBuilder message = new StringBuilder();

        for (String rawEmail : invalidEmails)
        {
            rawEmail = rawEmail.trim();
            // Ignore lines of all whitespace, otherwise show an error.
            if (!"".equals(rawEmail))
            {
                message.append("Failed to add user ").append(rawEmail).append(": Invalid email address");
                errors.rejectValue("notifyList","Error",new Object[] {message.toString()}, message.toString());
            }
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
    public class CompleteUserAction extends AjaxCompletionAction<CompleteUserForm>
    {
        public List<AjaxCompletion> getCompletions(CompleteUserForm form, BindException errors) throws Exception
        {
            return UserManager.getAjaxCompletions(form.getPrefix(), getViewContext());
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


    private void sendUpdateEmail(Issue issue, Issue prevIssue, String fieldChanges, String summary, String comment, ActionURL detailsURL, String change, Class<? extends Controller> action) throws ServletException
    {
        final String[] allAddresses = getEmailAddresses(issue, prevIssue, action);
        for (String to : allAddresses)
        {
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
                    template.init(issue, detailsURL, change, comment, fieldChanges);

                    m.setSubject(template.renderSubject(getContainer()));
                    m.setHeader("References", references);
                    String body = template.renderBody(getContainer());
                    m.setText(body);

                    MailHelper.send(m, getUser(), getContainer());
                }
            }
            catch (Exception e)
            {
                _log.error("sendUpdateEmail", e);
            }
        }
    }

    /**
     * Builds the list of email addresses for notification based on the user
     * preferences and the explicit notification list.
     */
    private String[] getEmailAddresses(Issue issue, Issue prevIssue, Class<? extends Controller> action) throws ServletException
    {
        final Set<String> emailAddresses = new HashSet<String>();
        final Container c = getContainer();
        int assignedToPref = IssueManager.getUserEmailPreferences(c, issue.getAssignedTo());
        int assignedToPrev = prevIssue != null && prevIssue.getAssignedTo() != null ? prevIssue.getAssignedTo() : 0;
        int assignedToPrevPref = assignedToPrev != 0 ? IssueManager.getUserEmailPreferences(c, prevIssue.getAssignedTo()) : 0;
        int createdByPref = IssueManager.getUserEmailPreferences(c, issue.getCreatedBy());

        if (InsertAction.class.equals(action))
        {
            if ((assignedToPref & IssueManager.NOTIFY_ASSIGNEDTO_OPEN) != 0)
                emailAddresses.add(UserManager.getEmailForId(issue.getAssignedTo()));
        }
        else
        {
            if ((assignedToPref & IssueManager.NOTIFY_ASSIGNEDTO_UPDATE) != 0)
                emailAddresses.add(UserManager.getEmailForId(issue.getAssignedTo()));

            if ((assignedToPrevPref & IssueManager.NOTIFY_ASSIGNEDTO_UPDATE) != 0)
                emailAddresses.add(UserManager.getEmailForId(prevIssue.getAssignedTo()));

            if ((createdByPref & IssueManager.NOTIFY_CREATED_UPDATE) != 0)
                emailAddresses.add(UserManager.getEmailForId(issue.getCreatedBy()));
        }

        // add any explicit notification list addresses
        final HString notify = issue.getNotifyList();

        if (notify != null)
        {
            StringTokenizer tokenizer = new StringTokenizer(notify.getSource(), ";\n\r\t");

            while (tokenizer.hasMoreTokens())
            {
                emailAddresses.add((String)tokenizer.nextElement());
            }
        }

        final String current = getUser().getEmail();

        boolean selfSpam = !((IssueManager.NOTIFY_SELF_SPAM & IssueManager.getUserEmailPreferences(c, getUser().getUserId())) == 0);
        if (selfSpam)
            emailAddresses.add(current);
        else
            emailAddresses.remove(current);

        return emailAddresses.toArray(new String[emailAddresses.size()]);
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class EmailPrefsAction extends FormViewAction<EmailPrefsForm>
    {
        String _message = null;

        public ModelAndView getView(EmailPrefsForm form, boolean reshow, BindException errors) throws Exception
        {
            if (getViewContext().getUser().isGuest())
                HttpView.throwUnauthorized();

            int emailPrefs = IssueManager.getUserEmailPreferences(getContainer(), getUser().getUserId());
            int issueId = form.getIssueId() == null ? 0 : form.getIssueId().intValue();
            return new JspView<EmailPrefsBean>(IssuesController.class, "emailPreferences.jsp",
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


    public static final String REQUIRED_FIELDS_COLUMNS = "Title,AssignedTo,Type,Area,Priority,Milestone,NotifyList";
    public static final String DEFAULT_REQUIRED_FIELDS = "title;assignedto";


    @RequiresPermissionClass(AdminPermission.class)
    public class AdminAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            // TODO: This hack ensures that priority & resolution option defaults get populated if first reference is the admin page.  Fix this.
            IssuePage page = new IssuePage()
            {
                public void _jspService(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException
                {
                }
            };
            page.getPriorityOptions(getContainer());
            page.getResolutionOptions(getContainer());
            // </HACK>

            return new AdminView(getContainer(), getCustomColumnConfiguration());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(getViewContext().getContainer());
            return (new ListAction(getViewContext())).appendNavTrail(root).addChild(names.pluralName + " Admin Page", getUrl());
        }

        public ActionURL getUrl()
        {
            return issueURL(AdminAction.class);
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
    public class AddKeywordAction extends AdminFormAction
    {
        public boolean handlePost(AdminForm form, BindException errors) throws Exception
        {
            int type = form.getType();
            HString keyword = form.getKeyword();

            if (ISSUE_PRIORITY == type)
            {
                try
                {
                    Integer.parseInt(keyword.getSource());
                }
                catch (NumberFormatException e)
                {
                    errors.reject(ERROR_MSG, "Priority must be an integer");
                    return false;
                }
            }

            IssueManager.addKeyword(getContainer(), type, keyword);
            return true;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class DeleteKeywordAction extends AdminFormAction
    {
        public boolean handlePost(AdminForm form, BindException errors) throws Exception
        {
            IssueManager.deleteKeyword(getContainer(), form.getType(), form.getKeyword());
            return true;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class SetKeywordDefaultAction extends AdminFormAction
    {
        public boolean handlePost(AdminForm form, BindException errors) throws Exception
        {
            IssueManager.setKeywordDefault(getContainer(), form.getType(), form.getKeyword());
            return true;
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class ClearKeywordDefaultAction extends AdminFormAction
    {
        public boolean handlePost(AdminForm form, BindException errors) throws Exception
        {
            IssueManager.clearKeywordDefault(getContainer(), form.getType());
            return true;
        }
    }

    public static class EntryTypeNamesForm
    {
        public static enum ParamNames
        {
            entrySingularName,
            entryPluralName
        }

        private HString _entrySingularName;
        private HString _entryPluralName;

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
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class SetEntryTypeNames extends FormHandlerAction<EntryTypeNamesForm>
    {
        public void validateCommand(EntryTypeNamesForm form, Errors errors)
        {
            if (form.getEntrySingularName().trimToEmpty().length() == 0)
                errors.reject(EntryTypeNamesForm.ParamNames.entrySingularName.name(), "You must specify a value for the entry type singular name!");
            if (form.getEntryPluralName().trimToEmpty().length() == 0)
                errors.reject(EntryTypeNamesForm.ParamNames.entryPluralName.name(), "You must specify a value for the entry type plural name!");
        }

        public boolean handlePost(EntryTypeNamesForm form, BindException errors) throws Exception
        {
            IssueManager.EntryTypeNames names = new IssueManager.EntryTypeNames();
            
            names.singularName = form.getEntrySingularName();
            names.pluralName = form.getEntryPluralName();

            IssueManager.saveEntryTypeNames(getViewContext().getContainer(), names);
            return true;
        }

        public ActionURL getSuccessURL(EntryTypeNamesForm form)
        {
            return issueURL(AdminAction.class);
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class SetAssignedToGroupAction extends FormHandlerAction<AssignedToGroupForm>
    {
        private Group _group = null;

        public void validateCommand(AssignedToGroupForm form, Errors errors)
        {
            if (form.getAssignedToMethod().equals("ProjectMembers"))
            {
                if (form.getAssignedToGroup() != 0)
                    errors.reject("assignedToGroup", "Project members setting shouldn't include a group!");
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
        }

        public boolean handlePost(AssignedToGroupForm form, BindException errors) throws Exception
        {
            IssueManager.saveAssignedToGroup(getContainer(), _group);
            return true;
        }

        public ActionURL getSuccessURL(AssignedToGroupForm form)
        {
            return issueURL(AdminAction.class);
        }
    }


    public static class AssignedToGroupForm
    {
        private String _assignedToMethod = null;
        private int _assignedToGroup = 0;

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
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class RssAction extends SimpleViewAction
    {
        @Override
        public void checkPermissions() throws TermsOfUseException, UnauthorizedException
        {
            checkPermissionsBasicAuth();
        }

        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            getPageConfig().setTemplate(PageConfig.Template.None);
            ResultSet rs = null;
            try
            {
                DataRegion r = new DataRegion();
                TableInfo tinfo = IssuesSchema.getInstance().getTableInfoIssues();
                List<ColumnInfo> cols = tinfo.getColumns("IssueId,Created,CreatedBy,Area,Type,Title,AssignedTo,Priority,Status,Milestone");
                r.addColumns(cols);

                rs = r.getResultSet(new RenderContext(getViewContext()));
                ObjectFactory f = ObjectFactory.Registry.getFactory(Issue.class);
                Issue[] issues = (Issue[]) f.handleArray(rs);

                ActionURL url = getDetailsURL(getContainer(), 1, isPrint());
                String filteredURLString = PageFlowUtil.filter(url);
                String detailsURLString = filteredURLString.substring(0, filteredURLString.length() - 1);

                WebPartView v = new JspView<RssBean>("/org/labkey/issue/rss.jsp", new RssBean(issues, detailsURLString));
                v.setFrame(WebPartView.FrameType.NONE);

                return v;
            }
            catch (SQLException x)
            {
                x.printStackTrace();
                throw new ServletException(x);
            }
            finally
            {
                ResultSetUtil.close(rs);
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


    @RequiresPermissionClass(AdminPermission.class)
    public class PurgeAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            if (!getUser().isAdministrator())   // GLOBAL
                HttpView.throwUnauthorized();
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
                    Issue issue = getIssue(id);
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
            url.addParameter(".lastFilter", "true");
            return HttpView.redirect(url);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class SearchAction extends SimpleViewAction
    {
        private String _status;

        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            Container c = getContainer();
            Object q = getProperty("q", "");
            String searchTerm = (q instanceof String) ? (String)q : StringUtils.join((String[])q," ");

            _status = (String)getProperty("status");

            getPageConfig().setHelpTopic(new HelpTopic("luceneSearch"));

            return new SearchResultsView(c, searchTerm, _status, isPrint());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            String title = "Search " + (null != _status ? _status + " " : "") + "Issues";
            return new ListAction(getViewContext()).appendNavTrail(root).addChild(title);
        }
    }


    public static class SearchResultsView extends JspView<SearchResultsView>
    {
        public Container _c;
        public String _query;
        public boolean _print;
        public String _status;
        
        SearchResultsView(Container c, String query, String status, boolean isPrint)
        {
            super(IssuesController.class, "search.jsp", null);
            _c = c;
            _query = query;
            _status = status;
            _print = isPrint;
            setModelBean(this);
        }
    }


    static String _toString(Object a)
    {
        return null == a ? "" : a.toString();
    }


    static void _appendChange(StringBuilder sbHTML, StringBuilder sbText, String field, HString from, HString to)
    {
        from = from == null ? HString.EMPTY : from;
        to = to == null ? HString.EMPTY : to;
        if (!from.equals(to))
        {
            sbText.append(field);
            sbText.append(" changed from ");
            sbText.append(HString.EMPTY.equals(from) ? "blank" : from.getSource());
            sbText.append(" to ");
            sbText.append(HString.EMPTY.equals(to) ? "blank" : to.getSource());
            sbText.append("\n");
            HString encFrom = PageFlowUtil.filter(from);
            HString encTo = PageFlowUtil.filter(to);
            sbHTML.append("<tr><td>").append(field).append("</td><td>").append(encFrom).append("</td><td>&raquo;</td><td>").append(encTo).append("</td></tr>\n");
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

    static ChangeSummary createChangeSummary(Issue issue, Issue previous, Issue duplicateOf, User user, Class<? extends Controller> action, String comment, Map<String, String> customColumns, ViewContext context)
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
            // issueChanges is not defined yet, but it leaves things flexible
            sbHTMLChanges.append("<table class=issues-Changes>");
            _appendChange(sbHTMLChanges, sbTextChanges, "Title", previous.getTitle(), issue.getTitle());
            _appendChange(sbHTMLChanges, sbTextChanges, "Status", previous.getStatus(), issue.getStatus());
            _appendChange(sbHTMLChanges, sbTextChanges, "Assigned To", previous.getAssignedToName(context), issue.getAssignedToName(context));
            _appendChange(sbHTMLChanges, sbTextChanges, "Notify", previous.getNotifyList(), issue.getNotifyList());
            _appendChange(sbHTMLChanges, sbTextChanges, "Type", previous.getType(), issue.getType());
            _appendChange(sbHTMLChanges, sbTextChanges, "Area", previous.getArea(), issue.getArea());
            _appendChange(sbHTMLChanges, sbTextChanges, "Priority", HString.valueOf(previous.getPriority()), HString.valueOf(issue.getPriority()));
            _appendChange(sbHTMLChanges, sbTextChanges, "Milestone", previous.getMilestone(), issue.getMilestone());

            _appendCustomColumnChange(sbHTMLChanges, sbTextChanges, "int1", HString.valueOf(previous.getInt1()), HString.valueOf(issue.getInt1()), customColumns);
            _appendCustomColumnChange(sbHTMLChanges, sbTextChanges, "int2", HString.valueOf(previous.getInt2()), HString.valueOf(issue.getInt2()), customColumns);
            _appendCustomColumnChange(sbHTMLChanges, sbTextChanges, "string1", previous.getString1(), issue.getString1(), customColumns);
            _appendCustomColumnChange(sbHTMLChanges, sbTextChanges, "string2", previous.getString2(), issue.getString2(), customColumns);
            _appendCustomColumnChange(sbHTMLChanges, sbTextChanges, "string3", previous.getString3(), issue.getString3(), customColumns);
            _appendCustomColumnChange(sbHTMLChanges, sbTextChanges, "string4", previous.getString4(), issue.getString4(), customColumns);
            _appendCustomColumnChange(sbHTMLChanges, sbTextChanges, "string5", previous.getString5(), issue.getString5(), customColumns);

            sbHTMLChanges.append("</table>\n");
        }

        //why we are wrapping issue comments in divs???
        HStringBuilder formattedComment = new HStringBuilder();
        formattedComment.append("<div class=\"wiki\">");
        formattedComment.append(sbHTMLChanges);
        //render issues as plain text with links
        WikiService wikiService = ServiceRegistry.get().getService(WikiService.class);
        if(null != wikiService)
        {
            WikiRenderer w = wikiService.getRenderer(WikiRendererType.TEXT_WITH_LINKS);
            formattedComment.append(w.format(comment).getHtml());
        }
        else
            formattedComment.append(comment);

        formattedComment.append("</div>");

        return new ChangeSummary(issue.addComment(user, formattedComment.toHString()), sbTextChanges.toString(), summary);
    }

    private static void _appendCustomColumnChange(StringBuilder sbHtml, StringBuilder sbText, String field, HString from, HString to, Map<String, String> columnCaptions)
    {
        String caption = columnCaptions.get(field);

        if (null != caption)
            _appendChange(sbHtml, sbText, caption, from, to);
    }


    //
    // VIEWS
    //
    public static class AdminView extends JspView<AdminBean>
    {
        public AdminView(Container c, IssueManager.CustomColumnConfiguration ccc)
        {
            super("/org/labkey/issue/admin.jsp");

            KeywordAdminView keywordView = new KeywordAdminView(c, ccc);
            keywordView.addKeyword("Type", ISSUE_TYPE);
            keywordView.addKeyword("Area", ISSUE_AREA);
            keywordView.addKeyword("Priority", ISSUE_PRIORITY);
            keywordView.addKeyword("Milestone", ISSUE_MILESTONE);
            keywordView.addKeyword("Resolution", ISSUE_RESOLUTION);
            keywordView.addCustomColumn("string1", ISSUE_STRING1);
            keywordView.addCustomColumn("string2", ISSUE_STRING2);
            keywordView.addCustomColumn("string3", ISSUE_STRING3);
            keywordView.addCustomColumn("string4", ISSUE_STRING4);
            keywordView.addCustomColumn("string5", ISSUE_STRING5);

            List<String> columnNames = new ArrayList<String>();
            columnNames.addAll(Arrays.asList(REQUIRED_FIELDS_COLUMNS.split(",")));
            columnNames.addAll(IssuesTable.getCustomColumnCaptions(c).keySet());
            List<ColumnInfo> cols = IssuesSchema.getInstance().getTableInfoIssues().getColumns(columnNames.toArray(new String[columnNames.size()]));

            IssuesPreference ipb = new IssuesPreference(cols, IssueManager.getRequiredIssueFields(c), IssueManager.getEntryTypeNames(c));

            AdminBean bean = new AdminBean();

            bean.ccc = ccc;
            bean.keywordView = keywordView;
            bean.requiredFieldsView = new JspView<IssuesPreference>("/org/labkey/issue/requiredFields.jsp", ipb);
            bean.entryTypeNames = IssueManager.getEntryTypeNames(c);
            bean.assignedToGroup = IssueManager.getAssignedToGroup(c);
            setModelBean(bean);
        }
    }


    public static class AdminBean
    {
        public IssueManager.CustomColumnConfiguration ccc;
        public KeywordAdminView keywordView;
        public JspView<IssuesPreference> requiredFieldsView;
        public IssueManager.EntryTypeNames entryTypeNames;
        public Group assignedToGroup;
    }


    // Renders the pickers for all keywords; would be nice to render each picker independently, but that makes it hard to align
    // all the top and bottom sections with each other.
    public static class KeywordAdminView extends JspView<List<KeywordPicker>>
    {
        private Container _c;
        private List<KeywordPicker> _keywordPickers = new ArrayList<KeywordPicker>(5);
        public IssueManager.CustomColumnConfiguration _ccc;

        public KeywordAdminView(Container c, IssueManager.CustomColumnConfiguration ccc)
        {
            super("/org/labkey/issue/keywordAdmin.jsp");
            setModelBean(_keywordPickers);
            _c = c;
            _ccc = ccc;
        }

        // Add keyword admin for custom columns with column picker enabled
        private void addCustomColumn(String tableColumn, int type)
        {
            if (_ccc.getPickListColumns().contains(tableColumn))
            {
                String caption = _ccc.getColumnCaptions().get(tableColumn);
                addKeyword(caption, type);
            }
        }

        private void addKeyword(String name, int type)
        {
            _keywordPickers.add(new KeywordPicker(_c, name, type));
        }
    }


    public static class KeywordPicker
    {
        public String name;
        public String plural;
        public int type;
        public IssueManager.Keyword[] keywords;

        KeywordPicker(Container c, String name, int type)
        {
            this.name = name;
            this.plural = name.endsWith("y") ? name.substring(0, name.length() - 1) + "ies" : name + "s";
            this.type = type;
            this.keywords = IssueManager.getKeywords(c.getId(), type);
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
        private HString keyword;


        public int getType()
        {
            return type;
        }


        public void setType(int type)
        {
            this.type = type;
        }


        public HString getKeyword()
        {
            return keyword;
        }


        public void setKeyword(HString keyword)
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
            Map<String, Class> map = new LinkedHashMap<String, Class>();
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
                title = "Issues Summary";
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


    public static class TestCase extends junit.framework.TestCase
    {
        public TestCase()
        {
            super("IssueController");
        }


        public TestCase(String name)
        {
            super(name);
        }


        public void testIssue()
                throws SQLException, ServletException
        {
        }


        public static Test suite()
        {
            return new TestSuite(TestCase.class);
        }
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


    /**
     * Throw an exception if user does not have permission to update issue
     */
    private void requiresUpdatePermission(User user, Issue issue)
            throws ServletException
    {
        if (!hasUpdatePermission(user, issue))
            HttpView.throwUnauthorized();
    }


    public static class ListForm
    {
        private QuerySettings _settings;
        private boolean _export;
        private ActionURL _customizeURL;
        private Map<String, CustomView> _views;
        private String dataRegionSelectionKey = null;
        private Map<String, String> _reports;

        public boolean getExport()
        {
            return _export;
        }

        public void setExport(boolean export)
        {
            _export = export;
        }

        public ActionURL getCustomizeURL() {return _customizeURL;}
        public void setCustomizeURL(ActionURL url) {_customizeURL = url;}
        public Map<String, CustomView> getViews() {return _views;}
        public void setViews(Map<String, CustomView> views) {_views = views;}
        public QuerySettings getQuerySettings()
        {
            return _settings;
        }
        public void setQuerySettings(QuerySettings settings)
        {
            _settings = settings;
        }

        public String getDataRegionSelectionKey()
        {
            return dataRegionSelectionKey;
        }

        public void setDataRegionSelectionKey(String dataRegionSelectionKey)
        {
            this.dataRegionSelectionKey = dataRegionSelectionKey;
        }

        public Map<String, String> getReports()
        {
            return _reports;
        }

        public void setReports(Map<String, String> reports)
        {
            _reports = reports;
        }
    }

    public static class IssuesPreference
    {
        private List<ColumnInfo> _columns;
        private HString _requiredFields;
        private IssueManager.EntryTypeNames _entryTypeNames;

        public IssuesPreference(List<ColumnInfo> columns, HString requiredFields, IssueManager.EntryTypeNames typeNames)
        {
            _columns = columns;
            _requiredFields = requiredFields;
            _entryTypeNames = typeNames;
        }

        public List<ColumnInfo> getColumns(){return _columns;}
        public HString getRequiredFields(){return _requiredFields;}
        public IssueManager.EntryTypeNames getEntryTypeNames() {return _entryTypeNames;}
    }


    public static class IssuePreferenceForm
    {
        private HString[] _requiredFields = new HString[0];

        public void setRequiredFields(HString[] requiredFields){_requiredFields = requiredFields;}
        public HString[] getRequiredFields(){return _requiredFields;}
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


    public static class RequiredError extends FieldError
    {
        RequiredError(String field, String display)
        {
            super("issue", field, "", true, new String[] {"NullError"}, new Object[] {display}, "Error: The field: " + display + " is required");
        }
    }
}
