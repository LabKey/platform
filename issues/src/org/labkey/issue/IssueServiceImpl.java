package org.labkey.issue;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbScope;
import org.labkey.api.issues.Issue;
import org.labkey.api.issues.IssueService;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.security.LimitedUser;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.EditorRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.util.HtmlString;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.issue.actions.ChangeSummary;
import org.labkey.issue.actions.IssueValidation;
import org.labkey.issue.model.CommentAttachmentParent;
import org.labkey.issue.model.IssueListDef;
import org.labkey.issue.model.IssueManager;
import org.labkey.issue.model.IssueObject;
import org.springframework.validation.Errors;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.labkey.api.action.SpringActionController.ERROR_MSG;

public class IssueServiceImpl implements IssueService
{
    @Override
    public Issue saveIssue(ViewContext context, Issue issue, Issue.action action, List<AttachmentFile> attachments, Errors errors)
    {
        Container container = context.getContainer();
        User user = context.getUser();

        try (DbScope.Transaction transaction = IssuesSchema.getInstance().getSchema().getScope().ensureTransaction())
        {
            IssueListDef issueListDef = getIssueListDef(container, issue);
            if (issueListDef != null)
            {
                CustomColumnConfiguration ccc = new IssuesController.CustomColumnConfigurationImpl(container, user, issueListDef);
                IssueObject issueObject = new IssueObject(issue);

                IssueObject prevIssue = new IssueObject();
                if (action != Issue.action.insert && issueObject.getIssueId() != 0)
                {
                    prevIssue = IssueManager.getIssue(container, user, issueObject.getIssueId());
                }
                IssueObject duplicateOf = null;
                String resolution = issueObject.getResolution() != null ? issueObject.getResolution() : "Fixed";

                switch (action)
                {
                    case insert -> {
                        // for new issues, the original is always the default.
                        issueObject.open(container, user);
                        prevIssue.open(container, user);
                    }
                    case update -> issueObject.change(user);
                    case resolve -> {
                        if (resolution.equals("Duplicate") &&
                                issueObject.getDuplicate() != null &&
                                !issueObject.getDuplicate().equals(prevIssue.getDuplicate()))
                        {
                            duplicateOf = IssueManager.getIssue(null, user, issueObject.getDuplicate());
                        }
                        issueObject.beforeResolve(container, user);
                        issueObject.resolve(user);
                    }
                    case reopen -> issueObject.open(container, user);
                    case close -> issueObject.close(user);
                }

                // validate any related issue values
                IssueValidation.relatedIssueHandler(issueObject, user, errors);
                if (errors.hasErrors())
                    return null;

                // convert from email addresses & display names to userids before we hit the database
                issueObject.parseNotifyList(issueObject.getNotifyList());

                ChangeSummary changeSummary = ChangeSummary.createChangeSummary(context, issueListDef,
                        issueObject, prevIssue, duplicateOf, container, user, action, issueObject.getComment(), ccc);
                IssueManager.saveIssue(user, container, issueObject);

                if (!attachments.isEmpty())
                    AttachmentService.get().addAttachments(new CommentAttachmentParent(changeSummary.getComment()), attachments, user);

                // get previous related issue ids before updating
                Set<Integer> prevRelatedIds = new HashSet<>();
                if (prevIssue != null)
                    prevRelatedIds = prevIssue.getRelatedIssues();

                // update the duplicate issue
                if (duplicateOf != null)
                {
                    duplicateOf.addComment(user, HtmlString.unsafe("<em>Issue " + issueObject.getIssueId() + " marked as duplicate of this issue.</em>"));
                    IssueManager.saveIssue(user, container, duplicateOf);
                }

                Set<Integer> newRelatedIds = issueObject.getRelatedIssues();

                // this list represents all the ids which will need related handling for creating a relatedIssue entry
                List<Integer> newAddedRelatedIssues = new ArrayList<>(newRelatedIds);
                newAddedRelatedIssues.removeAll(prevRelatedIds);

                for (int curIssueId : newAddedRelatedIssues)
                {
                    IssueObject relatedIssue = ChangeSummary.relatedIssueCommentHandler(issueObject.getIssueId(), curIssueId, user, false);
                    if (null != relatedIssue)
                        IssueManager.saveIssue(getRelatedIssueUser(container, user, relatedIssue), container, relatedIssue);
                }

                // this list represents all the ids which will need related handling for dropping a relatedIssue entry
                if (!prevRelatedIds.equals(newRelatedIds))
                {
                    List<Integer> prevIssues = new ArrayList<>(prevRelatedIds);
                    prevIssues.removeAll(newRelatedIds);
                    for (int curIssueId : prevIssues)
                    {
                        IssueObject relatedIssue = ChangeSummary.relatedIssueCommentHandler(issueObject.getIssueId(), curIssueId, user, true);
                        IssueManager.saveIssue(getRelatedIssueUser(container, user, relatedIssue), container, relatedIssue);
                    }
                }

                final String assignedTo = UserManager.getDisplayName(issueObject.getAssignedTo(), user);
                String change;
                if (action == Issue.action.insert)
                {
                    if (assignedTo != null)
                        change = "opened and assigned to " + assignedTo;
                    else
                        change = "opened";
                }
                else
                    change = action == Issue.action.reopen ? "reopened" : action.name() + "d";

                if ("resolved".equalsIgnoreCase(change))
                {
                    change += " as " + resolution; // Issue 12273
                }
                changeSummary.sendUpdateEmail(container, user, issueObject.getComment(),
                        new ActionURL(IssuesController.DetailsAction.class, container),
                        change, attachments);

                if (!errors.hasErrors())
                {
                    transaction.commit();
                    return IssueManager.getIssue(container, user, issueObject.getIssueId());
                }
            }
        }
        catch (IOException x)
        {
            String message = x.getMessage() == null ? x.toString() : x.getMessage();
            errors.reject(ERROR_MSG, message);

            return null;
        }
        return null;
    }

    @Override
    public Issue saveIssue(ViewContext context, Issue issue, Issue.action action, Errors errors)
    {
        return saveIssue(context, issue, action, Collections.emptyList(), errors);
    }

    @Override
    public void validateIssue(Container container, User user, Issue issue, Issue.action action, Errors errors)
    {
        // Fetch the default
        IssueListDef defaultIssueListDef = IssueManager.getDefaultIssueListDef(container);
        IssueListDef issueListDef = getIssueListDef(container, issue);
        IssueObject issueObject = new IssueObject(issue);

        if (action == null)
        {
            errors.reject(ERROR_MSG, "Action must be specified : (update, resolve, close, reopen)");
            return;
        }

        IssueObject prevIssue = null;
        if (action != Issue.action.insert)
        {
            prevIssue = IssueManager.getIssue(container, user, issueObject.getIssueId());
            if (prevIssue == null)
            {
                errors.reject(ERROR_MSG, "The specified issue does not exist, id : " + issueObject.getIssueId());
                return;
            }
            // if issue definition isn't provided get it from the issue being updated
            if (issueListDef == null)
                issueListDef = IssueManager.getIssueListDef(container, prevIssue.getIssueDefId());
        }
        else
        {
            if (issueListDef == null && defaultIssueListDef == null)
            {
                errors.reject(ERROR_MSG, "IssueDefName or IssueDefId is required when creating new issues");
                return;
            }
        }

        if (issueListDef == null && defaultIssueListDef == null)
        {
            errors.reject(ERROR_MSG, "No valid issue list def could be found");
            return;
        }

        // set the issueListDefId (from the default issue list) if it wasn't explicitly specified
        if (issueListDef == null)
        {
            issueObject.setIssueDefId(defaultIssueListDef.getRowId());
        }

        if (action == Issue.action.reopen)
        {
            // clear resolution, resolvedBy, and duplicate fields
            issueObject.beforeReOpen(container);
        }

        if (action == Issue.action.resolve)
        {
            //IssueObject issue = form.getBean();
            String resolution = issueObject.getResolution() != null ? issueObject.getResolution() : "Fixed";

            if (resolution.equals("Duplicate") &&
                    issueObject.getDuplicate() != null &&
                    !issueObject.getDuplicate().equals(prevIssue.getDuplicate()))
            {
                if (issueObject.getDuplicate() == issueObject.getIssueId())
                {
                    errors.reject(ERROR_MSG, "An issue may not be a duplicate of itself");
                    return;
                }
                IssueObject duplicateOf = IssueManager.getIssue(null, user, issueObject.getDuplicate());
                if (duplicateOf == null || duplicateOf.lookupContainer() == null)
                {
                    errors.reject(ERROR_MSG, "Duplicate issue '" + issueObject.getDuplicate().intValue() + "' not found");
                    return;
                }
                if (!duplicateOf.lookupContainer().hasPermission(user, ReadPermission.class))
                {
                    errors.reject(ERROR_MSG, "User does not have Read permission for duplicate issue '" + issueObject.getDuplicate().intValue() + "'");
                    return;
                }
            }
        }

        action.checkPermission(container, user, issueObject);
        CustomColumnConfiguration ccc = new IssuesController.CustomColumnConfigurationImpl(container, user, issueListDef);
        IssueValidation.validateRequiredFields(issueListDef, ccc, issueObject, user, errors);
        IssueValidation.validateNotifyList(issueObject, errors);
        // don't validate the assigned to field if we are in the process
        // of closing it and we are assigning it to the guest user (otherwise validate)
        if (action != Issue.action.close || UserManager.getGuestUser().getUserId() != issueObject.getAssignedTo())
        {
            IssueValidation.validateAssignedTo(issueObject, container, errors);
        }
        IssueValidation.validateComments(issueObject, errors);
    }

    /**
     * To specify a related issue, the user just has to have read access to the container that the related
     * issue resides in. Since we add comment updates to both issues, we need to ensure that the user has Update permissions
     * to the related folder and if not, temporarily elevate the permissions so the user can perform the update.
     */
    private User getRelatedIssueUser(Container container, User user, IssueObject relatedIssue)
    {
        Container relatedContainer = ContainerManager.getForId(relatedIssue.getContainerId());
        if (relatedContainer == null)
            relatedContainer = container;

        if (!relatedContainer.hasPermission(user, UpdatePermission.class))
        {
            Set<Role> contextualRoles = new HashSet<>(user.getStandardContextualRoles());
            contextualRoles.add(RoleManager.getRole(EditorRole.class));
            return new LimitedUser(user, user.getGroups(), contextualRoles, false);
        }
        return user;
    }

    @Nullable
    public static IssueListDef getIssueListDef(Container c, Issue issue)
    {
        IssueListDef issueListDef = null;
        if (issue.getIssueDefId() != null)
            issueListDef = IssueManager.getIssueListDef(c, issue.getIssueDefId());

        if (issueListDef == null && issue.getIssueDefName() != null)
            issueListDef = IssueManager.getIssueListDef(c, issue.getIssueDefName());

        return issueListDef;
    }
}
