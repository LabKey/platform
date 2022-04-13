package org.labkey.api.issues;

public class IssuesDomainKindProperties
{
    private String _issueDefName;
    private String _singularItemName;
    private String _pluralItemName;
    private String _commentSortDirection;

    private Integer _assignedToGroup;
    private Integer _assignedToUser;
    private String _relatedFolderName;

    public IssuesDomainKindProperties()
    {}

    public IssuesDomainKindProperties(String name, String singularName, String pluralName, String commentSortDirection,
                                      Integer assignedToGroup, Integer assignedToUser, String relatedFolderName)
    {
        _issueDefName = name;
        _singularItemName = singularName;
        _pluralItemName = pluralName;
        _commentSortDirection = commentSortDirection;
        _assignedToGroup = assignedToGroup;
        _assignedToUser = assignedToUser;
        _relatedFolderName = relatedFolderName;
    }

    public String getIssueDefName()
    {
        return _issueDefName;
    }

    public void setIssueDefName(String issueDefName)
    {
        _issueDefName = issueDefName;
    }

    public String getSingularItemName()
    {
        return _singularItemName;
    }

    public void setSingularItemName(String singularItemName)
    {
        _singularItemName = singularItemName;
    }

    public String getPluralItemName()
    {
        return _pluralItemName;
    }

    public void setPluralItemName(String pluralItemName)
    {
        _pluralItemName = pluralItemName;
    }

    public String getCommentSortDirection()
    {
        return _commentSortDirection;
    }

    public void setCommentSortDirection(String commentSortDirection)
    {
        _commentSortDirection = commentSortDirection;
    }

    public Integer getAssignedToGroup()
    {
        return _assignedToGroup;
    }

    public void setAssignedToGroup(Integer assignedToGroup)
    {
        _assignedToGroup = assignedToGroup;
    }

    public Integer getAssignedToUser()
    {
        return _assignedToUser;
    }

    public void setAssignedToUser(Integer assignedToUser)
    {
        _assignedToUser = assignedToUser;
    }

    public String getRelatedFolderName()
    {
        return _relatedFolderName;
    }

    public void setRelatedFolderName(String relatedFolderName)
    {
        _relatedFolderName = relatedFolderName;
    }
}
