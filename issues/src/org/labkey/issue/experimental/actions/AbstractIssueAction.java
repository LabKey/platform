package org.labkey.issue.experimental.actions;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.OwnerRole;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.labkey.issue.ColumnTypeEnum;
import org.labkey.issue.CustomColumnConfiguration;
import org.labkey.issue.IssuesController;
import org.labkey.issue.NewColumnType;
import org.labkey.issue.model.CustomColumn;
import org.labkey.issue.model.Issue;
import org.labkey.issue.model.IssueListDef;
import org.labkey.issue.model.IssueManager;
import org.labkey.issue.query.IssuesQuerySchema;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.validation.MapBindingResult;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.ServletException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by klum on 4/13/2016.
 */
public abstract class AbstractIssueAction extends FormViewAction<IssuesController.IssuesForm>
{
    protected Issue _issue = null;
    private CustomColumnConfiguration _columnConfiguration;

    public boolean handlePost(IssuesController.IssuesForm form, BindException errors) throws Exception
    {
        if (form.getSkipPost())
            return false;

        Container c = getContainer();
        User user = getUser();

        Issue issue = form.getBean();
        setIssue(issue);

        // bind the provisioned table to the form bean so we can get typed properties
        IssueListDef issueListDef = getIssueListDef();
        form.setTable(issueListDef.createTable(getUser()));

        Issue prevIssue = (Issue)form.getOldValues();
        requiresUpdatePermission(user, issue);
        ActionURL detailsUrl;

        // check for no op
        if (NewUpdateAction.class.equals(form.getAction()) && form.getComment().equals("") && issue.equals(prevIssue))
            return true;

        // clear resolution, resolvedBy, and duplicate fields
        if (NewReopenAction.class.equals(form.getAction()))
            issue.beforeReOpen(getContainer());

        Issue duplicateOf = null;
        if (NewResolveAction.class.equals(form.getAction()) &&
                issue.getResolution().equals("Duplicate") &&
                issue.getDuplicate() != null &&
                !issue.getDuplicate().equals(prevIssue.getDuplicate()))
        {
            if (issue.getDuplicate() == issue.getIssueId())
            {
                errors.rejectValue("Duplicate", SpringActionController.ERROR_MSG, "An issue may not be a duplicate of itself");
                return false;
            }
            duplicateOf = IssueManager.getIssue(null, issue.getDuplicate().intValue());
            if (duplicateOf == null)
            {
                errors.rejectValue("Duplicate", SpringActionController.ERROR_MSG, "Duplicate issue '" + issue.getDuplicate().intValue() + "' not found");
                return false;
            }
            if (!duplicateOf.lookupContainer().hasPermission(user, ReadPermission.class))
            {
                errors.rejectValue("Duplicate", SpringActionController.ERROR_MSG, "User does not have Read permission for duplicate issue '" + issue.getDuplicate().intValue() + "'");
                return false;
            }
        }

        // get previous related issue ids before updating
        Set<Integer> prevRelatedIds = prevIssue.getRelatedIssues();

        // todo handle related issues
/*
        boolean ret = relatedIssueHandler(issue, user, errors);
        if (!ret) return false;
*/

        IssuesController.ChangeSummary changeSummary;
        try (DbScope.Transaction transaction = IssuesSchema.getInstance().getSchema().getScope().ensureTransaction())
        {
            detailsUrl = new NewDetailsAction(issue, getViewContext()).getURL();

            if (NewInsertAction.class.equals(form.getAction()))
            {
                // for new issues, the original is always the default.
                issue.open(c, user);
                prevIssue = new Issue();
                prevIssue.open(getContainer(), getUser());
            }
            else if (NewResolveAction.class.equals(form.getAction()))
                issue.resolve(user);
            else if (NewReopenAction.class.equals(form.getAction()))
                issue.open(c, user);
            else if (NewCloseAction.class.equals(form.getAction()))
                issue.close(user);
            else
                issue.change(user);

            // convert from email addresses & display names to userids before we hit the database
            issue.parseNotifyList(issue.getNotifyList());

            changeSummary = IssuesController.createChangeSummary(issue, prevIssue, duplicateOf, user, form.getAction(), form.getComment(), getColumnConfiguration(), getUser());
            IssueManager.newSaveIssue(user, c, issue, form.getTypedColumns());
            AttachmentService.get().addAttachments(changeSummary.getComment(), getAttachmentFileList(), user);

            if (duplicateOf != null)
            {
                StringBuilder sb = new StringBuilder();
                sb.append("<em>Issue ").append(issue.getIssueId()).append(" marked as duplicate of this issue.</em>");
                Issue.Comment dupComment = duplicateOf.addComment(user, sb.toString());
                IssueManager.newSaveIssue(user, c, duplicateOf, Collections.emptyMap());
            }

            Set<Integer> newRelatedIds = issue.getRelatedIssues();

            // this list represents all the ids which will need related handling for a creating a relatedIssue entry
            Collection<Integer> newIssues = new ArrayList<>();
            newIssues.addAll(newRelatedIds);
            newIssues.removeAll(prevRelatedIds);
/*
            for (int curIssueId : newIssues)
            {
                Issue relatedIssue = relatedIssueCommentHandler(issue.getIssueId(), curIssueId, user, false);
                IssueManager.saveIssue(user, getContainer(), relatedIssue);
            }
*/

            // this list represents all the ids which will need related handling for a droping a relatedIssue entry
/*
            if(!prevRelatedIds.equals(newRelatedIds))
            {
                Collection<Integer> prevIssues = new ArrayList<>();
                prevIssues.addAll(prevRelatedIds);
                prevIssues.removeAll(newRelatedIds);
                for (int curIssueId : prevIssues)
                {
                    Issue relatedIssue = relatedIssueCommentHandler(issue.getIssueId(), curIssueId, user, true);
                    IssueManager.saveIssue(user, getContainer(), relatedIssue);
                }
            }
*/

            transaction.commit();
        }
/*
        catch (IOException x)
        {
            String message = x.getMessage() == null ? x.toString() : x.getMessage();
            errors.addError(new ObjectError("main", new String[] {"Error"}, new Object[] {message}, message));
            return false;
        }
*/

        // Send update email...
        //    ...if someone other than "created by" is closing a bug
        //    ...if someone other than "assigned to" is updating, reopening, or resolving a bug
/*
        final String assignedTo = UserManager.getDisplayName(_issue.getAssignedTo(), user);
        if (NewInsertAction.class.equals(form.getAction()))
        {
            if (assignedTo != null)
                change = "opened and assigned to " + assignedTo;
            else
                change = "opened";
        }
        else
            change = NewReopenAction.class.equals(form.getAction()) ? "reopened" : getActionName(form.getAction()) + "d";

        if ("resolved".equalsIgnoreCase(change) && issue.getResolution() != null)
        {
            change += " as " + issue.getResolution(); // Issue 12273
        }
        sendUpdateEmail(issue, prevIssue, changeSummary.getTextChanges(), changeSummary.getSummary(), form.getComment(), detailsUrl, change, getAttachmentFileList(), form.getAction(), user);
*/
        return true;
    }

    @Override
    public void validateCommand(IssuesController.IssuesForm form, Errors errors)
    {
        if (!form.getSkipPost())
        {
            setIssue(form.getBean());

            validateRequiredFields(form, errors);
            validateNotifyList(form, errors);
            validateAssignedTo(form, errors);
            validateStringFields(form, errors);
        }
    }

    public ActionURL getSuccessURL(IssuesController.IssuesForm form)
    {
        if (getIssue(form.getIssueId(), false).getStatus().equals("closed"))
            return new ActionURL(NewListAction.class, getContainer()).addParameter(DataRegion.LAST_FILTER_PARAM, "true");

        return form.getForwardURL();
    }

    /**
     * @param redirect if the issue isn't in this container, whether to redirect the browser to same URL except in the
     * issue's parent container
     * @throws RedirectException if the issue lives in another container and the user has at least read permission to it
     */
    protected Issue getIssue(int issueId, boolean redirect) throws RedirectException
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
        return IssueManager.getEntryTypeNames(getContainer()).singularName;
    }

    protected Set<String> getEditableFields(Class<? extends Controller> action, CustomColumnConfiguration ccc)
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
            editable.add(cc.getName());
        }

        editable.add("notifyList");

        if (NewResolveAction.class.equals(action))
        {
            editable.add("resolution");
            editable.add("duplicate");
        }

        editable.add("related");

        return editable;
    }

    /**
     * Throw an exception if user does not have permission to update issue
     */
    protected void requiresUpdatePermission(User user, Issue issue)
            throws ServletException
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

    protected IssueListDef getIssueListDef()
    {
        String issueDefName = getIssue().getIssueDefName();
        if (issueDefName != null)
        {
            return IssueManager.getIssueListDef(getContainer(), issueDefName);
        }
        else
        {
            return IssueManager.getIssueListDef(getIssue());
        }
    }

    public void setColumnConfiguration(CustomColumnConfiguration columnConfiguration)
    {
        _columnConfiguration = columnConfiguration;
    }

    @Deprecated
    /**
     * Temporary helper for the new details action
     */
    public static ActionURL getDetailsURL(Container c, Integer issueId, boolean print)
    {
        ActionURL url = new ActionURL(NewDetailsAction.class, c);

        if (print)
            url.addParameter("_print", "1");

        if (null != issueId)
            url.addParameter("issueId", issueId.toString());

        return url;
    }

    protected Issue setNewIssueDefaults(Issue issue) throws SQLException, ServletException
    {
        Map<ColumnTypeEnum, String> defaults = IssueManager.getAllDefaults(getContainer());

        ColumnTypeEnum.AREA.setDefaultValue(issue, defaults);
        ColumnTypeEnum.TYPE.setDefaultValue(issue, defaults);
        ColumnTypeEnum.MILESTONE.setDefaultValue(issue, defaults);
        ColumnTypeEnum.PRIORITY.setDefaultValue(issue, defaults);

        CustomColumnConfiguration config = getColumnConfiguration();

        // For each of the string configurable columns,
        // only set the default if the column is currently configured as a pick list
        for (ColumnTypeEnum stringColumn : ColumnTypeEnum.getCustomStringColumns())
        {
            if (config.hasPickList(stringColumn.getColumnName()))
            {
                stringColumn.setDefaultValue(issue, defaults);
            }
        }
        return issue;
    }

    private void validateRequiredFields(IssuesController.IssuesForm form, Errors errors)
    {
        String requiredFields = IssueManager.getRequiredIssueFields(getContainer());
        final Map<String, String> newFields = form.getStrings();
        if (!"0".equals(newFields.get("issueId")) && requiredFields.contains("comment"))
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

        // todo handle required custom fields
/*
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
*/
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
                if (StringUtils.isEmpty(value) || StringUtils.isEmpty(value.trim()))
                {
                    final CustomColumnConfiguration ccc = getColumnConfiguration();
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


    private void validateNotifyList(IssuesController.IssuesForm form, Errors errors)
    {
        User user;
        for (String username : StringUtils.split(StringUtils.trimToEmpty(form.getNotifyList()), ";\n"))
        {
            // NOTE: this "username" should be a user id but may be a psuedo-username (an assumed user which has default domain appended)
            //       or in the other special case this is an e-mail address
            username = username.trim();

            // Ignore lines of all whitespace, otherwise show an error.
            if (!"".equals(username))
            {
                user = UserManager.getUserByDisplayName(username);
                if (user != null)
                    continue;
                // Trying to generate user object from the "name" will not be enough if the username is for the default domain
                // TODO: most of this logic can be reduced when we change the Schema and fix the typing of these fields. (making announcements and issues consistent)
                try
                {
                    user = UserManager.getUser( new ValidEmail(username) );
                }
                catch (ValidEmail.InvalidEmailException e)
                {
                    // do nothing?
                }
                finally
                {
                    if (user == null)
                    {
                        String message = "Failed to add user " + username + ": Invalid user display name";
                        errors.rejectValue("notifyList", SpringActionController.ERROR_MSG, message);
                    }
                }
            }
        }
    }

    private void validateAssignedTo(IssuesController.IssuesForm form, Errors errors)
    {
        // here we check that the user is a valid assignee
        Integer userId = form.getBean().getAssignedTo();

        if (userId != null)
        {
            User user = UserManager.getUser(userId);
            // TODO: consider exposing IssueManager.canAssignTo
            if (!user.isActive() || !getContainer().hasPermission(user, UpdatePermission.class))
                errors.rejectValue("assignedTo", SpringActionController.ERROR_MSG, "An invalid user was set for the Assigned To");
        }
    }

    private static final int MAX_STRING_FIELD_LENGTH = 200;
    private void validateStringFields(IssuesController.IssuesForm form, Errors errors)
    {
        final Map<String, String> fields = form.getStrings();
        final CustomColumnConfiguration ccc = getColumnConfiguration();
        String lengthError = " cannot be longer than " + MAX_STRING_FIELD_LENGTH + " characters.";

        for (int i = 1; i <= 5; i++)
        {
            String name = "string" + i;

            if (fields.containsKey(name) && fields.get(name).length() > MAX_STRING_FIELD_LENGTH)
                errors.reject(SpringActionController.ERROR_MSG, ccc.getCaption(name) + lengthError);
        }
    }

    public static class NewCustomColumnConfiguration implements CustomColumnConfiguration
    {
        private Map<String, CustomColumn> _columnMap = new LinkedHashMap<>();
        private Map<String, String> _captionMap = new LinkedHashMap<>();

        public NewCustomColumnConfiguration(Container c, User user, IssueListDef issueDef)
        {
            if (issueDef != null)
            {
                Domain domain = issueDef.getDomain(user);
                Set<String> baseNames = new CaseInsensitiveHashSet();
                baseNames.addAll(domain.getDomainKind().getMandatoryPropertyNames(domain));

                if (domain != null)
                {
                    for (DomainProperty prop : domain.getProperties())
                    {
                        if (!baseNames.contains(prop.getName()))
                        {
                            CustomColumn col = new CustomColumn(c,
                                    prop.getName().toLowerCase(),
                                    prop.getLabel(),
                                    prop.getLookup() != null,
                                    prop.isProtected() ? InsertPermission.class : ReadPermission.class);

                            _columnMap.put(col.getName(), col);
                            _captionMap.put(col.getName(), col.getCaption());
                        }
                    }
                }
            }
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
            return true;
        }

        @Override
        public boolean hasPickList(String name)
        {
            CustomColumn col = _columnMap.get(name);
            return col != null && col.isPickList();
        }

        @Nullable
        @Override
        public String getCaption(String name)
        {
            CustomColumn col = _columnMap.get(name);
            return col != null ? col.getCaption() : null;
        }

        @Override
        public Map<String, String> getColumnCaptions()
        {
            return _captionMap;
        }
    }

    /**
     * Temporary shim to allow new and legacy Issue UI's to work at the same time
     */
    @Deprecated
    public static class ColumnTypeImpl implements NewColumnType
    {
        String _name;
        boolean _allowBlank;
        List<String> _intialValues = new ArrayList<>();
        String _defaultValue;
        IssueListDef _issueListDef;
        User _user;
        Issue _issue;

        @Override
        public int getOrdinal()
        {
            return 0;
        }

        @Override
        public String getColumnName()
        {
            return _name;
        }

        @Override
        public boolean isStandard()
        {
            return false;
        }

        @Override
        public boolean isCustomString()
        {
            return false;
        }

        @Override
        public boolean isCustomInteger()
        {
            return false;
        }

        @Override
        public boolean isCustom()
        {
            return false;
        }

        @Override
        public boolean allowBlank()
        {
            return _allowBlank;
        }

        @NotNull
        @Override
        public String[] getInitialValues()
        {
            return _intialValues.toArray(new String[0]);
        }

        @NotNull
        @Override
        public String getInitialDefaultValue()
        {
            return _defaultValue;
        }

        @Override
        public String getValue(Issue issue)
        {
            return null;
        }

        @Override
        public void setValue(Issue issue, String value)
        {

        }

        @Override
        public void setDefaultValue(Issue issue, Map<ColumnTypeEnum, String> defaults)
        {

        }

        @Override
        public DisplayColumn getRenderer(ViewContext context)
        {
            UserSchema userSchema = QueryService.get().getUserSchema(context.getUser(), context.getContainer(), IssuesQuerySchema.SCHEMA_NAME);
            TableInfo table = userSchema.getTable(_issueListDef.getName());

            ColumnInfo column = table.getColumn(FieldKey.fromParts(_name));

            return column.getRenderer();
        }

        public static NewColumnType fromCustomColumn(Issue issue, CustomColumn customColumn, IssueListDef issueListDef, User user)
        {
            ColumnTypeImpl columnType = new ColumnTypeImpl();

            columnType._issue = issue;
            columnType._name = customColumn.getName();
            columnType._allowBlank = true;
            columnType._issueListDef = issueListDef;
            columnType._user = user;

            return columnType;
        }
    }
}
