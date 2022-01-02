/*
 * Copyright (c) 2017-2019 LabKey Corporation
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
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.validator.ColumnValidator;
import org.labkey.api.data.validator.ColumnValidators;
import org.labkey.api.issues.Issue;
import org.labkey.api.issues.IssuesSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewServlet;
import org.labkey.issue.CustomColumnConfiguration;
import org.labkey.issue.model.IssueListDef;
import org.labkey.issue.model.IssueManager;
import org.labkey.issue.model.IssueObject;
import org.springframework.validation.Errors;
import org.springframework.validation.MapBindingResult;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Created by klum on 2/3/2017.
 */
public class IssueValidation
{
    public static void validateRequiredFields(@Nullable IssueListDef issueListDef, @Nullable CustomColumnConfiguration customColumnConfiguration,
                                              Issue issue, User user, Errors errors)
    {
        String requiredFields = "";
        final Map<String, Object> newFields = issue.getProperties();
        MapBindingResult requiredErrors = new MapBindingResult(newFields, errors.getObjectName());

        // handle custom field types
        if (issueListDef != null)
        {
            TableInfo tableInfo = issueListDef.createTable(user);
            for (Map.Entry<String, Object> entry : newFields.entrySet())
            {
                // special case the assigned to field if the status is closed
                if (entry.getKey().equalsIgnoreCase("assignedTo") && IssueObject.statusCLOSED.equals(issue.getStatus()))
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
                        errors.reject(SpringActionController.ERROR_MSG, ConvertHelper.getStandardConversionErrorMessage(entry.getValue(), col.getName(), col.getJavaClass()));
                    }
                }
            }
        }
        if (newFields.containsKey("comment"))
            validateRequired(user, "comment", String.valueOf(newFields.get("comment")), requiredFields, customColumnConfiguration, requiredErrors);

        // When resolving Duplicate, the 'duplicate' field should be set.
        if ("Duplicate".equals(newFields.get("resolution")))
            validateRequired(user, "duplicate", String.valueOf(newFields.get("duplicate")), "duplicate", customColumnConfiguration, requiredErrors);

        // when resolving, a resolution is always required
        if (newFields.containsKey("resolution"))
            validateRequired(user, "resolution", String.valueOf(newFields.get("resolution")), "resolution", customColumnConfiguration, requiredErrors);

        errors.addAllErrors(requiredErrors);
    }

    private static void validateRequired(User user, String columnName, String value, String requiredFields, @Nullable CustomColumnConfiguration ccc, Errors errors)
    {
        if (requiredFields != null)
        {
            if (requiredFields.contains(columnName))
            {
                if (StringUtils.isEmpty(value) || StringUtils.isEmpty(value.trim()))
                {
                    String name = null;

                    // TODO: Not sure what to do here
                    if (ccc != null && ccc.shouldDisplay(user, columnName))
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

    public static void validateNotifyList(IssueObject issue, Errors errors)
    {
        IssueObject.getNotifyListEmail(issue.getNotifyList(), errors);
    }

    public static void validateAssignedTo(Issue issue, Container container, Errors errors)
    {
        // here we check that the user is a valid assignee
        Integer userId = issue.getAssignedTo();

        if (userId != null)
        {
            User user = UserManager.getUser(userId);
            // TODO: consider exposing IssueManager.canAssignTo
            if (user == null || !user.isActive() || !container.hasPermission(user, UpdatePermission.class))
                errors.reject(SpringActionController.ERROR_MSG, "An invalid user was set for the Assigned To");
        }
    }

    public static void relatedIssueHandler(IssueObject issue, User user, Errors errors)
    {
        String textInput = issue.getRelated();
        Set<Integer> newRelatedIssues = new TreeSet<>();
        if (textInput != null)
        {
            String[] textValues = textInput.split("[\\s,;]+");
            int relatedId;
            // for each issue id we need to validate
            for (String relatedText : textValues)
            {
                relatedId = NumberUtils.toInt(relatedText.trim(), 0);
                if (relatedId == 0)
                {
                    errors.reject(SpringActionController.ERROR_MSG, "Invalid issue id in related string.");
                    return;
                }
                if (issue.getIssueId() == relatedId)
                {
                    errors.reject(SpringActionController.ERROR_MSG, "An issue may not be related to itself");
                    return;
                }

                // only need to verify that the related issue exists without regard to folder permissions (issue:27483), so just query
                // the issues.issues table directly.
                IssueObject related = new TableSelector(IssuesSchema.getInstance().getTableInfoIssues()).getObject(relatedId, IssueObject.class);
                if (related == null)
                {
                    errors.reject(SpringActionController.ERROR_MSG, "Related issue '" + relatedId + "' not found");
                    return;
                }
                newRelatedIssues.add(relatedId);
            }
        }

        // Fetch from IssueManager to make sure the related issues are populated
        IssueObject originalIssue = IssueManager.getIssue(null, user, issue.getIssueId());
        Set<Integer> originalRelatedIssues = originalIssue == null ? Collections.emptySet() : originalIssue.getRelatedIssues();

        // Only check permissions if
        if (!originalRelatedIssues.equals(newRelatedIssues))
        {
            for (Integer relatedId : newRelatedIssues)
            {
                IssueObject related = IssueManager.getIssue(null, user, relatedId);
                if (related == null || !related.lookupContainer().hasPermission(user, ReadPermission.class))
                {
                    errors.reject(SpringActionController.ERROR_MSG, "User does not have Read Permission for related issue '" + relatedId + "'");
                    return;
                }
            }

            // Issue 40178: Related Issues need to be in synch when related issues are deleted
            for (Integer originalRelatedId : originalRelatedIssues)
            {
                if (!newRelatedIssues.contains(originalRelatedId))
                {
                    IssueObject related = IssueManager.getIssue(null, user, originalRelatedId);
                    if (null != related)
                    {
                      related = ChangeSummary.relatedIssueCommentHandler(originalIssue.getIssueId(), related.getIssueId(), user, true );
                      IssueManager.saveIssue(user, related.lookupContainer(), related);
                    }
                }
            }
        }

        // this sets the collection of integer ids for all related issues
        issue.setRelatedIssues(newRelatedIssues);
    }

    public static void validateComments(Issue issue, Errors errors)
    {
        String comment = String.valueOf(issue.getProperties().get("comment"));
        if (!ViewServlet.validChars(comment))
            errors.reject(SpringActionController.ERROR_MSG, "Comment has invalid characters");
    }

    /**
     * Throw an exception if user does not have permission to update issue
     */
    public static void requiresInsertPermission(User user, IssueObject issue, Container c)
    {
        if (!hasInsertPermission(user, issue, c))
            throw new UnauthorizedException();
    }

    /**
     * Throw an exception if user does not have permission to update issue
     */
    public static void requiresUpdatePermission(User user, IssueObject issue, Container c)
    {
        if (!hasUpdatePermission(user, issue, c))
            throw new UnauthorizedException();
    }

    /**
     * Does this user have permission to update this issue?
     */
    public static boolean hasInsertPermission(User user, IssueObject issue, Container c)
    {
        return c.hasPermission(user, InsertPermission.class, getContextualRoles(user, issue, c));
    }

    /**
     * Does this user have permission to update this issue?
     */
    public static boolean hasUpdatePermission(User user, IssueObject issue, Container c)
    {
        return c.hasPermission(user, UpdatePermission.class, getContextualRoles(user, issue, c));
    }

    private static Set<Role> getContextualRoles(User user, IssueObject issue, Container c)
    {
        // we can't support AuthorRoles until we address issue: 36942
/*
        SecurityPolicy policy = SecurityPolicyManager.getPolicy(c);

        //36525
        if (issue.getCreatedBy() == user.getUserId() &&
                policy.hasPermission("issues", user, ReadPermission.class) &&
                policy.hasPermission("issues", user, InsertPermission.class))
        {
            roles.add(RoleManager.getRole(OwnerRole.class));
        }
*/

        return new HashSet<>();
    }
}
