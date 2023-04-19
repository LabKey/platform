package org.labkey.api.issues;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.query.ValidationError;
import org.labkey.api.security.Group;
import org.labkey.api.security.User;

import java.util.List;

/**
 * Provider for restricted issue lists
 */
public interface RestrictedIssueProvider
{
    /**
     * Tests whether a user should have access to a specific issue
     */
    boolean hasPermission(User user, Issue issue, List<Issue> relatedIssues, List<ValidationError> errors);

    /**
     * Methods to round trip setting related to a restricted issue list
     */
    void setRestrictedIssueTracker(Container c, String issueDefName, Boolean isRestricted);
    boolean isRestrictedIssueTracker(Container c, String issueDefName);

    void setRestrictedIssueListGroup(Container c, String issueDefName, @Nullable Group group);
    @Nullable Group getRestrictedIssueListGroup(Container c, String issueDefName);
}
