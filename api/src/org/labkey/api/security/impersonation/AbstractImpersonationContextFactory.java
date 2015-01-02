package org.labkey.api.security.impersonation;

import javax.servlet.http.HttpSession;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * User: adam
 * Date: 12/31/2014
 * Time: 4:36 PM
 */

/** Base class for session handling */
public class AbstractImpersonationContextFactory
{
    private final static List<String> _sessionAttributesToStash = new CopyOnWriteArrayList<>();

    /**
     * Register a session attribute that should always be removed from the session before impersonation (and restored to the
     * session after impersonation is complete). Obvious candidates are session attributes whose values are affected by
     * permissions changes. See #22220.
     */
    public static void registerSessionAttributeToStash(String attributeName)
    {
        _sessionAttributesToStash.add(attributeName);
    }

    /** Don't remove/rename without updating PipelineJobMarshaller.getXStream() */
    private final Map<String, Object> _adminSessionAttributes = new HashMap<>();

    // Stash all attributes in the session
    protected void stashAllSessionAttributes(HttpSession adminSession)
    {
        @SuppressWarnings("unchecked")
        Enumeration<String> attributeNames = (Enumeration<String>)adminSession.getAttributeNames();
        stashSessionAttributes(adminSession, Collections.list(attributeNames));
    }

    // Stash only the session attributes that have been registered
    protected void stashRegisteredSessionAttributes(HttpSession adminSession)
    {
        stashSessionAttributes(adminSession, _sessionAttributesToStash);
    }

    // Stash the requested session attributes (if present) in the factory, which gets put into session. This makes them
    // unavailable while impersonating but allows them to be reinstated after impersonation is over.
    private void stashSessionAttributes(HttpSession adminSession, Collection<String> attributeNames)
    {
        _adminSessionAttributes.clear();

        for (String name : attributeNames)
        {
            Object value = adminSession.getAttribute(name);

            if (null != value)
            {
                _adminSessionAttributes.put(name, adminSession.getAttribute(name));
                adminSession.removeAttribute(name);
            }
        }
    }

    // Reinstate all stashed attributes into the session
    protected void restoreSessionAttributes(HttpSession adminSession)
    {
        for (Map.Entry<String, Object> entry : _adminSessionAttributes.entrySet())
            adminSession.setAttribute(entry.getKey(), entry.getValue());
    }
}
