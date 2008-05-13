/*
 * Copyright (c) 2003-2008 Fred Hutchinson Cancer Research Center
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

import org.apache.log4j.Logger;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;


/**
 * User must call isEmpty() to distinguish an ACL that is not specified or blank,
 * versus one that explicitly allows denys all permissions.  In either case
 * hasPermissions() will always return false.
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

    // NYI
    public static final int PERM_DENYALL = 0xffff0000;

    private static int[] _emptyArray = new int[0];

    // use two arrays, makes it easy to use binarySearch()
    int[] _groups = _emptyArray;
    int[] _permissions = _emptyArray;
    boolean _isEmpty = true;


    public static final ACL EMPTY = new ACL(true)
    {
        public void setPermission(int group, int permission)
        {
            throw new IllegalStateException("This ACL is read only");
        }
    };


    public ACL()
    {
        this(true);
    }


    /*
     * see isEmpty()
     */
    public ACL(boolean empty)
    {
        _isEmpty = empty;
    }


    public ACL(byte[] bytes)
    {
        if (null == bytes || bytes.length == 0)
            return;

        _isEmpty = false;

        ByteBuffer buf = ByteBuffer.wrap(bytes);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        int len = bytes.length / 8;
        _groups = new int[len];
        _permissions = new int[len];
        for (int i = 0; i < len; i++)
        {
            _groups[i] = buf.getInt();
            _permissions[i] = buf.getInt();
        }
    }


    /** NOTE NOTE NOTE
     *
     * There is no explicit way to say, "No group has any permission"
     * isEmpty() means 'ignore this ACL' or 'do the default'
     *
     * To explicity deny permissions do something like
     *
     * acl = new ACL()
     * acl.setPermissions(User.GUEST, ACL.PERM_DENYALL)
     */

    public boolean isEmpty()
    {
        assert !_isEmpty || _permissions.length == 0;
        return _isEmpty;
    }


    public boolean hasPermission(User u, int requested)
    {
        int p = getPermissions(u);
        return p == (p | requested);
    }


    public boolean hasPermission(Group g, int requested)
    {
        int p = getPermissions(g);
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


    public byte[] toByteArray()
    {
        assert(_groups.length == _permissions.length);

        ByteBuffer buf = ByteBuffer.allocate(Math.max(1, _groups.length) * 4 * 2);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < _groups.length; i++)
        {
            if (_permissions[i] == 0)
                continue;
            buf.putInt(_groups[i]);
            buf.putInt(_permissions[i]);
        }

        // disambiguate empty v. no permissions if necessary
        if (buf.position() == 0 && !_isEmpty)
        {
            buf.putInt(0);
            buf.putInt(PERM_DENYALL);
        }

        if (buf.position() == buf.capacity())
            return buf.array();
        byte[] bytes = new byte[buf.position()];
        buf.position(0);
        buf.get(bytes);
        return bytes;
    }


    public ACL copy()
    {
        try
        {
            return (ACL)this.clone();
        }
        catch (CloneNotSupportedException x)
        {
            Logger.getLogger(ACL.class).error("unexpected error", x);
            throw new RuntimeException(x);
        }
    }


    /**
     * create copy of this ACL removing all groups not in set
     * will return this if there are no changes
     */
    public ACL scrub(int[] in)
    {
        // probably sorted already, but just to be safe
        Arrays.sort(in);

        // check that all groups with permissions are in the restricted list

        int i = 0;
        for ( ; i<_groups.length ; i++)
        {
            if (Arrays.binarySearch(in,_groups[i]) < 0 && _permissions[i] != 0)
                break;
        }
        if (i == _groups.length)
            return this;

        // otherwise need to create scrubbed ACL

        ACL aclNew = this.copy();
        for ( ; i<_groups.length ; i++)
        {
            if (Arrays.binarySearch(in,aclNew._groups[i]) < 0)
                aclNew._permissions[i] = 0;
        }
        return aclNew;
    }


    public int[] getGroups(int perm, User user)
    {
        int[] groupsWithPerm = new int[_groups.length];
        int len=0;
        for (int i=0 ; i<_groups.length ; i++)
        {
            if ((null == user || user.isInGroup(_groups[i])) && perm == (_permissions[i] & perm))
                groupsWithPerm[len++] = _groups[i];
        }
        int[] ret = new int[len];
        System.arraycopy(groupsWithPerm,0,ret,0,len);
        return ret;
    }
}