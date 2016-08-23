package org.labkey.api.issues;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.security.User;
import org.labkey.api.view.NavTree;

import java.util.List;
import java.util.Map;

public interface IssueDetailHeaderLinkProvider
{
    String PARAM_PREFIX = "issue";

    @NotNull
    List<NavTree> getLinks(Domain IssueListDefDomain, int issueId, boolean issueIsOpen, Map<String, Object> extraProperties, Container container, User user);
}
