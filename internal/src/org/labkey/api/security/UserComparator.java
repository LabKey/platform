package org.labkey.api.security;

import java.util.Comparator;

/**
 * User: jeckels
 * Date: Apr 24, 2006
 */
public class UserComparator implements Comparator<User>
{
    public int compare(User a, User b)
    {
        String c1 = a.getEmail();
        String c2 = b.getEmail();
        if (c1 == null ? c2 == null : c1.equals(c2))
            return 0;
        return null == c1 ? -1 : c1.compareTo(c2);
    }
}
