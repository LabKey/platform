package org.labkey.api.issues;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.security.User;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.util.HtmlString;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface Issue
{
    enum action
    {
        insert,
        update,
        close,
        reopen,
        resolve,
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
