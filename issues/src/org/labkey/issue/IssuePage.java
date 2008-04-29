package org.labkey.issue;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbCache;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.Cache;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.DateUtil;
import static org.labkey.api.util.PageFlowUtil.filter;
import org.labkey.api.view.ViewContext;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentParent;
import org.labkey.issue.model.Issue;
import org.labkey.issue.model.IssueManager;
import org.labkey.issue.model.IssueManager.*;
import org.springframework.validation.BindException;
import java.io.IOException;
import java.util.*;
import java.sql.SQLException;

/**
 * User: Karl Lum
 * Date: Aug 31, 2006
 * Time: 1:07:36 PM
 */
public class IssuePage implements DataRegionSelection.DataSelectionKeyForm
{
    private Issue _issue;
    private List<Issue> _issueList = Collections.emptyList();
    private IssueManager.CustomColumnConfiguration _ccc;
    private Set<String> _editable = Collections.emptySet();
    private String _callbackURL;
    private BindException _errors;
    private String _action;
    private String _body;
    private boolean _hasUpdatePermissions;
    private String _requiredFields;
    private String _dataRegionSelectionKey;

    public Issue getIssue()
    {
        return _issue;
    }

    public void setIssue(Issue issue)
    {
        _issue = issue;
    }
    
    public List<Issue> getIssueList()
    {
        return _issueList;
    }

    public void setIssueList(List<Issue> issueList)
    {
        _issueList = issueList;
    }

    public IssueManager.CustomColumnConfiguration getCustomColumnConfiguration()
    {
        return _ccc;
    }

    public void setCustomColumnConfiguration(IssueManager.CustomColumnConfiguration ccc)
    {
        _ccc = ccc;
    }

    public Set<String> getEditable()
    {
        return _editable;
    }

    public void setEditable(Set<String> editable)
    {
        _editable = editable;
    }

    public String getCallbackURL()
    {
        return _callbackURL;
    }

    public void setCallbackURL(String callbackURL)
    {
        _callbackURL = callbackURL;
    }

    public BindException getErrors()
    {
        return _errors;
    }

    public void setErrors(BindException errors)
    {
        _errors = errors;
    }

    public String getAction()
    {
        return _action;
    }

    public void setAction(String action)
    {
        _action = action;
    }

    public String getBody()
    {
        return _body;
    }

    public void setBody(String body)
    {
        _body = body;
    }

    public void setUserHasUpdatePermissions(boolean hasUpdatePermissions)
    {
        _hasUpdatePermissions = hasUpdatePermissions;
    }

    public boolean getHasUpdatePermissions()
    {
        return _hasUpdatePermissions;
    }

    public String getRequiredFields()
    {
        return _requiredFields;
    }

    public void setRequiredFields(String requiredFields)
    {
        _requiredFields = requiredFields;
    }

    public String getDataRegionSelectionKey()
    {
        return _dataRegionSelectionKey;
    }

    public void setDataRegionSelectionKey(String dataRegionSelectionKey)
    {
        _dataRegionSelectionKey = dataRegionSelectionKey;
    }

    public String writeCustomColumn(String container, String tableColumnName, String value, int keywordType) throws IOException
    {
        final String caption = _ccc.getColumnCaptions().get(tableColumnName);

        if (null != caption)
        {
            final StringBuffer sb = new StringBuffer();

            sb.append("<tr><td class=\"ms-searchform\">");
            sb.append(getLabel(tableColumnName));
            sb.append("</td><td class=\"normal\">");

            // If custom column has pick list, then show select with keywords, otherwise input box
            if (_ccc.getPickListColumns().contains(tableColumnName))
                sb.append(writeSelect(tableColumnName, value, getKeywordOptions(container, keywordType, true)));
            else if (tableColumnName.startsWith("int"))
                sb.append(writeIntegerInput(tableColumnName, value));
            else
                sb.append(writeInput(tableColumnName, value));

            sb.append("</td></tr>");

            return sb.toString();
        }

        return "";
    }

    public String writeInput(String field, String value, String extra)
    {
        if (!isEditable(field))
        {
            return filter(value);
        }
        final StringBuffer sb = new StringBuffer();

        sb.append("<input name=\"");
        sb.append(field);
        sb.append("\" value=\"");
        sb.append(filter(value));
        sb.append("\" onchange=\"LABKEY.setDirty(true);return true;");
        if (null == extra)
            sb.append("\">");
        else
        {
            sb.append("\" ");
            sb.append(extra);
            sb.append(">");
        }
        return sb.toString();
    }

    // Limit number of characters in an integer field
    public String writeIntegerInput(String field, String value)
    {
        return writeInput(field, value, "maxlength=\"10\" size=\"8\"");
    }

    public String writeInput(String field, String value)
    {
        return writeInput(field, value, null);
    }

    public String writeSelect(String field, String value, String display, String options)
    {
        if (!isEditable(field))
        {
            return filter(display);
        }
        final StringBuffer sb = new StringBuffer();
        sb.append("<select id=\"");
        sb.append(field);
        sb.append("\" name=\"");
        sb.append(field);
        sb.append("\" onchange=\"LABKEY.setDirty(true);return true;\" >");

        if (null != display && 0 != display.length())
        {
            sb.append("<option value=\"");
            sb.append(value);
            sb.append("\" selected>");
            sb.append(display);
            sb.append("</option>");
        }
        sb.append(options);
        sb.append("</select>"); // <script>document.getElementById('" + name + "').value = '" + value + "';</script>");

        return sb.toString();
    }

    public String writeSelect(String field, String value, String options) throws IOException
    {
        return writeSelect(field, value, value, options);
    }

    public boolean isEditable(String field)
    {
        return _editable.contains(field);
    }

    protected String getKeywordOptions(String container, int type, boolean allowBlank)
    {
        assert type != IssuesController.ISSUE_NONE;

        String cacheKey = container + "/" + type;
        String s = (String) DbCache.get(IssuesSchema.getInstance().getTableInfoIssueKeywords(), cacheKey);

        if (null != s)
            return s;

        Keyword[] keywords = IssueManager.getKeywords(container, type);
        StringBuffer sb = new StringBuffer(keywords.length * 30);
        if (allowBlank)
            sb.append("<option></option>\n");
        for (Keyword keyword : keywords)
        {
            sb.append("<option>");
            sb.append(PageFlowUtil.filter(keyword.getKeyword()));
            sb.append("</option>\n");
        }
        s = sb.toString();
        DbCache.put(IssuesSchema.getInstance().getTableInfoIssueKeywords(), cacheKey, s, 10 * Cache.MINUTE);
        return s;
    }

    protected String getKeywordOptionsWithDefault(Container c, int type, String[] standardValues, String def) throws SQLException
    {
        String options = getKeywordOptions(c.getId(), type, false);

        if (0 == options.length())
        {
            // First reference in this container... save away standard values
            for (String value : standardValues)
                IssueManager.addKeyword(c, type, value);

            IssueManager.setKeywordDefault(c, type, def);

            options = getKeywordOptions(c.getId(), type, false);
        }

        return options;
    }

    public String getTypeOptions(String container)
    {
        return getKeywordOptions(container, IssuesController.ISSUE_TYPE, true);
    }

    public String getAreaOptions(String container)
    {
        return getKeywordOptions(container, IssuesController.ISSUE_AREA, true);
    }

    public String getMilestoneOptions(String container)
    {
        return getKeywordOptions(container, IssuesController.ISSUE_MILESTONE, true);
    }

    public String getResolutionOptions(Container c) throws SQLException
    {
        return getKeywordOptionsWithDefault(c, IssuesController.ISSUE_RESOLUTION, new String[]{"Fixed", "Duplicate", "Won't Fix", "Not Repro", "By Design"}, "Fixed");
    }

    public String getPriorityOptions(Container c) throws SQLException
    {
        return getKeywordOptionsWithDefault(c, IssuesController.ISSUE_PRIORITY, new String[]{"0", "1", "2", "3", "4"}, "3");
    }

    public String getUserOptions(Container c, Issue issue, ViewContext context)
    {
        User[] members = IssueManager.getAssignedToList(c, issue);

        StringBuffer select = new StringBuffer();
        select.append("<option value=\"\"></option>");
        for (User member : members)
        {
            select.append("<option value=").append(member.getUserId()).append(">");
            select.append(member.getDisplayName(context));
            select.append("</option>\n");
        }

        return select.toString();
    }

    public String getNotifyListString()
    {
        final String notify = _issue.getNotifyList();
        if (notify != null)
            return notify.replace(';', '\n');
        return "";
    }

    public String getNotifyList(Container c, Issue issue)
    {
        if (!isEditable("notifyList"))
        {
            return filter(getNotifyListString());
        }
        final StringBuilder sb = new StringBuilder();

        sb.append("<script type=\"text/javascript\">LABKEY.requiresScript('completion.js');</script>");
        sb.append("<textarea name=\"notifyList\" id=\"notifyList\" cols=\"30\" rows=\"4\"" );
        sb.append(" onKeyDown=\"return ctrlKeyCheck(event);\"");
        sb.append(" onBlur=\"hideCompletionDiv();\"");
        sb.append(" onchange=\"LABKEY.setDirty(true);return true;\"");
        sb.append(" autocomplete=\"off\"");
        sb.append(" onKeyUp=\"return handleChange(this, event, 'completeUser.view?issueId=");
        sb.append(getIssue().getIssueId());
        sb.append("&amp;prefix=');\"");
        sb.append(">");
        sb.append(getNotifyListString());
        sb.append("</textarea>");

        return sb.toString();
    }

    public String getLabel(String columnName)
    {
        ColumnInfo col = IssuesSchema.getInstance().getTableInfoIssues().getColumn(columnName);
        String name = null;
        if (_ccc.getColumnCaptions().containsKey(columnName))
            name = _ccc.getColumnCaptions().get(columnName).replace(" ", "&nbsp;");
        else if (col != null)
            name = col.getCaption().replace(" ", "&nbsp;");

        if (name != null)
        {
            if (_requiredFields != null && _requiredFields.indexOf(columnName.toLowerCase()) != -1)
                    return "<span class=\"labkey-error\">*</span>&nbsp;" + name;
            return name;
        }
        return columnName;
    }

    public String _toString(Object a)
    {
        return null == a ? "" : a.toString();
    }

    public String writeDate(Date d)
    {
        if (null == d) return "";
        return DateUtil.formatDate(d);
    }

    public String renderAttachments(ViewContext context, AttachmentParent parent) throws SQLException
    {
        Attachment[] attachments = AttachmentService.get().getAttachments(parent);
        StringBuffer sb = new StringBuffer();
        boolean canEdit = isEditable("attachments");

        if (attachments.length > 0)
        {
            sb.append("<table>");
            sb.append("<tr><td>&nbsp;</td></tr>");
            for (Attachment a : attachments)
            {
                sb.append("<tr><td>");
                if (!canEdit)
                {
                    sb.append("<a class=\"link\" href=\"");
                    sb.append(PageFlowUtil.filter(a.getDownloadUrl("issues")));
                    sb.append("\"><img border=0 src=\"");
                    sb.append(context.getRequest().getContextPath());
                    sb.append(PageFlowUtil.filter(a.getFileIcon()));
                    sb.append("\">&nbsp;");
                    sb.append(PageFlowUtil.filter(a.getName()));
                    sb.append("</a>");
                }
                else
                {
                    sb.append("<img border=0 src=\"");
                    sb.append(context.getRequest().getContextPath());
                    sb.append(PageFlowUtil.filter(a.getFileIcon()));
                    sb.append("\">&nbsp;");
                    sb.append(PageFlowUtil.filter(a.getName()));
                }
                sb.append("</td></tr>");
            }
            sb.append("</table>");
        }
        return sb.toString();
    }
}
