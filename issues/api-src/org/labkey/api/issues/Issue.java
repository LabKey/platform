package org.labkey.api.issues;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.Pair;
import org.labkey.api.view.UnauthorizedException;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface Issue
{
    enum action
    {
        insert(InsertPermission.class),
        update(UpdatePermission.class),
        close(UpdatePermission.class),
        reopen(UpdatePermission.class),
        resolve(UpdatePermission.class);

        private final Class<? extends Permission> _requiredPermission;

        action(Class<? extends Permission> requiredPermission)
        {
            _requiredPermission = requiredPermission;
        }

        // verify permission for the action
        public void checkPermission(Container c, User user, Issue issue)
        {
            if (!c.hasPermission(user, _requiredPermission))
                throw new UnauthorizedException();
        }
    }

    int getIssueId();
    String getContainerId();

    String getTitle();
    String getStatus();
    String getResolution();
    Collection<Integer> getDuplicates();
    @NotNull Set<Integer> getRelatedIssues();
    String getRelated();
    Collection<Issue.Comment> getComments();
    Integer getIssueDefId();
    String getIssueDefName();
    List<String> getNotifyListDisplayNames(User user);
    List<ValidEmail> getNotifyListEmail();
    List<Pair<User, ValidEmail>> getNotifyListUserEmail();
    Map<String, Object> getProperties();

    Integer getAssignedTo();
    int getCreatedBy();
    Date getCreated();
    int getModifiedBy();
    Date getModified();
    Integer getResolvedBy();
    Date getResolved();
    Integer getClosedBy();
    Date getClosed();

    interface Comment
    {
        Issue getIssue();
        int getCommentId();
        int getCreatedBy();
        Date getCreated();
        int getModifiedBy();
        Date getModified();
        HtmlString getHtmlComment();
        String getContainerId();
    }
}
