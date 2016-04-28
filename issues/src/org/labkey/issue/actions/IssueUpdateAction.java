package org.labkey.issue.actions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.OwnerRole;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.issue.ColumnType;
import org.labkey.issue.ColumnTypeEnum;
import org.labkey.issue.CustomColumnConfiguration;
import org.labkey.issue.IssuesController;
import org.labkey.issue.model.CustomColumn;
import org.labkey.issue.model.Issue;
import org.labkey.issue.model.IssueListDef;
import org.labkey.issue.model.IssueManager;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.ServletException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by klum on 4/13/2016.
 */
public abstract class IssueUpdateAction extends FormViewAction<IssuesController.IssuesForm>
{
    // NOTE: aaron this is used in the InsertAction but not the update (consider refactor)
    protected Issue _issue = null;
    private CustomColumnConfiguration _columnConfiguration;

    public boolean handlePost(IssuesController.IssuesForm form, BindException errors) throws Exception
    {
        Container c = getContainer();
        User user = getUser();

        Issue issue = form.getBean();
        setIssue(issue);
        Issue prevIssue = (Issue)form.getOldValues();
        requiresUpdatePermission(user, issue);
        ActionURL detailsUrl;

        // check for no op
        if (IssuesController.UpdateAction.class.equals(form.getAction()) && form.getComment().equals("") && issue.equals(prevIssue))
            return true;

        // clear resolution, resolvedBy, and duplicate fields
        if (IssuesController.ReopenAction.class.equals(form.getAction()))
            issue.beforeReOpen(getContainer());

        Issue duplicateOf = null;
        if (IssuesController.ResolveAction.class.equals(form.getAction()) &&
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
//            detailsUrl = new IssuesController.DetailsAction(issue, getViewContext()).getURL();

            if (IssuesController.ResolveAction.class.equals(form.getAction()))
                issue.resolve(user);
            else if (IssuesController.InsertAction.class.equals(form.getAction()) || IssuesController.ReopenAction.class.equals(form.getAction()))
                issue.open(c, user);
            else if (IssuesController.CloseAction.class.equals(form.getAction()))
                issue.close(user);
            else
                issue.change(user);

            // convert from email addresses & display names to userids before we hit the database
            issue.parseNotifyList(issue.getNotifyList());

            changeSummary = IssuesController.createChangeSummary(issue, prevIssue, duplicateOf, user, form.getAction(), form.getComment(), getColumnConfiguration(), getUser());
            IssueManager.newSaveIssue(user, c, issue);
            AttachmentService.get().addAttachments(changeSummary.getComment(), getAttachmentFileList(), user);

            if (duplicateOf != null)
            {
                StringBuilder sb = new StringBuilder();
                sb.append("<em>Issue ").append(issue.getIssueId()).append(" marked as duplicate of this issue.</em>");
                Issue.Comment dupComment = duplicateOf.addComment(user, sb.toString());
                IssueManager.saveIssue(user, c, duplicateOf);
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
        String change = IssuesController.ReopenAction.class.equals(form.getAction()) ? "reopened" : getActionName(form.getAction()) + "d";
        if ("resolved".equalsIgnoreCase(change) && issue.getResolution() != null)
        {
            change += " as " + issue.getResolution(); // Issue 12273
        }
        sendUpdateEmail(issue, prevIssue, changeSummary.getTextChanges(), changeSummary.getSummary(), form.getComment(), detailsUrl, change, getAttachmentFileList(), form.getAction(), user);
*/
        return true;
    }

    public void validateCommand(IssuesController.IssuesForm form, Errors errors)
    {
/*
        validateRequiredFields(form, errors);
        validateNotifyList(form, errors);
        validateAssignedTo(form, errors);
        validateStringFields(form, errors);
*/
    }

    public ActionURL getSuccessURL(IssuesController.IssuesForm form)
    {
/*
        if(getIssue(form.getIssueId(), false).getStatus().equals("closed"))
            return issueURL(IssuesController.ListAction.class).addParameter(DataRegion.LAST_FILTER_PARAM, "true");

*/
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

        if (IssuesController.ResolveAction.class.equals(action))
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
            _columnConfiguration = new NewCustomColumnConfiguration(getContainer(), getUser(), getIssue());
        }
        return _columnConfiguration;
    }

    public void setColumnConfiguration(CustomColumnConfiguration columnConfiguration)
    {
        _columnConfiguration = columnConfiguration;
    }

    public static class NewCustomColumnConfiguration implements CustomColumnConfiguration
    {
        private Map<String, CustomColumn> _columnMap = new LinkedHashMap<>();
        private Map<String, String> _captionMap = new LinkedHashMap<>();

        public NewCustomColumnConfiguration(Container c, User user, Issue issue)
        {
            IssueListDef issueDef = IssueManager.getIssueListDef(issue);
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
    public static class ColumnTypeImpl implements ColumnType
    {
        String _name;
        boolean _allowBlank;
        List<String> _intialValues = new ArrayList<>();
        String _defaultValue;

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

        public static ColumnType fromCustomColumn(CustomColumn customColumn)
        {
            ColumnTypeImpl columnType = new ColumnTypeImpl();

            columnType._name = customColumn.getName();
            columnType._allowBlank = true;

            return columnType;
        }
    }
}
