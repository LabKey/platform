package org.labkey.api.action;

import org.labkey.api.collections.CaseInsensitiveHashSet;

import java.util.Set;
import java.util.function.Predicate;

public interface HasAllowBindParameter
{
    Predicate<String> allowBindParameter();

    Set<String> disallowed = new CaseInsensitiveHashSet("class","container","containerid","request","response","user","viewcontext");
    Predicate<String> defaultPredicate = (name) ->
    {
        if (name.startsWith(SpringActionController.FIELD_MARKER))
            name = name.substring(SpringActionController.FIELD_MARKER.length());
        return !name.contains(".") && !disallowed.contains(name);
    };

    static Predicate<String>  getDefaultPredicate()
    {
        return defaultPredicate;
    }
}
