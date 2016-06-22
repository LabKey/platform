package org.labkey.issue.actions;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiService;
import org.labkey.issue.CustomColumnConfiguration;
import org.labkey.issue.IssuesController;
import org.labkey.issue.model.CustomColumn;
import org.labkey.issue.model.Issue;
import org.labkey.issue.model.IssueListDef;
import org.springframework.web.servlet.mvc.Controller;

import java.util.Map;
import java.util.Set;

import static org.labkey.api.action.SpringActionController.getActionName;

/**
 * Created by klum on 5/10/2016.
 */
public class ChangeSummary
{
    private Issue.Comment _comment;
    private String _textChanges;
    private String _summary;
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

    static public ChangeSummary createChangeSummary(IssueListDef issueListDef, Issue issue, Issue previous, @Nullable Issue duplicateOf,
                                                    Container container,
                                                    User user,
                                                    Class<? extends Controller> action,
                                                    String comment,
                                                    CustomColumnConfiguration ccc,
                                                    User currentUser)
    {
        StringBuilder sbHTMLChanges = new StringBuilder();
        StringBuilder sbTextChanges = new StringBuilder();
        String summary = null;

        if (!action.equals(IssuesController.InsertAction.class) && !action.equals(IssuesController.UpdateAction.class))
        {
            summary = getActionName(action).toLowerCase();

            if (action.equals(IssuesController.ResolveAction.class))
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

            String prevPriStringVal = previous.getPriority() == null ? "" : String.valueOf(previous.getPriority());
            String priStringVal = issue.getPriority() == null ? "" : String.valueOf(issue.getPriority());

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
            _appendChange(sbHTMLChanges, sbTextChanges, "Priority", prevPriStringVal, priStringVal, ccc, newIssue);
            _appendChange(sbHTMLChanges, sbTextChanges, "Milestone", previous.getMilestone(), issue.getMilestone(), ccc, newIssue);
            _appendChange(sbHTMLChanges, sbTextChanges, "Related", StringUtils.join(previous.getRelatedIssues(), ", "), StringUtils.join(issue.getRelatedIssues(), ", "), ccc, newIssue);

            Map<String, Object> oldProps = previous.getExtraProperties();
            for (Map.Entry<String, Object> entry : issue.getExtraProperties().entrySet())
            {
                if (!_standardFields.contains(entry.getKey()))
                {
                    Object oldValue = oldProps.get(entry.getKey());

                    _appendCustomColumnChange(sbHTMLChanges, sbTextChanges, entry.getKey(),
                            oldValue != null ? String.valueOf(oldValue) : "",
                            entry.getValue() != null ? String.valueOf(entry.getValue()) : "",
                            ccc, newIssue);
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

        return new ChangeSummary(issue.addComment(user, formattedComment.toString()), sbTextChanges.toString(), summary);
    }

    private static void _appendCustomColumnChange(StringBuilder sbHtml, StringBuilder sbText, String internalFieldName, String from, String to, CustomColumnConfiguration ccc, boolean newIssue)
    {
        CustomColumn cc = ccc.getCustomColumn(internalFieldName);

        // Record only fields with read permissions
        if (null != cc && cc.getPermission().equals(ReadPermission.class))
            _appendChange(sbHtml, sbText, internalFieldName, cc, from, to, newIssue);
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
}
