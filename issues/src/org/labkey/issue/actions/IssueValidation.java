/*
 * Copyright (c) 2017 LabKey Corporation
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

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.validator.ColumnValidator;
import org.labkey.api.data.validator.ColumnValidators;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.issue.CustomColumnConfiguration;
import org.labkey.issue.IssuesController;
import org.labkey.issue.model.Issue;
import org.labkey.issue.model.IssueListDef;
import org.labkey.issue.model.IssueManager;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.validation.MapBindingResult;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Created by klum on 2/3/2017.
 */
public class IssueValidation
{
    public static void validateRequiredFields(@Nullable IssueListDef issueListDef, @Nullable CustomColumnConfiguration customColumnConfiguration,
                                              IssuesController.IssuesForm form, User user, Errors errors)
    {
        String requiredFields = "";
        final Map<String, String> newFields = form.getStrings();
        MapBindingResult requiredErrors = new MapBindingResult(newFields, errors.getObjectName());

        // handle custom field types
        if (issueListDef != null)
        {
            TableInfo tableInfo = issueListDef.createTable(user);
            for (Map.Entry<String, String> entry : newFields.entrySet())
            {
                // special case the assigned to field if the status is closed
                if (entry.getKey().equals("assignedTo") && Issue.statusCLOSED.equals(form.getBean().getStatus()))
                    continue;

                ColumnInfo col = tableInfo.getColumn(FieldKey.fromParts(entry.getKey()));
                if (col != null)
                {
                    for (ColumnValidator validator : ColumnValidators.create(col, null))
                    {
                        String msg = validator.validate(0, entry.getValue());
                        if (msg != null)
                            requiredErrors.reject(SpringActionController.ERROR_MSG, msg);
                    }

                    try
                    {
                        ConvertUtils.convert(entry.getValue(), col.getJavaClass());
                    }
                    catch (ConversionException e)
                    {
                        errors.reject(SpringActionController.ERROR_MSG, String.format("Could not convert '%s' to an %s", entry.getValue(), col.getJavaClass().getSimpleName()));
                    }
                }
            }
        }
        if (newFields.containsKey("comment"))
            validateRequired("comment", newFields.get("comment"), requiredFields, customColumnConfiguration, requiredErrors);

        // When resolving Duplicate, the 'duplicate' field should be set.
        if ("Duplicate".equals(newFields.get("resolution")))
            validateRequired("duplicate", newFields.get("duplicate"), "duplicate", customColumnConfiguration, requiredErrors);

        // when resolving, a resolution is always required
        if (newFields.containsKey("resolution"))
            validateRequired("resolution", newFields.get("resolution"), "resolution", customColumnConfiguration, requiredErrors);

        errors.addAllErrors(requiredErrors);
    }

    private static void validateRequired(String columnName, String value, String requiredFields, @Nullable CustomColumnConfiguration ccc, Errors errors)
    {
        if (requiredFields != null)
        {
            if (requiredFields.contains(columnName))
            {
                if (StringUtils.isEmpty(value) || StringUtils.isEmpty(value.trim()))
                {
                    String name = null;

                    // TODO: Not sure what to do here
                    if (ccc != null && ccc.shouldDisplay(columnName))
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
                    errors.reject(SpringActionController.ERROR_MSG, display + " is required.");
                }
            }
        }
    }

    public static void validateNotifyList(IssuesController.IssuesForm form, Errors errors)
    {
        User user;
        for (String username : StringUtils.split(StringUtils.trimToEmpty(form.getNotifyList()), ";\n"))
        {
            // NOTE: this "username" should be a user id but may be a psuedo-username (an assumed user which has default domain appended)
            //       or in the other special case this is an email address
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
                        errors.reject(SpringActionController.ERROR_MSG, message);
                    }
                }
            }
        }
    }

    public static void validateAssignedTo(IssuesController.IssuesForm form, Container container, Errors errors)
    {
        // here we check that the user is a valid assignee
        Integer userId = form.getBean().getAssignedTo();

        if (userId != null)
        {
            User user = UserManager.getUser(userId);
            // TODO: consider exposing IssueManager.canAssignTo
            if (user == null || !user.isActive() || !container.hasPermission(user, UpdatePermission.class))
                errors.reject(SpringActionController.ERROR_MSG, "An invalid user was set for the Assigned To");
        }
    }

    private static final int MAX_STRING_FIELD_LENGTH = 200;
    public static void validateStringFields(IssuesController.IssuesForm form, CustomColumnConfiguration ccc, Errors errors)
    {
        final Map<String, String> fields = form.getStrings();
            String lengthError = " cannot be longer than " + MAX_STRING_FIELD_LENGTH + " characters.";

        for (int i = 1; i <= 5; i++)
        {
            String name = "string" + i;

            if (fields.containsKey(name) && fields.get(name).length() > MAX_STRING_FIELD_LENGTH)
                errors.reject(SpringActionController.ERROR_MSG, ccc.getCaption(name) + lengthError);
        }
    }

    public static boolean relatedIssueHandler(Issue issue, User user, BindException errors)
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
                    errors.reject(SpringActionController.ERROR_MSG, "Invalid issue id in related string.");
                    return false;
                }
                if (issue.getIssueId() == relatedId)
                {
                    errors.reject(SpringActionController.ERROR_MSG, "As issue may not be related to itself");
                    return false;
                }

                // only need to verify that the related issue exists without regard to folder permissions (issue:27483), so just query
                // the issues.issues table directly.
                Issue related = new TableSelector(IssuesSchema.getInstance().getTableInfoIssues()).getObject(relatedId, Issue.class);
                if (related == null)
                {
                    errors.reject(SpringActionController.ERROR_MSG, "Related issue '" + relatedId + "' not found");
                    return false;
                }
                newRelatedIssues.add(relatedId);
            }
        }

        // Fetch from IssueManager to make sure the related issues are populated
        Issue originalIssue = IssueManager.getIssue(null, user, issue.getIssueId());
        Set<Integer> originalRelatedIssues = originalIssue == null ? Collections.emptySet() : originalIssue.getRelatedIssues();

        // Only check permissions if
        if (!originalRelatedIssues.equals(newRelatedIssues))
        {
            for (Integer relatedId : newRelatedIssues)
            {
                Issue related = IssueManager.getIssue(null, user, relatedId);
                if (related == null || !related.lookupContainer().hasPermission(user, ReadPermission.class))
                {
                    errors.reject(SpringActionController.ERROR_MSG, "User does not have Read Permission for related issue '" + relatedId + "'");
                    return false;
                }
            }
        }

        // this sets the collection of integer ids for all related issues
        issue.setRelatedIssues(newRelatedIssues);
        return true;
    }
}
