package org.labkey.api.action;

import org.labkey.api.collections.CaseInsensitiveHashSet;

import java.util.Set;
import java.util.function.Predicate;

/**
 * This interface provides a mechanism for MVC Form objects to control which fields will be bound.  By default, only simple
 * fields (no object path following) are allowed.  Forms may implement this interface to either a) allow any of the properties that
 * are restricted by default, or b) allow complex binding scenarios.
 */
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
