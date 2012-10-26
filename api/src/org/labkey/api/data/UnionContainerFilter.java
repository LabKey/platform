package org.labkey.api.data;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * User: jeckels
 * Date: 10/25/12
 */
public class UnionContainerFilter extends ContainerFilter
{
    private final ContainerFilter[] _filters;

    public UnionContainerFilter(ContainerFilter... filters)
    {
        _filters = filters;
    }


    @Override
    protected Collection<String> getIds(Container currentContainer)
    {
        Set<String> result = new HashSet<String>();
        for (ContainerFilter filter : _filters)
        {
            result.addAll(filter.getIds(currentContainer));
        }
        return result;
    }

    @Override
    public Type getType()
    {
        return _filters[0].getType();
    }
}
