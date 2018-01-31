/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.data.PHI;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.Results;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.defaults.DefaultValueService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.issue.ColumnType;
import org.labkey.issue.CustomColumnConfiguration;
import org.labkey.issue.IssuesController;
import org.labkey.issue.model.Issue.Comment;
import org.labkey.issue.query.IssuesQuerySchema;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.mvc.Controller;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toList;
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
    private Set<String> _visible = Collections.emptySet();
    private Set<String> _readOnly = Collections.emptySet();
    private String _callbackURL;
    private ActionURL _returnURL;
    private BindException _errors;
    private Class<? extends Controller> _action;
    private String _body;
    private boolean _hasUpdatePermissions;
    private boolean _hasAdminPermissions;
    private String _requiredFields;
    private String _dataRegionSelectionKey;
    private boolean _print = false;
    private boolean _moveDestinations;

    private IssueListDef _issueListDef;
    private RenderContext _renderContext;
    private TableInfo _tableInfo;
    private int _mode = DataRegion.MODE_DETAILS;
    private boolean _dirty;

    /**
     * A map of additional information to display on the issue's detail page. It will be displayed as a table row,
     * so the map is from the label to display to the cell contents (which can be a list of links or strings).
     */
    private Map<String, List<Pair<String, ActionURL>>> _additionalDetailInfo;

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

    public CustomColumnConfiguration getCustomColumnConfiguration()
    {
        return _ccc;
    }

    public Set<String> getVisibleFields()
    {
        return _visible;
    }

    public void setVisibleFields(Set<String> visible)
    {
        _visible = visible;
    }

    public void setReadOnlyFields(Set<String> readOnly)
    {
        _readOnly = readOnly;
    }

    public String getCallbackURL()
    {
        return _callbackURL;
    }

    public void setCallbackURL(String callbackURL)
    {
        _callbackURL = callbackURL;
    }

    public ActionURL getReturnURL()
    {
        return _returnURL;
    }

    public void setReturnURL(ActionURL returnURL)
    {
        _returnURL = returnURL;
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

    public IssueListDef getIssueListDef()
    {
        return _issueListDef;
    }

    public void setIssueListDef(IssueListDef issueListDef)
    {
        _issueListDef = issueListDef;
    }

    public int getMode()
    {
        return _mode;
    }

    public void setMode(int mode)
    {
        _mode = mode;
    }

    public boolean isDirty()
    {
        return _dirty;
    }

    public void setDirty(boolean dirty)
    {
        _dirty = dirty;
    }

    private RenderContext getRenderContext(ViewContext context)
    {
        if (_renderContext == null)
        {
            Map<String, Object> row = new CaseInsensitiveHashMap<>();
            IssueListDef issueListDef = getIssueListDef();
            Domain domain = issueListDef.getDomain(context.getUser());

            ObjectFactory factory = ObjectFactory.Registry.getFactory(Issue.class);
            factory.toMap(_issue, row);

            // apply any default values
            if (null != domain)
            {
                Map<DomainProperty, Object> domainDefaults = DefaultValueService.get().getDefaultValues(context.getContainer(), domain, context.getUser());
                for (Map.Entry<DomainProperty, Object> entry : domainDefaults.entrySet())
                {
                    if (row.get(entry.getKey()) == null)
                    {
                        row.put(entry.getKey().getName(), entry.getValue());
                    }
                }
            }

            // issue 27109 : need to add additional bindings to the render context so display values for lookup columns
            // don't render as broken lookups
            TableInfo table = getIssueTable(context);
            QueryDefinition queryDefinition = table.getUserSchema().getQueryDefForTable(_issueListDef.getName());
            if (queryDefinition != null)
            {
                List<ColumnInfo> selectCols = RenderContext.getSelectColumns(queryDefinition.getDisplayColumns(null, table), table);
                SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("IssueId"), _issue.getIssueId());

                try (Results results = new TableSelector(table, selectCols, filter, null).getResults())
                {
                    if (results.next())
                    {
                        Map<FieldKey, Object> rowMap = results.getFieldKeyRowMap();
                        for (Map.Entry<FieldKey, Object> entry : rowMap.entrySet())
                        {
                            row.put(entry.getKey().encode(), entry.getValue());
                        }
                    }
                }
                catch (SQLException e)
                {
                    throw new RuntimeException(e);
                }
            }
            row.putAll(_issue.getProperties());

            _renderContext = new RenderContext(context);
            _renderContext.setMode(_mode);
            _renderContext.setRow(row);
        }
        return  _renderContext;
    }

    private TableInfo getIssueTable(ViewContext context)
    {
        if (_tableInfo == null)
        {
            UserSchema userSchema = QueryService.get().getUserSchema(context.getUser(), context.getContainer(), IssuesQuerySchema.SCHEMA_NAME);
            _tableInfo = userSchema.getTable(_issueListDef.getName());
        }
        return _tableInfo;
    }

    public String renderColumn(DomainProperty prop, ViewContext context) throws IOException
    {
        return renderColumn(prop, context, true, false);
    }

    public String renderColumn(DomainProperty prop, ViewContext context, boolean visible, boolean readOnly) throws IOException
    {
        if (prop != null && shouldDisplay(prop, context.getContainer(), context.getUser()))
        {
            final StringBuilder sb = new StringBuilder();
            TableInfo table = getIssueTable(context);
            if (table != null)
            {
                ColumnInfo col = table.getColumn(FieldKey.fromParts(prop.getName()));
                if (col != null)
                {
                    try (Writer writer = new StringWriter())
                    {
                        writer.append("<tr>");
                        writer.append(renderLabel(prop, context));

                        if (visible)
                            writer.append(renderInput(prop, context, readOnly));

                        writer.append("</tr>");

                        sb.append(writer);
                    }
                    return sb.toString();
                }
            }
        }
        return "";
    }

    public String renderLabel(DomainProperty prop, ViewContext context) throws IOException
    {
        if (prop != null && shouldDisplay(prop, context.getContainer(), context.getUser()))
        {
            final StringBuilder sb = new StringBuilder();
            TableInfo table = getIssueTable(context);
            if (table != null)
            {
                ColumnInfo col = table.getColumn(FieldKey.fromParts(prop.getName()));
                if (col != null)
                {
                    DisplayColumn dc = col.getRenderer();
                    RenderContext renderContext = getRenderContext(context);

                    try (Writer writer = new StringWriter())
                    {
                        dc.renderDetailsCaptionCell(renderContext, writer, "lk-form-label");
                        sb.append(writer);
                    }
                    return sb.toString();
                }
            }
        }
        return "";
    }

    /**
     * Render column label only
     */
    public String renderLabel(String label) throws IOException
    {
        final StringBuilder sb = new StringBuilder();
        if (label != null)
        {
            sb.append("<td class=\"lk-form-label\">").append(label);
            if (label.length() < 100) //prevents silly-looking colons on extremely long labels (see: email prefs)
                sb.append(":");
            sb.append("</td>");

            return sb.toString();
        }
        return "";
    }

    public String renderInput(DomainProperty prop, ViewContext context, boolean readOnly) throws IOException
    {
        if (prop != null && shouldDisplay(prop, context.getContainer(), context.getUser()))
        {
            final StringBuilder sb = new StringBuilder();
            TableInfo table = getIssueTable(context);
            if (table != null)
            {
                ColumnInfo col = table.getColumn(FieldKey.fromParts(prop.getName()));
                if (col != null)
                {
                    // Issue 27672: text area input size too big for issue insert/update page
                    if ("textarea".equalsIgnoreCase(col.getInputType()))
                    {
                        col.setInputLength(40);
                        col.setInputRows(4);
                    }

                    DisplayColumn dc = col.getRenderer();
                    RenderContext renderContext = getRenderContext(context);
                    renderContext.setMode(readOnly ? DataRegion.MODE_DETAILS : getMode());

                    try (Writer writer = new StringWriter())
                    {
                        writer.append("<td>");
                        dc.render(renderContext, writer);
                        writer.append("</td>");
                        sb.append(writer);
                    }
                    return sb.toString();
                }
            }
        }
        return "";
    }

    public static boolean shouldDisplay(DomainProperty prop, Container container, User user)
    {
        // treat anything higher than NotPHI as needing special permission
        Class<? extends Permission> permission = prop.getPHI().isExportLevelAllowed(PHI.NotPHI) ? ReadPermission.class : InsertPermission.class;
        return container.hasPermission(user, permission);
    }

    // Field is always standard column name, which is HTML safe
    public String writeInput(String field, String value, String extra)
    {
        if (!isVisible(field))
            return filter(value, false, true);
        
        final StringBuilder sb = new StringBuilder();

        sb.append("<input name=\"");
        sb.append(field);
        sb.append("\" value=\"");
        sb.append(filter(value));
        sb.append("\" onchange=\"LABKEY.setDirty(true);return true;\" class=\"form-control\"");
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

    public String writeInput(String field, String value, int tabIndex)
    {
        return writeInput(field, value, "tabIndex=\"" + tabIndex + "\"");
    }

    public boolean isVisible(String field)
    {
        return _visible.contains(field);
    }

    public boolean isReadOnly(String field)
    {
        return _readOnly.contains(field);
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
        if (!isVisible("notifyList"))
        {
            return filter(getNotifyListString(false));
        }
        return "";
    }

    public List<String> getNotifyListCollection(boolean asEmail)
    {
        if (asEmail)
        {
            return _issue.getNotifyListEmail().stream()
                    .map(ValidEmail::toString)
                    .collect(toList());
        }
        else
        {
            return _issue.getNotifyListDisplayNames(_user);
        }
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
            return label + "&nbsp*";
        else
            return label;
    }

    public void setAdditionalDetailInfo(Map<String, List<Pair<String, ActionURL>>> additionalDetailInfo)
    {
        _additionalDetailInfo = additionalDetailInfo;
    }

    public Map<String, List<Pair<String, ActionURL>>> getAdditionalDetailInfo()
    {
        return _additionalDetailInfo;
    }

    public String writeDate(Date d)
    {
        if (null == d) return "";
        return DateUtil.formatDateTime(_c, d);
    }

    public String renderAttachments(ViewContext context, Comment comment)
    {
        List<Attachment> attachments = new ArrayList<>(AttachmentService.get().getAttachments(new CommentAttachmentParent(comment)));

        StringBuilder sb = new StringBuilder();

        if (attachments.size() > 0)
        {
            sb.append("<table>");
            sb.append("<tr><td>&nbsp;</td></tr>");

            for (Attachment a : attachments)
            {
                Issue issue = comment.getIssue();
                ActionURL download = IssuesController.getDownloadURL(issue, comment, a);
                sb.append("<tr><td>");
                sb.append("<a href=\"");
                sb.append(PageFlowUtil.filter(download));
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
        Issue issue = IssueManager.getIssue(null, _user, id);
        Container c = issue != null ? issue.lookupContainer() : null;
        if (c != null && c.hasPermission(_user, ReadPermission.class))
        {
            IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(c, _issueListDef.getName());
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

    public String renderAdditionalDetailInfo()
    {
        if (getAdditionalDetailInfo() != null && !getAdditionalDetailInfo().isEmpty())
        {
            StringBuilder sb = new StringBuilder();

            for (String label : getAdditionalDetailInfo().keySet())
            {
                StringBuilder cellContents = new StringBuilder();
                String sep = "";
                for (Pair<String, ActionURL> contentPair : getAdditionalDetailInfo().get(label))
                {
                    if (contentPair.second != null)
                        cellContents.append(sep).append("<a href=\"").append(contentPair.second).append("\">").append(contentPair.first).append("</a>");
                    else
                        cellContents.append(sep).append(contentPair.first);

                    sep = ", ";
                }

                sb.append("<tr><td class=\"lk-form-label\">");
                sb.append(label);
                sb.append("</td><td>");
                sb.append(cellContents.toString());
                sb.append("</td><tr>");

            }

            return sb.toString();
        }

        return null;
    }
}
