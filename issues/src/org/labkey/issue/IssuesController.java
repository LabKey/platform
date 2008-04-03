package org.labkey.issue;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.apache.beehive.netui.pageflow.FormData;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.action.*;
import org.labkey.api.data.*;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.*;
import org.labkey.api.security.*;
import org.labkey.api.util.*;
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


    static DefaultActionResolver _actionResolver = new DefaultActionResolver(IssuesController.class);

    public IssuesController() throws Exception
    {
        super();
        setActionResolver(_actionResolver);
    }


    public PageConfig defaultPageConfig()
    {
        PageConfig config = super.defaultPageConfig();
        config.setHelpTopic(new HelpTopic(helpTopic, HelpTopic.Area.SERVER));
        return config;
    }


    private Issue getIssue(int issueId) throws SQLException
    {
        return IssueManager.getIssue(openSession(), getContainer(), issueId);
    }


    private ActionURL issueURL(String action)
    {
        return new ActionURL("issues", action, getContainer());
    }


    public static ActionURL issueURL(Container c, String action)
    {
        return new ActionURL("issues", action, c);
    }


    /**
     * This method represents the point of entry into the pageflow
     */
    @RequiresPermission(ACL.PERM_READ)
    public class BeginAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return HttpView.redirect(getListUrl(getContainer()));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Issues", getListUrl(getContainer()));
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


    @RequiresPermission(ACL.PERM_ADMIN)
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


    @RequiresPermission(ACL.PERM_ADMIN)
    public class UpdateRequiredFieldsAction extends FormHandlerAction<IssuePreferenceForm>
    {
        public boolean handlePost(IssuePreferenceForm form, BindException errors) throws Exception
        {
            final StringBuffer sb = new StringBuffer();
            if (form.getRequiredFields().length > 0)
            {
                String sep = "";
                for (String field : form.getRequiredFields())
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


    private static ActionURL getListUrl(Container c)
    {
        ActionURL url = new ActionURL("issues", "list", c);
        url.addParameter(DataRegion.LAST_FILTER_PARAM, "true");
        return url;
    }


    private static final String ISSUES_QUERY = "Issues";
    private HttpView getIssuesView(IssuesController.ListForm form) throws SQLException, ServletException
    {
        UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), IssuesQuerySchema.SCHEMA_NAME);
        QuerySettings settings = new QuerySettings(getViewContext().getActionURL(), ISSUES_QUERY);
        settings.setSchemaName(schema.getSchemaName());
        settings.setQueryName(ISSUES_QUERY);
        form.setQuerySettings(settings);
        IssuesQueryView queryView = new IssuesQueryView(getViewContext(), schema, settings);

        // add the header for buttons and views
        QueryDefinition qd = schema.getQueryDefForTable(ISSUES_QUERY);
        Map<String, CustomView> views = qd.getCustomViews(getUser(), getViewContext().getRequest());
        // don't include a customized default view in the list
        if (views.containsKey(null))
            views.remove(null);

        form.setCustomizeURL(queryView.getCustomizeURL());
        form.setViews(views);
        form.setReports(queryView.getReports());
        
        VBox box = new VBox();

        box.addView(new JspView<IssuesController.ListForm>("/org/labkey/issue/list.jsp", form));
        box.addView(queryView);
        return box;
    }


    private ResultSet getIssuesResultSet() throws IOException, SQLException, ServletException
    {
        UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), IssuesQuerySchema.SCHEMA_NAME);
        QuerySettings settings = schema.getSettings(getViewContext().getActionURL(), ISSUES_QUERY);
        settings.setQueryName(ISSUES_QUERY);

        IssuesQueryView queryView = new IssuesQueryView(getViewContext(), schema, settings);

        return queryView.getResultSet();
    }


    @RequiresPermission(ACL.PERM_READ)
    public class ListAction extends SimpleViewAction<IssuesController.ListForm>
    {
        public ModelAndView getView(IssuesController.ListForm form, BindException errors) throws Exception
        {
            // convert AssignedTo/Email to AssignedTo/DisplayName: old bookmarks
            // reference Email, which is no longer displayed.
            ActionURL url = getViewContext().cloneActionURL();
            String[] emailFilters = url.getKeysByPrefix(ISSUES_QUERY + ".AssignedTo/Email");
            if (emailFilters != null && emailFilters.length > 0)
            {
                for (String emailFilter : emailFilters)
                    url.deleteParameter(emailFilter);
                return HttpView.redirect(url);
            }

            getPageConfig().setRssProperties(new RssAction().getUrl(), "Issues");
            HttpView view = getIssuesView(form);
            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Issues List", getURL());
        }

        public ActionURL getURL()
        {
            return issueURL("list").addParameter(".lastFilter","true");
        }
    }


    @RequiresPermission(ACL.PERM_READ)
    public class ExportTsvAction extends SimpleViewAction<QueryForm>
    {
        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            getPageConfig().setTemplate(PageConfig.Template.None);
            QueryView view = QueryView.create(form);
            final TSVGridWriter writer = view.getTsvWriter();
            return new HttpView()
            {
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


    @RequiresPermission(ACL.PERM_READ)
    public class DetailsAction extends SimpleViewAction<IssueIdForm>
    {
        Issue _issue = null;

        public DetailsAction()
        {
        }

        public DetailsAction(Issue issue)
        {
            _issue = issue;
        }

        public ModelAndView getView(IssueIdForm form, BindException errors) throws Exception
        {
            int issueId = form.getIssueId();
            _issue = getIssue(issueId);

            if (null == _issue)
            {
                HttpView.throwNotFound("Unable to find issue " + form.getIssueId());
                return null;
            }

            IssuePage page = new IssuePage();
            JspView v = new JspView<IssuePage>(IssuesController.class, "detailView.jsp", page);

            page.setIssue(_issue);
            page.setCustomColumnConfiguration(getCustomColumnConfiguration());
            //pass user's update perms to jsp page to determine whether to show notify list
            page.setUserHasUpdatePermissions(hasUpdatePermission(getUser(), _issue));
            page.setRequiredFields(IssueManager.getRequiredIssueFields(getContainer()));

            getPageConfig().setTitle("" + _issue.getIssueId() + " : " + _issue.getTitle());
            return v;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return new ListAction().appendNavTrail(root)
                    .addChild("Detail -- " + _issue.getIssueId(), getURL());
        }

        public ActionURL getURL()
        {
            return issueURL("details").addParameter("issueId", _issue.getIssueId());
        }

        public ActionURL getURL(HttpServletRequest request)
        {
            return issueURL("details").addParameter("issueId", _issue.getIssueId());
        }
    }


    @RequiresPermission(ACL.PERM_READ)
    public class DetailsListAction extends SimpleViewAction<ListForm>
    {
        public ModelAndView getView(IssuesController.ListForm listForm, BindException errors) throws Exception
        {
            // convert AssignedTo/Email to AssignedTo/DisplayName: old bookmarks
            // reference Email, which is no longer displayed.
            ActionURL url = getViewContext().cloneActionURL();
            String[] emailFilters = url.getKeysByPrefix(ISSUES_QUERY + ".AssignedTo/Email");
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
            return new ListAction().appendNavTrail(root).addChild("Issue Details");
        }
    }


    @RequiresPermission(ACL.PERM_INSERT)
    public class InsertAction extends FormViewAction<IssuesForm>
    {
        Issue _issue = null;

        public ModelAndView getView(IssuesForm form, boolean reshow, BindException errors) throws Exception
        {
            _issue = reshow ? form.getBean() : new Issue();

            if (_issue.getAssignedTo() != null)
            {
                User user = UserManager.getUser(_issue.getAssignedTo());
                if (user != null)
                {
                    _issue.setAssignedTo(user.getUserId());
                }
            }

            _issue.Open(getContainer(), getUser());
            setNewIssueDefaults(_issue);

            IssuePage page = new IssuePage();
            JspView v = new JspView<IssuePage>(IssuesController.class, "updateView.jsp",page);

            IssueManager.CustomColumnConfiguration ccc = getCustomColumnConfiguration();

            page.setAction("insert");
            page.setIssue(_issue);
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
            _issue.Open(c, user);
            validateNotifyList(_issue, form, errors);

            try
            {
                // for new issues, the original is always the default.
                Issue orig = new Issue();
                orig.Open(getContainer(), getUser());

                addComment(_issue, orig, user, form.getAction(), form.getComment(), getColumnCaptions(), getViewContext());
                IssueManager.saveIssue(openSession(), user, c, _issue);
            }
            catch (Exception x)
            {
                Throwable ex = x.getCause() == null ? x : x.getCause();
                String error = ex.getMessage();
                _log.debug("IssuesContoller.doInsert", x);
                _issue.Open(c, user);

                errors.addError(new ObjectError("form", null, null, error));
                return false;
            }

            ActionURL url = new DetailsAction(_issue).getURL();

            final String assignedTo = UserManager.getDisplayName(_issue.getAssignedTo(), getViewContext());
            if (assignedTo != null)
                sendUpdateEmail(_issue, url, "opened and assigned to " + assignedTo, form.getAction());
            else
                sendUpdateEmail(_issue, url, "opened", form.getAction());

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
            
            ActionURL forwardURL = new DetailsAction(_issue).getURL();
            return forwardURL;
        }


        public NavTree appendNavTrail(NavTree root)
        {
            return new ListAction().appendNavTrail(root).addChild("Insert New Issue");
        }
    }


    private Issue setNewIssueDefaults(Issue issue) throws SQLException, ServletException
    {
        Map<Integer, String> defaults = IssueManager.getAllDefaults(getContainer());

        issue.setArea(defaults.get(ISSUE_AREA));
        issue.setType(defaults.get(ISSUE_TYPE));
        issue.setMilestone(defaults.get(ISSUE_MILESTONE));
        issue.setString1(defaults.get(ISSUE_STRING1));
        issue.setString2(defaults.get(ISSUE_STRING2));

        String priority = defaults.get(ISSUE_PRIORITY);
        issue.setPriority(null != priority ? Integer.parseInt(defaults.get(ISSUE_PRIORITY)) : 3);

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
            requiresUpdatePermission(user, issue);
            ActionURL detailsUrl;

            try
            {
                detailsUrl = new DetailsAction(issue).getURL();

                if ("resolve".equals(form.getAction()))
                    issue.Resolve(user);
                else if ("open".equals(form.getAction()) || "reopen".equals(form.getAction()))
                    issue.Open(c, user, true);
                else if ("close".equals(form.getAction()))
                    issue.Close(user);
                else
                    issue.Change(user);

                addComment(issue, (Issue)form.getOldValues(), user, form.getAction(), form.getComment(), getColumnCaptions(), getViewContext());
                IssueManager.saveIssue(openSession(), user, c, issue);
            }
            catch (Exception x)
            {
                errors.addError(new ObjectError("main", new String[] {"Error"}, new Object[] {x}, x.getMessage()));
                return false;
            }

            // Send update email...
            //    ...if someone other than "created by" is closing a bug
            //    ...if someone other than "assigned to" is updating, reopening, or resolving a bug
            if ("close".equals(form.getAction()))
            {
                sendUpdateEmail(issue, detailsUrl, "closed", form.getAction());
            }
            else
            {
                String change = ("open".equals(form.getAction()) || "reopen".equals(form.getAction()) ? "reopened" : form.getAction() + "d");
                sendUpdateEmail(issue, detailsUrl, change, form.getAction());
            }
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


    @RequiresPermission(ACL.PERM_UPDATEOWN)
    public class UpdateAction extends IssueUpdateAction
    {
        public ModelAndView getView(IssuesForm form, boolean reshow, BindException errors) throws Exception
        {
            int issueId = form.getBean().getIssueId();
            _issue = getIssue(issueId);
            if (_issue == null)
                HttpView.throwNotFound();

            User user = getUser();
            requiresUpdatePermission(user, _issue);

            IssuePage page = new IssuePage();
            JspView v = new JspView<IssuePage>(IssuesController.class, "updateView.jsp",page);

            IssueManager.CustomColumnConfiguration ccc = getCustomColumnConfiguration();

            page.setAction("update");
            page.setIssue(_issue);
            page.setCustomColumnConfiguration(ccc);
            page.setBody(form.getComment());
            page.setEditable(getEditableFields(page.getAction(), ccc));
            page.setRequiredFields(IssueManager.getRequiredIssueFields(getContainer()));
            page.setErrors(errors);

            return v;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return new DetailsAction(_issue).appendNavTrail(root)
                    .addChild("(update) " + _issue.getTitle());
        }
    }


    private Set<String> getEditableFields(String action, IssueManager.CustomColumnConfiguration ccc)
    {
        final Set<String> editable = new HashSet<String>(20);

        editable.add("title");
        editable.add("assignedTo");
        editable.add("type");
        editable.add("area");
        editable.add("priority");
        editable.add("milestone");
        editable.add("comments");

        for (String columnName : ccc.getColumnCaptions().keySet())
            editable.add(columnName);

        //if (!"insert".equals(action))
        editable.add("notifyList");

        if ("resolve".equals(action))
        {
            editable.add("resolution");
            editable.add("duplicate");
        }

        return editable;
    }


    @RequiresPermission(ACL.PERM_UPDATEOWN)
    public class ResolveAction extends IssueUpdateAction
    {
        public ModelAndView getView(IssuesForm form, boolean reshow, BindException errors) throws Exception
        {
            int issueId = form.getBean().getIssueId();
            _issue = getIssue(issueId);
            if (null == _issue)
                HttpView.throwNotFound();

            User user = getUser();
            requiresUpdatePermission(user, _issue);

            _issue.beforeResolve(user);

            if (null == _issue.getResolution())
            {
                Map<Integer, String> defaults = IssueManager.getAllDefaults(getContainer());

                String resolution = defaults.get(ISSUE_RESOLUTION);

                if (null != resolution)
                    _issue.setResolution(resolution);
            }

            IssuePage page = new IssuePage();
            JspView v = new JspView<IssuePage>(IssuesController.class, "updateView.jsp",page);

            IssueManager.CustomColumnConfiguration ccc = getCustomColumnConfiguration();

            page.setAction("resolve");
            page.setIssue(_issue);
            page.setCustomColumnConfiguration(ccc);
            page.setBody(form.getComment());
            page.setEditable(getEditableFields(page.getAction(), ccc));
            page.setRequiredFields(IssueManager.getRequiredIssueFields(getContainer()));
            page.setErrors(errors);

            return v;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return (new DetailsAction(_issue).appendNavTrail(root)).addChild("Resolve Issue");
        }
    }


    @RequiresPermission(ACL.PERM_UPDATEOWN)
    public class CloseAction extends IssueUpdateAction
    {
        public ModelAndView getView(IssuesForm form, boolean reshow, BindException errors) throws Exception
        {
            int issueId = form.getBean().getIssueId();
            _issue = getIssue(issueId);
            if (null == _issue)
                HttpView.throwNotFound();

            User user = getUser();
            requiresUpdatePermission(user, _issue);

            _issue.Close(user);

            IssuePage page = new IssuePage();
            JspView v = new JspView<IssuePage>(IssuesController.class, "updateView.jsp",page);

            IssueManager.CustomColumnConfiguration ccc = getCustomColumnConfiguration();

            page.setAction("close");
            page.setIssue(_issue);
            page.setCustomColumnConfiguration(ccc);
            page.setBody(form.getComment());
            page.setEditable(getEditableFields(page.getAction(), ccc));
            page.setRequiredFields(IssueManager.getRequiredIssueFields(getContainer()));
            page.setErrors(errors);

            return v;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return (new DetailsAction(_issue).appendNavTrail(root)).addChild("Close Issue");
        }
    }


    @RequiresPermission(ACL.PERM_UPDATEOWN)
    public class ReopenAction extends IssueUpdateAction
    {
        public ModelAndView getView(IssuesForm form, boolean reshow, BindException errors) throws Exception
        {
            int issueId = form.getBean().getIssueId();
            _issue = getIssue(issueId);

            User user = getUser();
            requiresUpdatePermission(user, _issue);

            _issue.beforeReOpen();
            _issue.Open(getContainer(), user);

            IssuePage page = new IssuePage();
            JspView v = new JspView<IssuePage>(IssuesController.class, "updateView.jsp",page);

            IssueManager.CustomColumnConfiguration ccc = getCustomColumnConfiguration();

            page.setAction("reopen");
            page.setIssue(_issue);
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
            return (new DetailsAction(_issue).appendNavTrail(root)).addChild("Reopen Issue");
        }
    }


    private static ActionURL getDetailsForwardURL(ViewContext context, Issue issue)
    {
        ActionURL url = context.cloneActionURL();
        url.setAction("details");
        url.addParameter("issueId", "" + issue.getIssueId());
        return url;
    }


    private void validateRequiredFields(IssuesController.IssuesForm form, Errors errors)
    {
        String requiredFields = IssueManager.getRequiredIssueFields(getContainer());
        final Map<String, String> newFields = form.getStrings();
        if (StringUtils.isEmpty(requiredFields))
            return;

        MapBindingResult requiredErrors = new MapBindingResult(newFields, errors.getObjectName());
        if (newFields.containsKey("title"))
            validateRequired("title", newFields.get("title"), requiredFields, requiredErrors);
        if (newFields.containsKey("assignedTo") && !(StringUtils.equals(form.getBean().getStatus(), Issue.statusCLOSED)))
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
    

    private void validateNotifyList(Issue issue, IssuesController.IssuesForm form, Errors errors)
    {
        String[] rawEmails = _toString(form.getNotifyList()).split("\n");
        List<String> invalidEmails = new ArrayList<String>();
        List<ValidEmail> emails = org.labkey.api.security.SecurityManager.normalizeEmails(rawEmails, invalidEmails);

        StringBuffer message = new StringBuffer();

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

        if (!emails.isEmpty())
        {
            StringBuffer notify = new StringBuffer();
            for (int i=0; i < emails.size(); i++)
            {
                notify.append(emails.get(i));
                if (i < emails.size()-1)
                    notify.append(';');
            }
            issue.setNotifyList(notify.toString());
        }
    }

    public static class CompleteUserForm extends FormData
    {
        private String _prefix;
        private String _issueId;

        public String getPrefix(){return _prefix;}
        public void setPrefix(String prefix){_prefix = prefix;}

        public String getIssueId(){return _issueId;}
        public void setIssueId(String issueId){_issueId = issueId;}
    }


    @RequiresPermission(ACL.PERM_READ)
    public class CompleteUserAction extends AjaxCompletionAction<CompleteUserForm>
    {
        public List<AjaxCompletion> getCompletions(CompleteUserForm form, BindException errors) throws Exception
        {
            Container c = getContainer();

            final int issueId = Integer.valueOf(form.getIssueId());
            Issue issue = getIssue(issueId);
            if (issue == null)
            {
                issue = new Issue();
                issue.Open(c, getUser());
            }
            User[] users = IssueManager.getAssignedToList(c, issue);
            return UserManager.getAjaxCompletions(form.getPrefix(), users, getViewContext());
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


    private void sendUpdateEmail(Issue issue, ActionURL detailsUrl, String change, String action)
    {
        try
        {
            final String to = getEmailAddresses(issue, action);
            if (to.length() > 0)
            {
                Issue.Comment lastComment = issue.getLastComment();
                String messageId = "<" + issue.getEntityId() + "." + lastComment.getCommentId() + "@" + AppProps.getInstance().getDefaultDomain() + ">";
                String references = messageId + " <" + issue.getEntityId() + "@" + AppProps.getInstance().getDefaultDomain() + ">";
                MailHelper.ViewMessage m = MailHelper.createMessage(AppProps.getInstance().getSystemEmailAddress(), to);
                HttpServletRequest request = AppProps.getInstance().createMockRequest();  // Use base server url for root of links in email
                if (m.getAllRecipients().length > 0)
                {
                    m.setMultipart(true);
                    m.setSubject("Issue #" + issue.getIssueId() + ", \"" + issue.getTitle() + ",\" has been " + change);
                    m.setHeader("References", references);

                    JspView viewPlain = new JspView<UpdateEmailPage>(IssuesController.class,"updateEmail.jsp",new UpdateEmailPage(detailsUrl.getURIString(),issue,true));
                    m.setTemplateContent(request, viewPlain, "text/plain");

                    JspView viewHtml = new JspView<UpdateEmailPage>(IssuesController.class,"updateEmail.jsp",new UpdateEmailPage(detailsUrl.getURIString(),issue,false));
                    m.setTemplateContent(request, viewHtml, "text/html");

                    MailHelper.send(m);
                }
            }
        }
        catch (Exception e)
        {
            _log.error("sendUpdateEmail", e);
        }
    }

    /**
     * Builds the list of email addresses for notification based on the user
     * preferences and the explicit notification list.
     */
    private String getEmailAddresses(Issue issue, String action) throws ServletException
    {
        final Set<String> emailAddresses = new HashSet<String>();
        final int filter = getNotificationFilter(action);
        final Container c = getContainer();

        if ((filter & IssueManager.getUserEmailPreferences(c, issue.getAssignedTo())) != 0)
        {
            emailAddresses.add(UserManager.getEmailForId(issue.getAssignedTo()));
        }

        if ((filter & IssueManager.getUserEmailPreferences(c, issue.getCreatedBy())) != 0)
        {
            emailAddresses.add(UserManager.getEmailForId(issue.getCreatedBy()));
        }

        // add any explicit notification list addresses
        final String notify = issue.getNotifyList();
        if (notify != null)
        {
            StringTokenizer tokenizer = new StringTokenizer(notify, ";\n\r\t");
            while (tokenizer.hasMoreTokens())
            {
                emailAddresses.add((String)tokenizer.nextElement());
            }
        }

        final String current = getUser().getEmail();
        final StringBuffer sb = new StringBuffer();

        boolean selfSpam = !((IssueManager.NOTIFY_SELF_SPAM & IssueManager.getUserEmailPreferences(c, getUser().getUserId())) == 0);
        if (selfSpam)
            emailAddresses.add(current);

        // build up the final semicolon delimited list, excluding the current user
        for (String email : emailAddresses.toArray(new String[0]))
        {
            if (selfSpam || !email.equals(current))
            {
                sb.append(email);
                sb.append(';');
            }
        }
        return sb.toString();
    }

    private int getNotificationFilter(String action)
    {
        if ("insert".equals(action))
            return IssueManager.NOTIFY_ASSIGNEDTO_OPEN;
        else
            return IssueManager.NOTIFY_ASSIGNEDTO_UPDATE | IssueManager.NOTIFY_CREATED_UPDATE;
    }

    @RequiresPermission(ACL.PERM_READ)
    public class EmailPrefsAction extends FormViewAction<EmailPrefsForm>
    {
        String _message = null;

        public ModelAndView getView(EmailPrefsForm form, boolean reshow, BindException errors) throws Exception
        {
            if (getViewContext().getUser().isGuest())
                HttpView.throwUnauthorized();

            int emailPrefs = IssueManager.getUserEmailPreferences(getContainer(), getUser().getUserId());
            int issueId = form.getIssueId() == null ? 0 : form.getIssueId();
            JspView v = new JspView<EmailPrefsBean>(IssuesController.class, "emailPreferences.jsp",
                new EmailPrefsBean(emailPrefs, errors, _message, issueId));
            return v;
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
            return (new BeginAction()).appendNavTrail(root).addChild("Email preferences");
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


    @RequiresPermission(ACL.PERM_ADMIN)
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

            IssuesController.AdminView adminView = new IssuesController.AdminView(getContainer(), getCustomColumnConfiguration());
            return adminView;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return (new BeginAction()).appendNavTrail(root).addChild("Issues Admin Page", getUrl());
        }

        public ActionURL getUrl()
        {
            return issueURL("admin");
        }
    }


    public abstract class AdminFormAction extends FormHandlerAction<AdminForm>
    {
        public void validateCommand(AdminForm adminForm, Errors errors)
        {
        }

        public ActionURL getSuccessURL(AdminForm adminForm)
        {
            return issueURL("admin");
        }
    }


    @RequiresPermission(ACL.PERM_ADMIN)
    public class AddKeywordAction extends AdminFormAction
    {
        public boolean handlePost(AdminForm form, BindException errors) throws Exception
        {
            IssueManager.addKeyword(getContainer(), form.getType(), form.getKeyword());
            return true;
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class DeleteKeywordAction extends AdminFormAction
    {
        public boolean handlePost(AdminForm form, BindException errors) throws Exception
        {
            IssueManager.deleteKeyword(getContainer(), form.getType(), form.getKeyword());
            return true;
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class SetKeywordDefaultAction extends AdminFormAction
    {
        public boolean handlePost(AdminForm form, BindException errors) throws Exception
        {
            IssueManager.setKeywordDefault(getContainer(), form.getType(), form.getKeyword());
            return true;
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class ClearKeywordDefaultAction extends AdminFormAction
    {
        public boolean handlePost(AdminForm form, BindException errors) throws Exception
        {
            IssueManager.clearKeywordDefault(getContainer(), form.getType());
            return true;
        }
    }


    @RequiresPermission(ACL.PERM_READ)
    public class RssAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            getPageConfig().setTemplate(PageConfig.Template.None);
            ResultSet rs = null;
            try
            {
                DataRegion r = new DataRegion();
                TableInfo tinfo = IssuesSchema.getInstance().getTableInfoIssues();
                ColumnInfo[] cols = tinfo.getColumns("IssueId,Created,Area,Title,AssignedTo,Priority,Status,Milestone");
                r.addColumns(cols);

                rs = r.getResultSet(new RenderContext(getViewContext()));
                ObjectFactory f = ObjectFactory.Registry.getFactory(Issue.class);
                Issue[] issues = (Issue[]) f.handleArray(rs);

                WebPartView v = new GroovyView("/org/labkey/issue/rss.gm");
                v.setFrame(WebPartView.FrameType.NONE);
                v.addObject("issues", issues);

                ActionURL url = new ActionURL("issues", "details.view", getContainer());
                v.addObject("url", url.getURIString() + "issueId=");
                v.addObject("homePageUrl", ActionURL.getBaseServerURL());
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
            return issueURL("rss");
        }
    }


    @RequiresPermission(ACL.PERM_ADMIN)
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


    @RequiresPermission(ACL.PERM_READ)
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
                        ActionURL url = getViewContext().cloneActionURL();
                        url.deleteParameters();
                        url.addParameter("issueId", Integer.toString(id));
                        url.setAction("details.view");
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
            url.setAction("list.view");
            url.addParameter(".lastFilter", "true");
            return HttpView.redirect(url);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermission(ACL.PERM_READ)
    public class SearchAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            Container c = getContainer();
            String searchTerm = (String)getProperty("search", "");

            Module module = ModuleLoader.getInstance().getCurrentModule();
            List<Search.Searchable> l = new ArrayList<Search.Searchable>();
            l.add((Search.Searchable)module);

            getPageConfig().setHelpTopic(new HelpTopic("search", HelpTopic.Area.DEFAULT));

            HttpView results = new Search.SearchResultsView(c, l, searchTerm, new ActionURL("issues", "search", c), getUser(), false, false);
            return results;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return new ListAction().appendNavTrail(root).addChild("Search Results");
        }
    }


    static boolean _equal(String a, String b)
    {
        return _toString(a).equals(_toString(b));
    }


    static String _toString(Object a)
    {
        return null == a ? "" : a.toString();
    }


    static void _appendChange(StringBuffer sb, String field, String from, String to)
    {
        from = _toString(from);
        to = _toString(to);
        if (!from.equals(to))
        {
            String encFrom = PageFlowUtil.filter(from);
            String encTo = PageFlowUtil.filter(to);
            sb.append("<tr><td>").append(field).append("</td><td>").append(encFrom).append("</td><td>&raquo;</td><td>").append(encTo).append("</td></tr>\n");
        }
    }


    static void addComment(Issue issue, Issue previous, User user, String action, String comment, Map<String, String> customColumns, ViewContext context)
    {
        StringBuffer sbChanges = new StringBuffer();
        if (!action.equals("insert") && !action.equals("update"))
        {
            sbChanges.append("<b>").append(action);

            if (action.equals("resolve"))
            {
                // Add the resolution; e.g. "resolve as Fixed"
                sbChanges.append(" as ").append(issue.getResolution());
            }

            sbChanges.append("</b><br>\n");
        }
        

        // CONSIDER: write changes in wiki
        // CONSIDER: and postpone formatting until render
        if (null != previous)
        {
            // issueChanges is not defined yet, but it leaves things flexible
            sbChanges.append("<table class=issues-Changes>");
            _appendChange(sbChanges, "Title", previous.getTitle(), issue.getTitle());
            _appendChange(sbChanges, "Status", previous.getStatus(), issue.getStatus());
            _appendChange(sbChanges, "Assigned To", previous.getAssignedToName(context), issue.getAssignedToName(context));
            _appendChange(sbChanges, "Notify", previous.getNotifyList(), issue.getNotifyList());
            _appendChange(sbChanges, "Type", previous.getType(), issue.getType());
            _appendChange(sbChanges, "Area", previous.getArea(), issue.getArea());
            _appendChange(sbChanges, "Priority", _toString(previous.getPriority()), _toString(issue.getPriority()));
            _appendChange(sbChanges, "Milestone", previous.getMilestone(), issue.getMilestone());

            _appendCustomColumnChange(sbChanges, "int1", _toString(previous.getInt1()), _toString(issue.getInt1()), customColumns);
            _appendCustomColumnChange(sbChanges, "int2", _toString(previous.getInt2()), _toString(issue.getInt2()), customColumns);
            _appendCustomColumnChange(sbChanges, "string1", previous.getString1(), issue.getString1(), customColumns);
            _appendCustomColumnChange(sbChanges, "string2", previous.getString2(), issue.getString2(), customColumns);

            sbChanges.append("</table>\n");
        }

        //why we are wrapping issue comments in divs???
        StringBuilder formattedComment = new StringBuilder();
        formattedComment.append("<div class=\"wiki\">");
        formattedComment.append(sbChanges);
        //render issues as plain text with links
        WikiRenderer w = WikiService.get().getRenderer(WikiRendererType.TEXT_WITH_LINKS);
        formattedComment.append(w.format(comment).getHtml());
        formattedComment.append("</div>");

        issue.addComment(user, formattedComment.toString());
    }

    private static void _appendCustomColumnChange(StringBuffer sb, String field, String from, String to, Map<String, String> columnCaptions)
    {
        String caption = columnCaptions.get(field);

        if (null != caption)
            _appendChange(sb, caption, from, to);
    }


    //
    // VIEWS
    //
    public static class AdminView extends GroovyView
    {
        Container _c;
        IssueManager.CustomColumnConfiguration _ccc;
        IssuesController.KeywordAdminView _keywordAdminView;
        JspView<IssuesController.IssuesPreference> _requiredFieldsView;

        public AdminView(Container c, IssueManager.CustomColumnConfiguration ccc)
        {
            super("/org/labkey/issue/admin.gm");

            _ccc = ccc;

            _keywordAdminView = new IssuesController.KeywordAdminView(c);
            _keywordAdminView.addKeyword("Type", ISSUE_TYPE);
            _keywordAdminView.addKeyword("Area", ISSUE_AREA);
            _keywordAdminView.addKeyword("Priority", ISSUE_PRIORITY);
            _keywordAdminView.addKeyword("Milestone", ISSUE_MILESTONE);
            _keywordAdminView.addKeyword("Resolution", ISSUE_RESOLUTION);

            addCustomColumn("string1", ISSUE_STRING1);
            addCustomColumn("string2", ISSUE_STRING2);

            List<String> columnNames = new ArrayList<String>();
            columnNames.addAll(Arrays.asList(REQUIRED_FIELDS_COLUMNS.split(",")));
            columnNames.addAll(IssuesTable.getCustomColumnCaptions(c).keySet());
            ColumnInfo[] cols = IssuesSchema.getInstance().getTableInfoIssues().getColumns(columnNames.toArray(new String[0]));

            IssuesController.IssuesPreference bean = new IssuesController.IssuesPreference(cols, IssueManager.getRequiredIssueFields(c));
            _requiredFieldsView = new JspView<IssuesController.IssuesPreference>("/org/labkey/issue/requiredFields.jsp", bean);
        }


        // Add keyword admin for custom columns with column picker enabled
        private void addCustomColumn(String tableColumn, int type)
        {
            if (_ccc.getPickListColumns().contains(tableColumn))
            {
                String caption = _ccc.getColumnCaptions().get(tableColumn);
                _keywordAdminView.addKeyword(caption, type);
            }
        }


        @Override
        protected void prepareWebPart(Object model) throws ServletException
        {
            this.setView("keywordView", _keywordAdminView);
            this.setView("requiredFieldsView", _requiredFieldsView);

            addObject("captions", _ccc.getColumnCaptions());
            addObject("pickLists", _ccc.getPickListColumns());
            addObject("pickListName", IssueManager.CustomColumnConfiguration.PICK_LIST_NAME);

            super.prepareWebPart(model);
        }
    }


    // Renders the pickers for all keywords; would be nice to render each picker independently, but that makes it hard to align
    // all the top and bottom sections with each other.
    public static class KeywordAdminView extends GroovyView
    {
        private Container _c;
        private List<IssuesController.KeywordAdminView.KeywordPicker> _keywordPickers = new ArrayList<IssuesController.KeywordAdminView.KeywordPicker>(5);

        public KeywordAdminView(Container c)
        {
            super("/org/labkey/issue/keywordAdmin.gm");
            _c = c;
        }

        public void addKeyword(String name, int type)
        {
            _keywordPickers.add(new IssuesController.KeywordAdminView.KeywordPicker(_c, name, type));
        }

        protected void prepareWebPart(Object context) throws ServletException
        {
            addObject("keywordPickers", _keywordPickers);
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
    }

    public static class EmailPrefsBean extends ViewForm
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
            return _issueId;
        }
    }

    public static class EmailPrefsForm extends ViewForm
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

    public static class AdminForm extends ViewForm
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
            super(Issue.class, IssuesSchema.getInstance().getTableInfoIssues(), new String[]{"action", "comment", "callbackURL"});
            setValidateRequired(false);
        }

        public String getAction()
        {
            return _stringValues.get("action");
        }

        public String getComment()
        {
            return _stringValues.get("comment");
        }

        public String getNotifyList()
        {
            return _stringValues.get("notifyList");
        }

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
         * @return
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
                return getDetailsForwardURL(getViewContext(), getBean());
            }
        }
    }


    public static class SummaryWebPart extends GroovyView
    {
        public SummaryWebPart()
        {
            super("/org/labkey/issue/summary_webpart.gm");
            addObject("isGuest", Boolean.TRUE);
            addObject("hasPermission", Boolean.TRUE);
            addObject("title", null);
        }


        @Override
        protected void prepareWebPart(Object model) throws ServletException
        {
            ViewContext context = getViewContext();
            // TODO: parameterize container
            Container c = context.getContainer();

            //set specified web part ti tle
            Object title = context.get("title");
            if(title == null)
                title = "Issues Summary";
            setTitle(title.toString());

            User u = context.getUser();
            boolean hasPermission = c.hasPermission(u, ACL.PERM_READ);
            context.put("hasPermission", hasPermission);
            context.put("isGuest", u.isGuest());
            if (!hasPermission)
                return;

            ActionURL url = getListUrl(c);
            setTitleHref(url.getLocalURIString());

            url.deleteParameters();
            context.put("url", url);

            try
            {
                Map[] bugs = IssueManager.getSummary(c);
                context.put("bugs", bugs);
            }
            catch (SQLException x)
            {
                setVisible(false);
            }
        }
    }


    public static class TestCase extends junit.framework.TestCase
    {
        public TestCase()
        {
            super("IssueController.jpf");
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
            return new TestSuite(IssuesController.TestCase.class);
        }
    }


    Object openSession()
    {
//        if (null == _s)
//            _s = IssueManager.openSession();
//        return _s;
        return null;
    }


    void closeSession()
    {
//        if (null != _s)
//            _s.close();
//        _s = null;
    }


    protected synchronized void afterAction(Throwable t)
    {
        super.afterAction(t);
        closeSession();
    }

    /**
     * Does this user have permission to update this issue?
     */
    private boolean hasUpdatePermission(User user, Issue issue)
    {
        // If we have full Update rights on the container, continue
        if (getViewContext().hasPermission(ACL.PERM_UPDATE))
            return true;

        // If UpdateOwn on the container AND we created this Issue, continue
        //noinspection RedundantIfStatement
        if (getViewContext().hasPermission(ACL.PERM_UPDATEOWN)
                && issue.getCreatedBy() == user.getUserId())
            return true;

        return false;
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


    public static class InsertForm extends ViewForm
    {
        private String _body;
        private Integer _assignedto;
        private String _callbackURL;
        private String _title;

        public String getBody()
        {
            return _body;
        }

        public void setBody(String body)
        {
            _body = body;
        }

        public Integer getAssignedto()
        {
            return _assignedto;
        }

        public void setAssignedto(Integer assignedto)
        {
            _assignedto = assignedto;
        }

        public String getCallbackURL()
        {
            return _callbackURL;
        }

        public void setCallbackURL(String callbackURL)
        {
            _callbackURL = callbackURL;
        }

        public String getTitle()
        {
            return _title;
        }

        public void setTitle(String title)
        {
            _title = title;
        }
    }


    public static class ListForm extends FormData
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

    public static class CustomizeIssuesPartView extends AbstractCustomizeWebPartView<Object>
    {
        public CustomizeIssuesPartView()
        {
            super("/org/labkey/issue/issues_customize.gm");
        }

        @Override
        public void prepareWebPart(Object model) throws ServletException
        {
            super.prepareWebPart(model);
            addObject("containerName", getViewContext().getContainer().getName());
        }
    }


    public static class IssuesPreference
    {
        private ColumnInfo[] _columns;
        private String _requiredFields;

        public IssuesPreference(ColumnInfo[] columns, String requiredFields)
        {
            _columns = columns;
            _requiredFields = requiredFields;
        }

        public ColumnInfo[] getColumns(){return _columns;}
        public String getRequiredFields(){return _requiredFields;}
    }


    public static class IssuePreferenceForm extends ViewForm
    {
        private String[] _requiredFields = new String[0];

        public void setRequiredFields(String[] requiredFields){_requiredFields = requiredFields;}
        public String[] getRequiredFields(){return _requiredFields;}
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
