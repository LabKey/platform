package org.labkey.api.security;

import java.util.Comparator;

/**
 * User: adam
 * Date: Feb 3, 2010
 * Time: 2:33:46 PM
 */
public class UserDisplayNameComparator implements Comparator<User>
{
    public int compare(User a, User b)
    {
        String c1 = a.getFriendlyName();
        String c2 = b.getFriendlyName();
        if (c1 == null ? c2 == null : c1.equals(c2))
            return 0;
        return null == c1 ? -1 : c1.compareToIgnoreCase(c2);
    }
}
