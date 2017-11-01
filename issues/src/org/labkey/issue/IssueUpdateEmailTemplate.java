/*
 * Copyright (c) 2010-2017 LabKey Corporation
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
package org.labkey.issue;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.emailTemplate.UserOriginatedEmailTemplate;
import org.labkey.api.view.ActionURL;
import org.labkey.issue.model.Issue;
import org.labkey.issue.model.IssueListDef;
import org.labkey.issue.model.IssueManager;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User: jeckels
 * Date: Aug 3, 2010
 */
public class IssueUpdateEmailTemplate extends UserOriginatedEmailTemplate
{
    protected static final String DEFAULT_SUBJECT =
            "^itemName^ #^issueId^, \"^title^,\" has been ^action^";
    protected static final String DEFAULT_BODY =
            "You can review this ^itemNameLowerCase^ here: ^detailsURL^\n" +
                    "Modified by: ^user^\n" +
                    "^modifiedFields^\n" +
                    "^comment^\n" +
                    "^attachments^";
    private List<ReplacementParam> _replacements = new ArrayList<>();
    private List<ReplacementParam> _allReplacements = new ArrayList<>();    // includes both static and dynamic custom field replacements
    private Issue _newIssue;
    private ActionURL _detailsURL;
    private String _change;
    private String _comment;
    private String _fieldChanges;
    private String _recipients;
    private String _attachments;
    private Map<String, Object> _issueProperties = new HashMap<>();

    public IssueUpdateEmailTemplate()
    {
        super("Issue update");
        setSubject(DEFAULT_SUBJECT);
        setBody(DEFAULT_BODY);
        setDescription("Sent to the users based on issue notification rules and settings after an issue has been edited or inserted.");
        setPriority(10);
        setEditableScopes(Scope.SiteOrFolder);

        _replacements.add(new ReplacementParam<Integer>("issueId", Integer.class, "Unique id for the issue")
        {
            public Integer getValue(Container c) {return _newIssue == null ? null : _newIssue.getIssueId();}
        });
        _replacements.add(new ReplacementParam<String>("detailsURL", String.class, "URL to get the details view for the issue")
        {
            public String getValue(Container c) {return _detailsURL == null ? null : _detailsURL.getURIString();}
        });
        _replacements.add(new ReplacementParam<String>("action", String.class, "Description of the type of action, like 'opened' or 'resolved'")
        {
            public String getValue(Container c) {return _change;}
        });
        _replacements.add(new ReplacementParam<String>("itemName", String.class, "Potentially customized singular item name, typically 'Issue'")
        {
            public String getValue(Container c) {return  getEntryTypeName(c, _newIssue).singularName;}
        });
        _replacements.add(new ReplacementParam<String>("itemNameLowerCase", String.class, "Potentially customized singular item name in lower case, typically 'issue'")
        {
            public String getValue(Container c) {return getEntryTypeName(c, _newIssue).singularName.toLowerCase();}
        });
        _replacements.add(new ReplacementParam<String>("itemNamePlural", String.class, "Potentially customized plural item name, typically 'Issues'")
        {
            public String getValue(Container c) {return getEntryTypeName(c, _newIssue).pluralName;}
        });
        _replacements.add(new ReplacementParam<String>("itemNamePluralLowerCase", String.class, "Potentially customized plural item name in lower case, typically 'issues'")
        {
            public String getValue(Container c) {return getEntryTypeName(c, _newIssue).pluralName.toLowerCase();}
        });
        _replacements.add(new IssueUpdateEmailTemplate.UserIdReplacementParam("user", "The display name of the user performing the operation")
        {
            public Integer getUserId(Container c)
            {
                return _newIssue.getModifiedBy();
            }
        });
        _replacements.add(new ReplacementParam<String>("comment", String.class, "The comment that was just added")
        {
            public String getValue(Container c)
            {
                return _comment;
            }
        });
        _replacements.add(new ReplacementParam<String>("attachments", String.class, "A List of attachments, if applicable")
        {
            public String getValue(Container c)
            {

                return (((_attachments == null) || (_attachments.length() == 0)) ? null : "\nAttachments: " + _attachments);
            }
        });
        _replacements.add(new ReplacementParam<String>("recipients", String.class, "All of the recipients of the email notification")
        {
            public String getValue(Container c)
            {
                return _recipients == null ? "user@domain.com" : _recipients;
            }
        });
        _replacements.add(new IssueUpdateEmailTemplate.StringReplacementParam("title", "The current title of the issue")
        {
            public String getStringValue(Container c)
            {
                return _newIssue.getTitle();
            }
        });
        _replacements.add(new IssueUpdateEmailTemplate.StringReplacementParam("status", "The current status of the issue")
        {
            public String getStringValue(Container c)
            {
                return _newIssue.getStatus();
            }
        });
        _replacements.add(new IssueUpdateEmailTemplate.StringReplacementParam("type", "The current type of the issue")
        {
            public String getStringValue(Container c)
            {
                return _newIssue.getType();
            }
        });
        _replacements.add(new IssueUpdateEmailTemplate.StringReplacementParam("area", "The current area of the issue")
        {
            public String getStringValue(Container c)
            {
                return _newIssue.getArea();
            }
        });
        _replacements.add(new ReplacementParam<String>("priority", String.class, "The current priority of the issue")
        {
            @Override
            public String getValue(Container c)
            {
                if (_newIssue == null || _newIssue.getPriority() == null)
                {
                    return null;
                }
                return _newIssue.getPriority().toString();
            }
        });
        _replacements.add(new IssueUpdateEmailTemplate.StringReplacementParam("milestone", "The current milestone of the issue")
        {
            public String getStringValue(Container c)
            {
                return _newIssue.getMilestone();
            }
        });
        _replacements.add(new IssueUpdateEmailTemplate.UserIdReplacementParam("openedBy", "The user that opened the issue")
        {
            public Integer getUserId(Container c)
            {
                return _newIssue.getCreatedBy();
            }
        });
        _replacements.add(new ReplacementParam<Date>("opened", Date.class, "The date that the issue was opened")
        {
            public Date getValue(Container c)
            {
                return _newIssue == null ? null : _newIssue.getCreated();
            }
        });
        _replacements.add(new ReplacementParam<Date>("resolved", Date.class, "The date that the issue was last resolved")
        {
            public Date getValue(Container c)
            {
                return _newIssue == null || _newIssue.getResolved() == null ? null : _newIssue.getResolved();
            }
        });
        _replacements.add(new IssueUpdateEmailTemplate.UserIdReplacementParam("resolvedBy", "The user who last resolved this issue")
        {
            public Integer getUserId(Container c)
            {
                return _newIssue.getResolvedBy();
            }
        });
        _replacements.add(new IssueUpdateEmailTemplate.StringReplacementParam("resolution", "The resolution type that was last used for this issue")
        {
            public String getStringValue(Container c)
            {
                return _newIssue.getResolution();
            }
        });
        _replacements.add(new ReplacementParam<Date>("closed", Date.class, "The date that the issue was last closed")
        {
            public Date getValue(Container c)
            {
                return _newIssue == null || _newIssue.getClosed() == null ? null : _newIssue.getClosed();
            }
        });
        _replacements.add(new IssueUpdateEmailTemplate.UserIdReplacementParam("closedBy", "The user who last closed this issue")
        {
            public Integer getUserId(Container c)
            {
                return _newIssue.getClosedBy();
            }
        });
        _replacements.add(new IssueUpdateEmailTemplate.StringReplacementParam("notifyList", "The current notification list for this issue")
        {
            public String getStringValue(Container c)
            {
                List<String> names = _newIssue.getNotifyListDisplayNames(null);
                return StringUtils.join(names, ";");
            }
        });
        _replacements.add(new ReplacementParam<String>("modifiedFields", String.class, "Summary of all changed fields with before and after values")
        {
            public String getValue(Container c)
            {
                return _fieldChanges;
            }
        });

        // modifiedFields

        _replacements.addAll(super.getValidReplacements());
    }

    private static IssueManager.EntryTypeNames getEntryTypeName(Container c, Issue issue)
    {
        String issueDefName = IssueListDef.DEFAULT_ISSUE_LIST_NAME;
        if (issue != null)
        {
            IssueListDef issueListDef = IssueManager.getIssueListDef(issue);
            if (issueListDef != null)
                issueDefName = issueListDef.getName();
        }
        return IssueManager.getEntryTypeNames(c, issueDefName);
    }

    private abstract class StringReplacementParam extends ReplacementParam<String>
    {
        public StringReplacementParam(String name, String description)
        {
            super(name, String.class, description);
        }

        @Override
        public final String getValue(Container c)
        {
            if (_newIssue == null)
            {
                return null;
            }

            return getStringValue(c);
        }

        protected abstract String getStringValue(Container c);
    }

    private abstract class UserIdReplacementParam extends ReplacementParam<String>
    {
        public UserIdReplacementParam(String name, String description)
        {
            super(name, String.class, description);
        }

        @Override
        public final String getValue(Container c)
        {
            if (_newIssue == null)
            {
                return null;
            }
            Integer userId = getUserId(c);
            if (userId == null)
            {
                return null;
            }
            User user = UserManager.getUser(userId.intValue());
            return user == null ? "(unknown)" : user.getFriendlyName();
        }

        protected abstract Integer getUserId(Container c);
    }

    public void init(Issue newIssue, ActionURL detailsURL, String change, String comment, String fieldChanges, Set<User> recipients,
                     List<AttachmentFile> attachments, User creator, Map<String, Object> issueProperties)
    {
        _newIssue = newIssue;
        _detailsURL = detailsURL;
        _change = change;
        _comment = comment;
        _fieldChanges = fieldChanges;
        setOriginatingUser(creator);
        _issueProperties = issueProperties;

        StringBuilder sb = new StringBuilder();
        String separator = "";
        for (User user : recipients)
        {
            sb.append(separator);
            separator = ", ";
            sb.append(user.getEmail());
        }
        _recipients = sb.toString();

        sb = new StringBuilder();
        separator = "";
        for (AttachmentFile attachment : attachments)
        {
            sb.append(separator);
            separator = ", ";
            sb.append(attachment.getFilename());
        }
        _attachments = sb.toString();
        Set<String> existingParams = _replacements.stream().map(ReplacementParam::getName).collect(Collectors.toSet());
        _allReplacements.addAll(_replacements);

        // inject any custom fields into the replacement parameters
        for (Map.Entry<String, Object> prop : _issueProperties.entrySet())
        {
            if (!existingParams.contains(prop.getKey()))
            {
                Object value = prop.getValue();

                if (value instanceof Integer)
                {
                    _allReplacements.add(new IssueUpdateEmailTemplate.CustomFieldReplacementParam<>(prop.getKey(), (Integer)value, Integer.class));
                }
                else if (value instanceof Date)
                {
                    _allReplacements.add(new IssueUpdateEmailTemplate.CustomFieldReplacementParam<>(prop.getKey(), (Date)value, Date.class));
                }
                else if (value instanceof Double)
                {
                    _allReplacements.add(new IssueUpdateEmailTemplate.CustomFieldReplacementParam<>(prop.getKey(), (Double)value, Double.class));
                }
                else
                {
                    _allReplacements.add(new IssueUpdateEmailTemplate.CustomFieldReplacementParam<>(prop.getKey(), String.valueOf(value), String.class));
                }
            }
        }
    }

    public List<ReplacementParam> getValidReplacements()
    {
        return _allReplacements.isEmpty() ? _replacements : _allReplacements;
    }

    @Override
    protected boolean isValidReplacement(String paramNameAndFormat)
    {
        // allowing everything because the underlying domain could change
        return true;
    }

    static class CustomFieldReplacementParam<Type> extends ReplacementParam<Type>
    {
        Type _value;

        public CustomFieldReplacementParam(@NotNull String name, Type value, Class<Type> valueType)
        {
            super(name, valueType, "");
            _value = value;
        }

        @Override
        public Type getValue(Container c)
        {
            return _value;
        }
    }
}
