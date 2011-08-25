/*
 * Copyright (c) 2003-2009 Fred Hutchinson Cancer Research Center
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.api.security;

import java.util.Arrays;


/**
 * User must call isEmpty() to distinguish an ACL that is not specified or blank,
 * versus one that explicitly allows denys all permissions.  In either case
 * hasPermissions() will always return false.
 */

/**
 * @deprecated Use SecurityPolicy
 */
public class ACL implements Cloneable
{
    public static final int PERM_NONE = 0x00000000;

    public static final int PERM_READ = 0x00000001;
    public static final int PERM_INSERT = 0x00000002;
    public static final int PERM_UPDATE = 0x00000004;
    public static final int PERM_DELETE = 0x00000008;
    public static final int PERM_READOWN = 0x00000010;
    // PERM_INSERTOWN = 0x00000020;
    public static final int PERM_UPDATEOWN = 0x00000040;
    public static final int PERM_DELETEOWN = 0x00000080;
    public static final int PERM_ADMIN = 0x00008000;
    public static final int PERM_ALLOWALL = 0x0000ffff;

    private static int[] _emptyArray = new int[0];

    // use two arrays, makes it easy to use binarySearch()
    int[] _groups = _emptyArray;
    int[] _permissions = _emptyArray;
    boolean _isEmpty = true;


    public ACL()
    {
    }


    public boolean hasPermission(User u, int requested)
    {
        int p = getPermissions(u);
        return p == (p | requested);
    }


    public int getPermissions(User u)
    {
        if (u == null)
            return PERM_NONE;

        int perm = getPermissions(u.getGroups());
        return perm;
    }


    /** groups[] must be unique and sorted */
    public int getPermissions(int[] groups)
    {
        int i = 0;
        int j = 0;
        int perm = 0;
        while (i < groups.length && j < _groups.length)
        {
            assert i == 0 || groups[i-1] < groups[i];
            assert j == 0 || _groups[j-1] < _groups[j];

            int x = groups[i];
            int y = _groups[j];
            if (x == y)
            {
                perm |= _permissions[j];
                i++;
                j++;
            }
            else if (x < y)
                i++;
            else
                j++;
        }

        //  enforce that PERM_READ is a strict superset of PERM_READOWN, etc
        //  CONSIDER: do we want to force these semantics on every module?
        assert PERM_READOWN == PERM_READ << 4;
        assert PERM_UPDATEOWN == PERM_UPDATE << 4;
        assert PERM_DELETEOWN == PERM_DELETE << 4;
        perm |= (perm & (PERM_READ|PERM_UPDATE|PERM_DELETE))<<4;

        return perm;
    }


    /**
     * If we ever have groups of groups this should return the calculated
     * permissions like getPermissions(User).  Since we don't have groups
     * of groups, this is sematically equivalent.
     *
     * @param group
     * @return calculated permissions
     */
    public int getPermissions(Group group)
    {
        return getPermissions(group.getUserId());
    }


    /**
     * Explicitly set permissions for this groupid.
     */
    public int getPermissions(int group)
    {
        for (int i = 0; i < _groups.length; i++)
        {
            if (_groups[i] == group)
                return _permissions[i];
        }
        return 0;
    }

    public void setPermission(UserPrincipal p, int permission)
    {
        setPermission(p.getUserId(), permission);
    }

    public void setPermission(int group, int permission)
    {
        assert _groups.length == _permissions.length;

        // once you explicity set a permission, isEmpty is false
        _isEmpty = false;

        if (group == User.guest.getUserId())
            return;
        if (group == Group.groupGuests)
            permission &= ~ACL.PERM_ADMIN;

        int i = Arrays.binarySearch(_groups, group);
        if (i >= 0)
        {
            _permissions[i] = permission;
            return;
        }
        if (permission == 0)
            return;

        i = -(i + 1);
        int[] g = new int[_groups.length + 1];
        int[] p = new int[_groups.length + 1];
        System.arraycopy(_groups, 0, g, 0, i);
        System.arraycopy(_permissions, 0, p, 0, i);
        g[i] = group;
        p[i] = permission;
        System.arraycopy(_groups, i, g, i + 1, _groups.length - i);
        System.arraycopy(_permissions, i, p, i + 1, _groups.length - i);
        _groups = g;
        _permissions = p;
    }
}