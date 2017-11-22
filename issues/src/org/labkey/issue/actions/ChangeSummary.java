/*
 * Copyright (c) 2016-2017 LabKey Corporation
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
package org.labkey.issue.actions;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.notification.Notification;
import org.labkey.api.admin.notification.NotificationService;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.notification.NotificationMenuView;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.MailHelper;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.emailTemplate.EmailTemplateService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;
import org.labkey.issue.CustomColumnConfiguration;
import org.labkey.issue.IssueUpdateEmailTemplate;
import org.labkey.issue.IssuesController;
import org.labkey.issue.model.CustomColumn;
import org.labkey.issue.model.Issue;
import org.labkey.issue.model.IssueListDef;
import org.labkey.issue.model.IssueManager;
import org.springframework.web.servlet.mvc.Controller;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.labkey.api.action.SpringActionController.getActionName;

/**
 * Created by klum on 5/10/2016.
 */
public class ChangeSummary
{
    private static final Logger _log = Logger.getLogger(ChangeSummary.class);

    private Issue.Comment _comment;
    private String _textChanges;
    private String _summary;

    private IssueListDef _issueListDef;
    private Issue _issue;
    private Issue _prevIssue;
    private Class<? extends Controller> _action;
    private Map<String, Object> _issueProperties = new HashMap<>();

    private static Set<String> _standardFields = new CaseInsensitiveHashSet();

    static
    {
        _standardFields.add("Title");
        _standardFields.add("Status");
        _standardFields.add("AssignedTo");
        _standardFields.add("Notify");
        _standardFields.add("Type");
        _standardFields.add("Area");
        _standardFields.add("Priority");
        _standardFields.add("Milestone");
        _standardFields.add("Related");
    }

    private ChangeSummary(IssueListDef issueListDef, Issue issue, Issue prevIssue, Issue.Comment comment,
                          String textChanges, String summary, Class<? extends Controller> action, Map<String, Object> issueProperties)
    {
        _issueListDef = issueListDef;
        _issue = issue;
        _prevIssue = prevIssue;
        _comment = comment;
        _textChanges = textChanges;
        _summary = summary;
        _action = action;
        _issueProperties = issueProperties;
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

    static public ChangeSummary createChangeSummary(ViewContext context, IssueListDef issueListDef, Issue issue, Issue previous, @Nullable Issue duplicateOf,
                                                    Container container,
                                                    User user,
                                                    Class<? extends Controller> action,
                                                    String comment,
                                                    CustomColumnConfiguration ccc)
    {
        StringBuilder sbHTMLChanges = new StringBuilder();
        StringBuilder sbTextChanges = new StringBuilder();
        String summary = null;
        Map<String, Object> issueProperties = new HashMap<>();

        if (!action.equals(IssuesController.InsertAction.class) && !action.equals(IssuesController.UpdateAction.class))
        {
            summary = getActionName(action).toLowerCase();

            if (action.equals(IssuesController.ResolveAction.class))
            {
                if (issue.getResolution() != null)
                {
                    // Add the resolution; e.g. "resolve as Fixed"
                    summary += " as " + issue.getResolution();
                }
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

            String prevPriStringVal = previous.getPriority() == null ? "" : String.valueOf(previous.getPriority());
            String priStringVal = issue.getPriority() == null ? "" : String.valueOf(issue.getPriority());

            // issueChanges is not defined yet, but it leaves things flexible
            sbHTMLChanges.append("<table class=issues-Changes>");
            _appendColumnChange(sbHTMLChanges, sbTextChanges, "Title", previous.getTitle(), issue.getTitle(), ccc, newIssue);
            _appendColumnChange(sbHTMLChanges, sbTextChanges, "Status", previous.getStatus(), issue.getStatus(), ccc, newIssue);
            _appendColumnChange(sbHTMLChanges, sbTextChanges, "AssignedTo", previous.getAssignedToName(user), issue.getAssignedToName(user), ccc, newIssue);
            _appendColumnChange(sbHTMLChanges, sbTextChanges, "Notify",
                    StringUtils.join(previous.getNotifyListDisplayNames(null),";"),
                    StringUtils.join(issue.getNotifyListDisplayNames(null),";"),
                    ccc, newIssue);
            _appendColumnChange(sbHTMLChanges, sbTextChanges, "Type", previous.getType(), issue.getType(), ccc, newIssue);
            _appendColumnChange(sbHTMLChanges, sbTextChanges, "Area", previous.getArea(), issue.getArea(), ccc, newIssue);
            _appendColumnChange(sbHTMLChanges, sbTextChanges, "Priority", prevPriStringVal, priStringVal, ccc, newIssue);
            _appendColumnChange(sbHTMLChanges, sbTextChanges, "Milestone", previous.getMilestone(), issue.getMilestone(), ccc, newIssue);
            _appendColumnChange(sbHTMLChanges, sbTextChanges, "Related", StringUtils.join(previous.getRelatedIssues(), ", "), StringUtils.join(issue.getRelatedIssues(), ", "), ccc, newIssue);

            Map<String, Object> oldProps = previous.getProperties();
            UserSchema schema = QueryService.get().getUserSchema(user, container, IssuesSchema.SCHEMA_NAME);
            TableInfo table = schema.getTable(issueListDef.getName());
            if (table != null)
            {
                RenderContext ctx = new RenderContext(context);
                ctx.setMode(DataRegion.MODE_DETAILS);

                for (Map.Entry<String, Object> entry : issue.getProperties().entrySet())
                {
                    if (!_standardFields.contains(entry.getKey()))
                    {
                        Object oldValue = oldProps.get(entry.getKey());
                        Object newValue = entry.getValue();

                        issueProperties.put(entry.getKey(), newValue);
                        ColumnInfo col = table.getColumn(FieldKey.fromParts(entry.getKey()));
                        if (col != null && col.getFk() != null)
                        {
                            // if the column is a lookup, render the display column name versus the key : issue 27525
                            NamedObjectList nol = col.getFk().getSelectList(ctx);
                            if (nol != null)
                            {
                                Object ov = nol.get(String.valueOf(oldValue));
                                Object nv = nol.get(String.valueOf(newValue));

                                oldValue = ov != null ? ov : oldValue;
                                newValue = nv != null ? nv : newValue;

                                issueProperties.put(entry.getKey(), newValue);
                            }
                        }

                        _appendCustomColumnChange(sbHTMLChanges, sbTextChanges, entry.getKey(),
                                oldValue != null ? String.valueOf(oldValue) : "",
                                newValue != null ? String.valueOf(newValue) : "",
                                ccc, newIssue);
                    }
                }
            }
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

        return new ChangeSummary(issueListDef, issue, previous, issue.addComment(user, formattedComment.toString()),
                sbTextChanges.toString(), summary, action, issueProperties);
    }

    private static void _appendCustomColumnChange(StringBuilder sbHtml, StringBuilder sbText, String internalFieldName, String from, String to, CustomColumnConfiguration ccc, boolean newIssue)
    {
        CustomColumn cc = ccc.getCustomColumn(internalFieldName);

        // Record only fields with read permissions
        if (null != cc && cc.getPermission().equals(ReadPermission.class))
            _appendChange(sbHtml, sbText, cc.getCaption(), from, to, newIssue);
    }

    private static void _appendColumnChange(StringBuilder sbHTML, StringBuilder sbText, String fieldName, String from, String to, CustomColumnConfiguration ccc, boolean newIssue)
    {
        // Use custom caption if one is configured
        DomainProperty prop = ccc.getPropertyMap().get(fieldName);
        String caption = (prop != null && prop.getLabel() != null) ? prop.getLabel() : ColumnInfo.labelFromName(fieldName);

        _appendChange(sbHTML, sbText, caption, from, to, newIssue);
    }

    private static void _appendChange(StringBuilder sbHTML, StringBuilder sbText, String caption, String from, String to, boolean newIssue)
    {
        // Use custom caption if one is configured
        String encField = PageFlowUtil.filter(caption);
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
                sbText.append(StringUtils.isEmpty(from) ? "blank" : "\"" + from + "\"");
            }
            sbText.append(" to ");
            sbText.append(StringUtils.isEmpty(to) ? "blank" : "\"" + to + "\"");
            sbText.append("\n");
            String encFrom = PageFlowUtil.filter(from);
            String encTo = PageFlowUtil.filter(to);
            sbHTML.append("<tr><td>").append(encField).append("</td><td>").append(encFrom).append("</td><td>&raquo;</td><td>").append(encTo).append("</td></tr>\n");
        }
    }

    public static Issue relatedIssueCommentHandler(int issueId, int relatedIssueId, User user, boolean drop)
    {
        StringBuilder sb = new StringBuilder();
        Issue relatedIssue = IssueManager.getIssue(null, user, relatedIssueId);
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

    public void sendUpdateEmail(Container container, User user, String comment, ActionURL detailsURL, String change,
                                List<AttachmentFile> attachments) throws ServletException
    {
        // Skip the email if no comment and no public fields have changed, #17304
        String fieldChanges = getTextChanges();

        if (fieldChanges.isEmpty() && comment.isEmpty())
            return;

        final Set<User> allAddresses = getUsersToEmail(container, user, _issue, _prevIssue, _action);
        MailHelper.BulkEmailer emailer = new MailHelper.BulkEmailer(user);

        for (User recipient : allAddresses)
        {
            boolean hasPermission = container.hasPermission(recipient, ReadPermission.class);
            if (!hasPermission) continue;

            String to = recipient.getEmail();
            try
            {
                Issue.Comment lastComment = _issue.getLastComment();
                String messageId = "<" + _issue.getEntityId() + "." + lastComment.getCommentId() + "@" + AppProps.getInstance().getDefaultDomain() + ">";
                String references = messageId + " <" + _issue.getEntityId() + "@" + AppProps.getInstance().getDefaultDomain() + ">";
                MailHelper.MultipartMessage m = MailHelper.createMultipartMessage();
                m.addRecipients(Message.RecipientType.TO, MailHelper.createAddressArray(to));
                Address[] addresses = m.getAllRecipients();

                if (addresses != null && addresses.length > 0)
                {
                    IssueUpdateEmailTemplate template = EmailTemplateService.get().getEmailTemplate(IssueUpdateEmailTemplate.class, container);
                    template.init(_issue, detailsURL, change, comment, fieldChanges, allAddresses, attachments, recipient, _issueProperties);

                    m.setSubject(template.renderSubject(container));
                    template.renderSenderToMessage(m, container);
                    m.setHeader("References", references);
                    String body = template.renderBody(container);

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
                                    "  <meta itemprop=\"description\" content=\"View this " + PageFlowUtil.filter(IssueManager.getEntryTypeNames(container, _issueListDef.getName()).singularName) + "\"></meta>\n" +
                                    "</div>\n");
                    html.append("</body></html>");
                    m.setEncodedHtmlContent(html.toString());

                    emailer.addMessage(recipient.getEmail(), m);
                    Notification notification = createNotification(recipient, m,
                            "view " + IssueManager.getEntryTypeNames(container, _issueListDef.getName()).singularName,
                            new ActionURL(IssuesController.DetailsAction.class,container).addParameter("issueId", _issue.getIssueId()).getLocalURIString(false),
                            "issue:" + _issue.getIssueId(),
                            Issue.class.getName());

                    if (notification != null)
                    {
                        NotificationService.get().removeNotifications(container, notification.getObjectId(),
                                Collections.singletonList(notification.getType()), notification.getUserId());

                        NotificationService.get().addNotification(container, user, notification);
                    }
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
        emailer.start();
    }

    /**
     * Create a notification object from the message object
     */
    @Nullable
    private static Notification createNotification(User notifyUser, MailHelper.MultipartMessage m, String linkText,
                                                   String linkURL, String id, String type) throws IOException, MessagingException
    {
        if (!AppProps.getInstance().isExperimentalFeatureEnabled(NotificationMenuView.EXPERIMENTAL_NOTIFICATION_MENU))
            return null;

        Notification notification = new Notification();
        notification.setActionLinkText(linkText);
        notification.setActionLinkURL(linkURL);
        notification.setObjectId(id);
        notification.setType(type);
        notification.setUserId(notifyUser.getUserId());
        notification.setContent(m.getSubject());

        return notification;
    }

    /**
     * Builds the list of email addresses for notification based on the user
     * preferences and the explicit notification list.
     */
    private static Set<User> getUsersToEmail(Container c, User user, Issue issue, Issue prevIssue, Class<? extends Controller> action) throws ServletException
    {
        final Set<User> emailUsers = new HashSet<>();
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

        boolean selfSpam = !((IssueManager.NOTIFY_SELF_SPAM & IssueManager.getUserEmailPreferences(c, user.getUserId())) == 0);
        if (selfSpam)
            safeAddEmailUsers(emailUsers, user);
        else
            emailUsers.remove(user);

        return emailUsers;
    }

    private static void safeAddEmailUsers(Set<User> users, User user)
    {
        if (user != null && user.isActive())
            users.add(user);
    }
}
