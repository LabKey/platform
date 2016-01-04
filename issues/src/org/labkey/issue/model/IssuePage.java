/*
 * Copyright (c) 2006-2015 LabKey Corporation
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

package org.labkey.issue.model;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;
import org.apache.commons.lang3.StringUtils;
import org.labkey.api.attachments.Attachment;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ViewContext;
import org.labkey.issue.ColumnType;
import org.labkey.issue.IssuesController;
import org.labkey.issue.IssuesController.DownloadAction;
import org.labkey.issue.model.IssueManager.CustomColumnConfiguration;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.mvc.Controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.labkey.api.util.PageFlowUtil.filter;


/**
 * User: Karl Lum
 * Date: Aug 31, 2006
 * Time: 1:07:36 PM
 */
public class IssuePage implements DataRegionSelection.DataSelectionKeyForm
{
    private final Container _c;
    private final User _user;
    private Issue _issue;
    private Issue _prevIssue;
    private Set<String> _issueIds = Collections.emptySet();
    private CustomColumnConfiguration _ccc;
    private Set<String> _editable = Collections.emptySet();
    private String _callbackURL;
    private BindException _errors;
    private Class<? extends Controller> _action;
    private String _body;
    private boolean _hasUpdatePermissions;
    private boolean _hasAdminPermissions;
    private String _requiredFields;
    private String _dataRegionSelectionKey;
    private boolean _print = false;
    private boolean _moveDestinations;

    public IssuePage(Container c, User user)
    {
        _c = c;
        _user = user;
    }

    public Issue getIssue()
    {
        return _issue;
    }

    public void setIssue(Issue issue)
    {
        _issue = issue;
    }

    public Issue getPrevIssue()
    {
        return _prevIssue;
    }

    public void setPrevIssue(Issue prevIssue)
    {
        _prevIssue = prevIssue;
    }

    public void setPrint(boolean print)
    {
        _print = print;
    }

    public boolean isPrint()
    {
        return _print;
    }
    
    public boolean isInsert()
    {
        return 0 == _issue.getIssueId();
    }

    public Set<String> getIssueIds()
    {
        return _issueIds;
    }

    public void setIssueIds(Set<String> issueIds)
    {
        _issueIds = issueIds;
    }

    public void setCustomColumnConfiguration(CustomColumnConfiguration ccc)
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

    public Class<? extends Controller> getAction()
    {
        return _action;
    }

    public void setAction(Class<? extends Controller> action)
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

    public void setUserHasAdminPermissions(boolean hasAdminPermissions)
    {
        _hasAdminPermissions = hasAdminPermissions;
    }

    public boolean getHasAdminPermissions()
    {
        return _hasAdminPermissions;
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

    public boolean hasMoveDestinations()
    {
        return _moveDestinations;
    }

    public void setMoveDestinations(boolean moveDestinations)
    {
        _moveDestinations = moveDestinations;
    }

    public String writeCustomColumn(ColumnType type, int tabIndex, boolean markIfRequired) throws IOException
    {
        if (_ccc.shouldDisplay(_user, type.getColumnName()))
        {
            String tableColumnName = type.getColumnName();
            final StringBuilder sb = new StringBuilder();

            sb.append("<tr><td class=\"labkey-form-label\">");
            sb.append(getLabel(type, markIfRequired));
            sb.append("</td><td>");

            // If custom column has pick list, then show select with keywords, otherwise input box
            if (_ccc.hasPickList(type.getColumnName()))
                sb.append(writeSelect(type, tabIndex));
            else if (type.isCustomInteger())
                sb.append(writeIntegerInput(type, tabIndex));
            else
                sb.append(writeInput(tableColumnName, type.getValue(getIssue()), tabIndex));

            sb.append("</td></tr>");

            return sb.toString();
        }

        return "";
    }


    // Field is always standard column name, which is HTML safe
    public String writeInput(String field, String value, String extra)
    {
        if (!isEditable(field))
            return filter(value, false, true);
        
        final StringBuilder sb = new StringBuilder();

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
    public String writeIntegerInput(ColumnType type, int tabIndex)
    {
        return writeInput(type.getColumnName(), type.getValue(getIssue()), "type=\"number\" maxlength=\"10\" tabIndex=\"" + tabIndex + "\" size=\"8\"");
    }

    public String writeInput(String field, String value, int tabIndex)
    {
        return writeInput(field, value, "tabIndex=\"" + tabIndex + "\"");
    }

    public String writeSelect(String field, String value, String display, String options, int tabIndex)
    {
        if (!isEditable(field))
        {
            return filter(display);
        }
        final StringBuilder sb = new StringBuilder();
        sb.append("<select id=\"");
        sb.append(PageFlowUtil.filter(field));
        sb.append("\" name=\"");
        sb.append(PageFlowUtil.filter(field));
        sb.append("\" tabindex=\"");
        sb.append(tabIndex);
        sb.append("\" onchange=\"LABKEY.setDirty(true);return true;\" >");

        if (null != display && 0 != display.length())
        {
            sb.append("<option value=\"");
            sb.append(filter(value));
            sb.append("\" selected>");
            sb.append(filter(display));
            sb.append("</option>");
        }
        sb.append(options);
        sb.append("</select>");
        return sb.toString();
    }

    public boolean hasKeywords(ColumnType type)
    {
        return !KeywordManager.getKeywords(_c, type).isEmpty();
    }

    public String writeSelect(ColumnType type, int tabIndex) throws IOException
    {
        String value = type.getValue(getIssue());
        return writeSelect(type.getColumnName(), value, value, KeywordManager.getKeywordOptions(_c, type), tabIndex);
    }

    public boolean isEditable(String field)
    {
        return _editable.contains(field);
    }

    public String getUserOptions()
    {
        Collection<User> members = IssueManager.getAssignedToList(_c, getIssue());
        StringBuilder select = new StringBuilder();
        select.append("<option value=\"\"></option>");

        for (User member : members)
        {
            select.append("<option value=").append(member.getUserId()).append(">");
            select.append(filter(member.getDisplayName(_user)));
            select.append("</option>\n");
        }

        return select.toString();
    }


    public String getNotifyListString(boolean asEmail)
    {
        if (asEmail)
        {
            List<ValidEmail> names = _issue.getNotifyListEmail();
            StringBuilder sb = new StringBuilder();
            String nl = "";
            for (ValidEmail e : names)
            {
                sb.append(nl);
                sb.append(e.getEmailAddress());
                nl = "\n";
            }
            return sb.toString();
        }
        else
        {
            List<String> names = _issue.getNotifyListDisplayNames(_user);

            return StringUtils.join(names, "\n");
        }
    }


    public String getNotifyList()
    {
        if (!isEditable("notifyList"))
        {
            return filter(getNotifyListString(false));
        }
        return "";
    }


    public String getLabel(ColumnType type, boolean markIfRequired)
    {
        return getLabel(type.getColumnName(), markIfRequired);
    }

    public String getLabel(String columnName, boolean markIfRequired)
    {
        ColumnInfo col = IssuesSchema.getInstance().getTableInfoIssues().getColumn(columnName);
        String name = null;

        if (_ccc.shouldDisplay(_user, columnName))
            name = _ccc.getCaption(columnName);
        else if (col != null)
            name = col.getLabel();

        String capitalizedColumnName;
        if (columnName != null && columnName.length() > 1)
            capitalizedColumnName = columnName.substring(0,1).toUpperCase() + columnName.substring(1); // capitalize
        else
            capitalizedColumnName = columnName;
        String label = PageFlowUtil.filter(StringUtils.isEmpty(name) ? capitalizedColumnName : name).replaceAll(" ", "&nbsp;");

        if (markIfRequired && _requiredFields != null && _requiredFields.contains(columnName.toLowerCase()))
            return label + "<span class=\"labkey-error\">*</span>";
        else
            return label;
    }

    public String writeDate(Date d)
    {
        if (null == d) return "";
        return DateUtil.formatDateTime(_c, d);
    }

    public String renderAttachments(ViewContext context, Issue.Comment parent)
    {
        List<Attachment> attachments = new ArrayList<>(AttachmentService.get().getAttachments(parent));

        StringBuilder sb = new StringBuilder();

        if (attachments.size() > 0)
        {
            sb.append("<table>");
            sb.append("<tr><td>&nbsp;</td></tr>");

            for (Attachment a : attachments)
            {
                Issue issue = parent.getIssue();
                sb.append("<tr><td>");
                sb.append("<a href=\"");
                sb.append(PageFlowUtil.filter(a.getDownloadUrl(DownloadAction.class).addParameter("issueId", issue.getIssueId())));
                sb.append("\" target=\"_blank\"><img src=\"");
                sb.append(context.getRequest().getContextPath());
                sb.append(PageFlowUtil.filter(a.getFileIcon()));
                sb.append("\">&nbsp;");
                sb.append(PageFlowUtil.filter(a.getName()));
                sb.append("</a>");
                sb.append("</td></tr>");
            }
            sb.append("</table>");
        }
        return sb.toString();
    }

    public String renderIssueIdLink(Integer id)
    {
        Issue issue = IssueManager.getIssue(null, id);
        Container c = issue != null ? issue.lookupContainer() : null;
        if (c != null && c.hasPermission(_user, ReadPermission.class))
        {
            IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(c);
            String title = String.format("%s %d: %s", PageFlowUtil.filter(names.singularName), issue.getIssueId(), PageFlowUtil.filter(issue.getTitle()));
            return String.format("<a href='%s' title='%s'>%d</a>", IssuesController.getDetailsURL(c, id, false), title, id);
        }
        else
        {
            return String.valueOf(id);
        }
    }

    public String renderRelatedIssues(Set<Integer> rels)
    {
        return renderDuplicates(rels);
    }

    public String renderDuplicates(Collection<Integer> dups)
    {
        StringBuilder sb = new StringBuilder();
        Joiner.on(", ").skipNulls().appendTo(sb, Collections2.transform(dups, new Function<Integer, String>()
        {
            @Override
            public String apply(Integer id)
            {
                return renderIssueIdLink(id);
            }
        }));
        return sb.toString();
    }

    // simple wrapper for renderDuplicates if issue.getDuplicates().isempty() is true. (if duplicates mechanism designed better this could disappear)
    public String renderDuplicate(int dup)
    {
        Collection<Integer> dups = Collections.singletonList(dup);
        return renderDuplicates(dups);
    }

}
