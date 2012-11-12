package org.labkey.query.sql;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: matthewb
 * Date: 2012-11-07
 * Time: 3:07 PM
 */
public class ReferenceCount
{
    private final IdentityHashMap<Object,Object> _refs = new IdentityHashMap<Object, Object>();
    private final Set<Class> _legal;

    public ReferenceCount(Set<Class> legal)
    {
        _legal = legal;
    }

    public int increment(@NotNull Object referant)
    {
        assert null==_legal || _legal.contains(referant.getClass());
        _refs.put(referant,referant);
        return _refs.size();
    }

    public int decrement(@NotNull Object referant)
    {
        assert null==_legal || _legal.contains(referant.getClass());
        assert _refs.containsKey(referant);
        _refs.remove(referant);
        return _refs.size();
    }

    public int count()
    {
        return _refs.size();
    }

    public boolean isReferencedBy(Object referant)
    {
        return _refs.containsKey(referant);
    }

    @Override
    public String toString()
    {
        return "" + count() + " reference(s)";
    }
}
