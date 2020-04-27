package org.labkey.api.issues;

public class IssuesDomainKindProperties
{
    protected String _issueDefName;
    protected String _singularItemName;
    protected String _pluralItemName;
    protected String _commentSortDirection = "ASC";
//    protected String _moveToContainer;
//    protected String _moveToContainerSelect; // RP TODO: Not really convinced these are needed

    protected Integer _assignedToGroup;
    protected Integer _assignedToUser;

    public IssuesDomainKindProperties()
    {
    }

    public IssuesDomainKindProperties(String name, String singularName, String pluralName, String commentSortDirection, Integer assignedToGroup, Integer assignedToUser)
    {
        _issueDefName = name;
        _singularItemName = singularName;
        _pluralItemName = pluralName;
        _commentSortDirection = commentSortDirection;
        _assignedToGroup = assignedToGroup;
        _assignedToUser = assignedToUser;
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

//    public String getMoveToContainer()
//    {
//        return _moveToContainer;
//    }
//
//    public void setMoveToContainer(String moveToContainer)
//    {
//        _moveToContainer = moveToContainer;
//    }
//
//    public String getMoveToContainerSelect()
//    {
//        return _moveToContainerSelect;
//    }
//
//    public void setMoveToContainerSelect(String moveToContainerSelect)
//    {
//        _moveToContainerSelect = moveToContainerSelect;
//    }

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
}
