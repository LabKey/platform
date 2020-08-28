package org.labkey.api.view;

import java.util.Collection;

public class NavTrees
{
    private final Collection<NavTree> _navTrees;

    public NavTrees(Collection<NavTree> navTrees)
    {
        _navTrees = navTrees;
    }

    public void add(NavTree navTree)
    {
        _navTrees.add(navTree);
    }
}
