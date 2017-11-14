/*
 * Copyright (c) 2011-2016 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.StringKeyCache;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SqlSelector;

import java.beans.PropertyChangeEvent;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

/**
 * Caches membership information for groups (their constituent users and child groups).
 * User: adam
 * Date: 10/11/11
 */
public class GroupMembershipCache
{
    private static final String ALL_GROUP_MEMBERSHIPS_PREFIX = "AllMemShip=";
    private static final String IMMEDIATE_GROUP_MEMBERSHIPS_PREFIX = "ImmMemShip=";
    private static final String GROUP_MEMBERS_PREFIX = "Members=";
    private static final CoreSchema CORE = CoreSchema.getInstance();
    private static final StringKeyCache<int[]> CACHE = CacheManager.getStringKeyCache(CacheManager.UNLIMITED, CacheManager.DAY, "Group Memberships");

    static
    {
        UserManager.addUserListener(new GroupMembershipUserListener());
    }


    private final static CacheLoader<String, int[]> ALL_GROUP_MEMBERSHIPS_LOADER = (key, argument) -> {
        UserPrincipal user = (UserPrincipal)argument;
        return computeAllGroups(user);
    };


    private final static CacheLoader<String, int[]> IMMEDIATE_GROUP_MEMBERSHIPS_LOADER = (key, argument) -> {
        int groupId = (Integer)argument;
        SqlSelector selector = new SqlSelector(CORE.getSchema(), new SQLFragment("SELECT GroupId FROM " + CORE.getTableInfoMembers() + " WHERE UserId = ?", groupId));
        Integer[] groupsInt = selector.getArray(Integer.class);
        return _toIntArray(groupsInt);
    };


    private final static CacheLoader<String, int[]> GROUP_MEMBERS_LOADER = (key, argument) -> {
        Group group = (Group)argument;
        Selector selector = new SqlSelector(CORE.getSchema(), new SQLFragment(
            "SELECT Members.UserId FROM " + CORE.getTableInfoMembers() + " Members" +
            " JOIN " + CORE.getTableInfoPrincipals() + " Users ON Members.UserId = Users.UserId\n" +
            " WHERE Members.GroupId = ?" +
            // order: Site groups, project groups, users
            " ORDER BY Users.Type, CASE WHEN ( Users.Container IS NULL ) THEN 1 ELSE 2 END, Users.Name", group.getUserId()));

        return _toIntArray(selector.getArray(Integer.class));
    };


    // Return FLATTENED array of groups to which this principal belongs (recursive)
    static int[] getAllGroupMemberships(@NotNull UserPrincipal user)
    {
        return CACHE.get(ALL_GROUP_MEMBERSHIPS_PREFIX + user.getUserId(), user, ALL_GROUP_MEMBERSHIPS_LOADER);
    }


    // Return array of groups to which this principal directly belongs (non-recursive)
    static int[] getGroupMemberships(int principalId)
    {
        return CACHE.get(IMMEDIATE_GROUP_MEMBERSHIPS_PREFIX + principalId, principalId, IMMEDIATE_GROUP_MEMBERSHIPS_LOADER);
    }


    // Return array of principals that directly belong to this group (non-recursive)
    static int[] getGroupMembers(Group group)
    {
        return CACHE.get(GROUP_MEMBERS_PREFIX + group.getUserId(), group, GROUP_MEMBERS_LOADER);
    }


    static void handleGroupChange(Group group, UserPrincipal principal)
    {
        // very slight overkill
        uncache(group);
        uncache(principal);

        // invalidate all computed group lists (getAllGroups())
        if (principal instanceof Group)
            CACHE.removeUsingPrefix(ALL_GROUP_MEMBERSHIPS_PREFIX);
    }


    private static void uncache(UserPrincipal principal)
    {
        CACHE.remove(ALL_GROUP_MEMBERSHIPS_PREFIX + principal.getUserId());
        CACHE.remove(IMMEDIATE_GROUP_MEMBERSHIPS_PREFIX + principal.getUserId());
        CACHE.remove(GROUP_MEMBERS_PREFIX + principal.getUserId());
    }


    private static int[] _toIntArray(Integer[] groupsInt)
    {
        int[] arr = new int[groupsInt.length];
        for (int i=0 ; i<groupsInt.length ; i++)
            arr[i] = groupsInt[i];
        return arr;
    }


    private static int[] _toIntArray(Set<Integer> groupsInt)
    {
        int[] arr = new int[groupsInt.size()];
        int i = 0;
        for (int group : groupsInt)
            arr[i++] = group;
        Arrays.sort(arr);
        return arr;
    }


    private static int[] computeAllGroups(UserPrincipal principal)
    {
        int userId = principal.getUserId();

        LinkedList<Integer> principals = new LinkedList<>();

        // All principals are themselves
        principals.add(userId);

        // Every user is a member of the guests group; logged in users are members of Site Users.
        if (principal.getPrincipalType() == PrincipalType.USER)
        {
            principals.add(Group.groupGuests);
            if (principal.getUserId() != User.guest.getUserId())
                principals.add(Group.groupUsers);
        }

        return computeAllGroups(principals);
    }


    // Return all the principals plus all the groups they belong to (plus all the groups those groups belong to, etc.)
    public static int[] computeAllGroups(Deque<Integer> principals)
    {
        HashSet<Integer> groupSet = new HashSet<>();

        while (!principals.isEmpty())
        {
            int id = principals.removeFirst();
            groupSet.add(id);
            int[] groups = getGroupMemberships(id);

            for (int g : groups)
                if (!groupSet.contains(g))
                    principals.addLast(g);
        }

        // Site administrators always get developer role as well
        if (groupSet.contains(Group.groupAdministrators))
            groupSet.add(Group.groupDevelopers);

        return _toIntArray(groupSet);
    }


    public static class GroupMembershipUserListener implements UserManager.UserListener
    {
        public void userAddedToSite(User user)
        {
        }

        public void userDeletedFromSite(User user)
        {
            // Blow away groups immediately after user is deleted, otherwise this user's groups, and therefore permissions, will remain active
            // until the user chooses to sign out.
            uncache(user);
        }

        public void userAccountDisabled(User user)
        {
            uncache(user);

        }

        public void userAccountEnabled(User user)
        {
            uncache(user);
        }

        public void propertyChange(PropertyChangeEvent evt)
        {
        }
    }
}
