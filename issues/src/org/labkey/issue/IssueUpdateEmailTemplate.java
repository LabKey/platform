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
import org.labkey.api.collections.LabKeyCollectors;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

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
    private final List<ReplacementParam<?>> _replacements = new ArrayList<>();
    private final List<ReplacementParam<?>> _allReplacements = new ArrayList<>();    // includes both static and dynamic custom field replacements

    private Issue _newIssue;
    private ActionURL _detailsURL;
    private String _change;
    private String _comment;
    private String _fieldChanges;
    private String _recipients;
    private String _attachments;

    public IssueUpdateEmailTemplate()
    {
        super("Issue update", "Sent to the users based on issue notification rules and settings after an issue has been edited or inserted.", DEFAULT_SUBJECT, DEFAULT_BODY, ContentType.HTML, Scope.SiteOrFolder);

        Replacements replacements = new Replacements(_replacements);

        replacements.add("issueId", Integer.class, "Unique id for the issue", ContentType.Plain, c -> _newIssue == null ? null : _newIssue.getIssueId());
        replacements.add("detailsURL", String.class, "URL to get the details view for the issue", ContentType.Plain, c -> _detailsURL == null ? null : _detailsURL.getURIString());
        replacements.add("action", String.class, "Description of the type of action, like 'opened' or 'resolved'", ContentType.Plain, c -> _change);
        replacements.add("itemName", String.class, "Potentially customized singular item name, typically 'Issue'", ContentType.Plain, c -> getEntryTypeName(c, _newIssue).singularName);
        replacements.add("itemNameLowerCase", String.class, "Potentially customized singular item name in lower case, typically 'issue'", ContentType.Plain, c -> getEntryTypeName(c, _newIssue).singularName.toLowerCase());
        replacements.add("itemNamePlural", String.class, "Potentially customized plural item name, typically 'Issues'", ContentType.Plain, c -> getEntryTypeName(c, _newIssue).pluralName);
        replacements.add("itemNamePluralLowerCase", String.class, "Potentially customized plural item name in lower case, typically 'issues'", ContentType.Plain, c -> getEntryTypeName(c, _newIssue).pluralName.toLowerCase());
        replacements.add(new UserIdReplacementParam("user", "The display name of the user performing the operation", c -> _newIssue.getModifiedBy()));
        replacements.add("comment", String.class, "The comment that was just added", ContentType.Plain, c -> _comment);
        replacements.add("attachments", String.class, "A List of attachments, if applicable", ContentType.Plain, c -> (((_attachments == null) || (_attachments.length() == 0)) ? null : "\nAttachments: " + _attachments));
        replacements.add("recipients", String.class, "All of the recipients of the email notification", ContentType.Plain, c -> _recipients == null ? "user@domain.com" : _recipients);
        replacements.add(new StringReplacementParam("title", "The current title of the issue", c -> _newIssue.getTitle()));
        replacements.add(new StringReplacementParam("status", "The current status of the issue", c -> _newIssue.getStatus()));
        replacements.add(new StringReplacementParam("type", "The current type of the issue", c -> _newIssue.getProperty(Issue.Prop.type)));
        replacements.add(new StringReplacementParam("area", "The current area of the issue", c -> _newIssue.getProperty(Issue.Prop.area)));
        replacements.add("priority", String.class, "The current priority of the issue", ContentType.Plain, c -> {
            if (_newIssue == null)
            {
                return null;
            }
            return _newIssue.getProperty(Issue.Prop.priority);
        });
        replacements.add(new StringReplacementParam("milestone", "The current milestone of the issue", c -> _newIssue.getProperty(Issue.Prop.milestone)));
        replacements.add(new UserIdReplacementParam("openedBy", "The user that opened the issue", c -> _newIssue.getCreatedBy()));
        replacements.add("opened", Date.class, "The date that the issue was opened", ContentType.Plain, c -> _newIssue == null ? null : _newIssue.getCreated());
        replacements.add("resolved", Date.class, "The date that the issue was last resolved", ContentType.Plain, c -> _newIssue == null || _newIssue.getResolved() == null ? null : _newIssue.getResolved());
        replacements.add(new UserIdReplacementParam("resolvedBy", "The user who last resolved this issue", c -> _newIssue.getResolvedBy()));
        replacements.add(new StringReplacementParam("resolution", "The resolution type that was last used for this issue", c -> _newIssue.getResolution()));
        replacements.add("closed", Date.class, "The date that the issue was last closed", ContentType.Plain, c -> _newIssue == null || _newIssue.getClosed() == null ? null : _newIssue.getClosed());
        replacements.add(new UserIdReplacementParam("closedBy", "The user who last closed this issue", c -> _newIssue.getClosedBy()));
        replacements.add(new StringReplacementParam("notifyList", "The current notification list for this issue", c -> {
            List<String> names = _newIssue.getNotifyListDisplayNames(null);
            return StringUtils.join(names, ";"); })
        );
        replacements.add("modifiedFields", String.class, "Summary of all changed fields with before and after values", ContentType.Plain, c -> _fieldChanges);
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

    private final class StringReplacementParam extends ReplacementParam<String>
    {
        private final Function<Container, String> _valueGetter;

        public StringReplacementParam(String name, String description, Function<Container, String> valueGetter)
        {
            super(name, String.class, description, ContentType.Plain);
            _valueGetter = valueGetter;
        }

        @Override
        public final String getValue(Container c)
        {
            if (_newIssue == null)
            {
                return null;
            }

            return _valueGetter.apply(c);
        }
    }

    private final class UserIdReplacementParam extends ReplacementParam<String>
    {
        private final Function<Container, Integer> _userIdGetter;

        public UserIdReplacementParam(String name, String description, Function<Container, Integer> userIdGetter)
        {
            super(name, String.class, description, ContentType.Plain);
            _userIdGetter = userIdGetter;
        }

        @Override
        public final String getValue(Container c)
        {
            if (_newIssue == null)
            {
                return null;
            }
            Integer userId = _userIdGetter.apply(c);
            if (userId == null)
            {
                return null;
            }
            User user = UserManager.getUser(userId.intValue());
            return user == null ? "(unknown)" : user.getFriendlyName();
        }
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
        Set<String> existingParams = _replacements.stream()
            .map(ReplacementParam::getName)
            .collect(LabKeyCollectors.toCaseInsensitiveHashSet());

        Replacements allReplacements = new Replacements(_allReplacements);
        _replacements.forEach(allReplacements::add);

        // inject any custom fields into the replacement parameters
        for (Map.Entry<String, Object> prop : issueProperties.entrySet())
        {
            if (!existingParams.contains(prop.getKey()))
            {
                Object value = prop.getValue();

                if (value instanceof Integer)
                {
                    allReplacements.add(new CustomFieldReplacementParam<>(prop.getKey(), (Integer)value, Integer.class));
                }
                else if (value instanceof Date)
                {
                    allReplacements.add(new CustomFieldReplacementParam<>(prop.getKey(), (Date)value, Date.class));
                }
                else if (value instanceof Double)
                {
                    allReplacements.add(new CustomFieldReplacementParam<>(prop.getKey(), (Double)value, Double.class));
                }
                else
                {
                    allReplacements.add(new CustomFieldReplacementParam<>(prop.getKey(), String.valueOf(value), String.class));
                }
            }
        }
    }

    @Override
    protected void addCustomReplacements(Replacements replacements)
    {
        super.addCustomReplacements(replacements);
        (_allReplacements.isEmpty() ? _replacements : _allReplacements).forEach(replacements::add);
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
            super(name, valueType, "", ContentType.Plain);
            _value = value;
        }

        @Override
        public Type getValue(Container c)
        {
            return _value;
        }
    }
}
