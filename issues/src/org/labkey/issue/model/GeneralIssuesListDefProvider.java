package org.labkey.issue.model;

import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.issues.IssuesListDefProvider;
import org.labkey.issue.query.IssueDefDomainKind;

/**
 * Created by davebradlee on 8/3/16.
 */
public class GeneralIssuesListDefProvider implements IssuesListDefProvider
{
    public String getName()
    {
        return IssueDefDomainKind.NAME;
    }
    public String getLabel()
    {
        return "General Issue Tracker";
    }
    public String getDescription()
    {
        return "General purpose issue tracker";
    }
    public DomainKind getDomainKind()
    {
        return PropertyService.get().getDomainKindByName(IssueDefDomainKind.NAME);
    }
}
