package org.labkey.issue.experimental.actions;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.notification.NotificationService;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.validator.ColumnValidator;
import org.labkey.api.data.validator.ColumnValidators;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.OwnerRole;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.MailHelper;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.emailTemplate.EmailTemplateService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.issue.CustomColumnConfiguration;
import org.labkey.issue.IssuesController;
import org.labkey.issue.experimental.IssuesListView;
import org.labkey.issue.experimental.NewIssueUpdateEmailTemplate;
import org.labkey.issue.model.CustomColumn;
import org.labkey.issue.model.Issue;
import org.labkey.issue.model.IssueListDef;
import org.labkey.issue.model.IssueManager;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.validation.MapBindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.servlet.mvc.Controller;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.internet.AddressException;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.labkey.api.action.SpringActionController.getActionName;

/**
 * Created by klum on 4/13/2016.
 */
public abstract class AbstractIssueAction extends FormViewAction<IssuesController.IssuesForm>
{
    private static final Logger _log = Logger.getLogger(IssuesController.class);
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
        issue.setExtraProperties(form.getTypedColumns());

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

        boolean ret = relatedIssueHandler(issue, user, errors);
        if (!ret) return false;

        ChangeSummary changeSummary;
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

            changeSummary = ChangeSummary.createChangeSummary(getIssueListDef(), issue, prevIssue, duplicateOf, getContainer(), user, form.getAction(), form.getComment(), getColumnConfiguration(), getUser());
            IssueManager.newSaveIssue(user, c, issue);
            AttachmentService.get().addAttachments(changeSummary.getComment(), getAttachmentFileList(), user);

            if (duplicateOf != null)
            {
                StringBuilder sb = new StringBuilder();
                sb.append("<em>Issue ").append(issue.getIssueId()).append(" marked as duplicate of this issue.</em>");
                duplicateOf.addComment(user, sb.toString());
                IssueManager.newSaveIssue(user, c, duplicateOf);
            }

            Set<Integer> newRelatedIds = issue.getRelatedIssues();

            // this list represents all the ids which will need related handling for a creating a relatedIssue entry
            Collection<Integer> newIssues = new ArrayList<>();
            newIssues.addAll(newRelatedIds);
            newIssues.removeAll(prevRelatedIds);

            for (int curIssueId : newIssues)
            {
                Issue relatedIssue = relatedIssueCommentHandler(issue.getIssueId(), curIssueId, user, false);
                IssueManager.newSaveIssue(user, getContainer(), relatedIssue);
            }

            // this list represents all the ids which will need related handling for a droping a relatedIssue entry
            if(!prevRelatedIds.equals(newRelatedIds))
            {
                Collection<Integer> prevIssues = new ArrayList<>();
                prevIssues.addAll(prevRelatedIds);
                prevIssues.removeAll(newRelatedIds);
                for (int curIssueId : prevIssues)
                {
                    Issue relatedIssue = relatedIssueCommentHandler(issue.getIssueId(), curIssueId, user, true);
                    IssueManager.newSaveIssue(user, getContainer(), relatedIssue);
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
        {
            ActionURL url = new ActionURL(NewListAction.class, getContainer()).addParameter(DataRegion.LAST_FILTER_PARAM, "true");
            IssueListDef issueListDef = getIssueListDef();

            if (issueListDef != null)
                url.addParameter(IssuesListView.ISSUE_LIST_DEF_NAME, issueListDef.getName());

            return url;
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
        Issue result = IssueManager.getNewIssue(getContainer(), getUser(), issueId);
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

    private void validateRequiredFields(IssuesController.IssuesForm form, Errors errors)
    {
        String requiredFields = "";
        final Map<String, String> newFields = form.getStrings();
        MapBindingResult requiredErrors = new MapBindingResult(newFields, errors.getObjectName());

        // handle custom field types
        IssueListDef issueListDef = getIssueListDef();
        if (issueListDef != null)
        {
            TableInfo tableInfo = issueListDef.createTable(getUser());

            for (Map.Entry<String, String> entry : newFields.entrySet())
            {
                ColumnInfo col = tableInfo.getColumn(FieldKey.fromParts(entry.getKey()));
                if (col != null)
                {
                    for (ColumnValidator validator : ColumnValidators.create(col, null))
                    {
                        String msg = validator.validate(0, entry.getValue());
                        if (msg != null)
                            requiredErrors.rejectValue(col.getName(), "NullError", new Object[] {col.getName()}, msg);
                    }
                }
            }
        }
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

    private void sendUpdateEmail(Issue issue, Issue prevIssue, String fieldChanges, String summary, String comment, ActionURL detailsURL, String change, List<AttachmentFile> attachments, Class<? extends Controller> action, User createdByUser) throws ServletException
    {
        // Skip the email if no comment and no public fields have changed, #17304
        if (fieldChanges.isEmpty() && comment.isEmpty())
            return;

        final Set<User> allAddresses = getUsersToEmail(issue, prevIssue, action);
        for (User user : allAddresses)
        {
            boolean hasPermission = getContainer().hasPermission(user, ReadPermission.class);
            if (!hasPermission) continue;

            String to = user.getEmail();
            try
            {
                Issue.Comment lastComment = issue.getLastComment();
                String messageId = "<" + issue.getEntityId() + "." + lastComment.getCommentId() + "@" + AppProps.getInstance().getDefaultDomain() + ">";
                String references = messageId + " <" + issue.getEntityId() + "@" + AppProps.getInstance().getDefaultDomain() + ">";
                MailHelper.MultipartMessage m = MailHelper.createMultipartMessage();
                m.addRecipients(Message.RecipientType.TO, MailHelper.createAddressArray(to));
                Address[] addresses = m.getAllRecipients();

                if (addresses != null && addresses.length > 0)
                {
                    NewIssueUpdateEmailTemplate template = EmailTemplateService.get().getEmailTemplate(NewIssueUpdateEmailTemplate.class, getContainer());
                    template.init(issue, detailsURL, change, comment, fieldChanges, allAddresses, attachments, user);

                    m.setSubject(template.renderSubject(getContainer()));
                    m.setFrom(template.renderFrom(getContainer(), LookAndFeelProperties.getInstance(getContainer()).getSystemEmailAddress()));
                    m.setHeader("References", references);
                    String body = template.renderBody(getContainer());

                    m.setTextContent(body);
                    StringBuilder html = new StringBuilder();
                    html.append("<html><head></head><body>");
                    html.append(PageFlowUtil.filter(body,true,true));
                    html.append(
                            "<div itemscope itemtype=\"http://schema.org/EmailMessage\">\n" +
                                    "  <div itemprop=\"action\" itemscope itemtype=\"http://schema.org/ViewAction\">\n" +
                                    "    <link itemprop=\"url\" href=\"" + PageFlowUtil.filter(detailsURL) + "\"></link>\n" +
                                    "    <meta itemprop=\"name\" content=\"View Commit\"></meta>\n" +
                                    "  </div>\n" +
                                    "  <meta itemprop=\"description\" content=\"View this " + PageFlowUtil.filter(IssueManager.getEntryTypeNames(getContainer()).singularName) + "\"></meta>\n" +
                                    "</div>\n");
                    html.append("</body></html>");
                    m.setEncodedHtmlContent(html.toString());

                    NotificationService.get().sendMessage(getContainer(), createdByUser, user, m,
                            "view " + IssueManager.getEntryTypeNames(getContainer()).singularName,
                            new ActionURL(IssuesController.DetailsAction.class,getContainer()).addParameter("issueId",issue.getIssueId()).getLocalURIString(false),
                            "issue:" + issue.getIssueId(),
                            Issue.class.getName(), true);
                }
            }
            catch (ConfigurationException | AddressException e)
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

        if (IssuesController.InsertAction.class.equals(action))
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

    private boolean relatedIssueHandler(Issue issue, User user, BindException errors)
    {
        String textInput = issue.getRelated();
        Set<Integer> newRelatedIssues = new TreeSet<>();
        if (textInput != null)
        {
            String[] textValues = issue.getRelated().split("[\\s,;]+");
            int relatedId;
            // for each issue id we need to validate
            for (String relatedText : textValues)
            {
                relatedId = NumberUtils.toInt(relatedText.trim(), 0);
                if (relatedId == 0)
                {
                    errors.rejectValue("Related", SpringActionController.ERROR_MSG, "Invalid issue id in related string.");
                    return false;
                }
                if (issue.getIssueId() == relatedId)
                {
                    errors.rejectValue("Related", SpringActionController.ERROR_MSG, "As issue may not be related to itself");
                    return false;
                }

                Issue related = IssueManager.getNewIssue(null, getUser(), relatedId);
                if (related == null)
                {
                    errors.rejectValue("Related", SpringActionController.ERROR_MSG, "Related issue '" + relatedId + "' not found");
                    return false;
                }
                newRelatedIssues.add(relatedId);
            }
        }

        // Fetch from IssueManager to make sure the related issues are populated
        Issue originalIssue = IssueManager.getNewIssue(null, getUser(), issue.getIssueId());
        Set<Integer> originalRelatedIssues = originalIssue == null ? Collections.emptySet() : originalIssue.getRelatedIssues();

        // Only check permissions if
        if (!originalRelatedIssues.equals(newRelatedIssues))
        {
            for (Integer relatedId : newRelatedIssues)
            {
                Issue related = IssueManager.getIssue(null, relatedId);
                if (!related.lookupContainer().hasPermission(user, ReadPermission.class))
                {
                    errors.rejectValue("Related", SpringActionController.ERROR_MSG, "User does not have Read Permission for related issue '" + relatedId + "'");
                    return false;
                }
            }
        }

        // this sets the collection of integer ids for all related issues
        issue.setRelatedIssues(newRelatedIssues);
        return true;
    }

    private Issue relatedIssueCommentHandler(int issueId, int relatedIssueId, User user, boolean drop)
    {
        StringBuilder sb = new StringBuilder();
        Issue relatedIssue = IssueManager.getNewIssue(null, getUser(), relatedIssueId);
        Set<Integer> prevRelated = relatedIssue.getRelatedIssues();
        Set<Integer> newRelated = new TreeSet<>();
        newRelated.addAll(prevRelated);

        if (drop)
            newRelated.remove(new Integer(issueId));
        else
            newRelated.add(issueId);

        sb.append("<div class=\"wiki\"><table class=issues-Changes>");
        sb.append(String.format("<tr><td>Related</td><td>%s</td><td>&raquo;</td><td>%s</td></tr>", StringUtils.join(prevRelated, ", "), StringUtils.join(newRelated, ", ")));
        sb.append("</table></div>");

        relatedIssue.addComment(user, sb.toString());
        relatedIssue.setRelatedIssues(newRelated);

        return relatedIssue;
    }

    public static class NewCustomColumnConfiguration implements CustomColumnConfiguration
    {
        private Map<String, CustomColumn> _columnMap = new LinkedHashMap<>();
        private Map<String, String> _captionMap = new LinkedHashMap<>();
        private Set<String> _baseNames = new CaseInsensitiveHashSet();
        private Map<String, DomainProperty> _propertyMap = new CaseInsensitiveHashMap<>();
        private List<DomainProperty> _customProperties = new ArrayList<>();

        public NewCustomColumnConfiguration(Container c, User user, IssueListDef issueDef)
        {
            if (issueDef != null)
            {
                Domain domain = issueDef.getDomain(user);
                _baseNames.addAll(domain.getDomainKind().getMandatoryPropertyNames(domain));

                if (domain != null)
                {
                    for (DomainProperty prop : domain.getProperties())
                    {
                        _propertyMap.put(prop.getName(), prop);
                        if (!_baseNames.contains(prop.getName()))
                        {
                            _customProperties.add(prop);
                            CustomColumn col = new CustomColumn(c,
                                    prop.getName().toLowerCase(),
                                    prop.getLabel() != null ? prop.getLabel() : prop.getName(),
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
}
