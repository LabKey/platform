/*
 * Copyright (c) 2012-2013 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;

import java.lang.*;

/**
 * User: adam
 * Date: 11/12/12
 * Time: 8:30 AM
 */

// This is a "generic enum"... it was a true enum (SecurityManager.GroupMemberType), but the interface / implementation approach
// better supports generics and cleans up getGroupMembers(), getAllGroupMembers(), etc. Standard enums don't support generics.

// CONSIDER: Add ACTIVE_USERS or GROUPS_AND_ACTIVE_USERS types?
public interface MemberType<P extends UserPrincipal>
{
    @Nullable P getPrincipal(int id);

    static MemberType<Group> GROUPS = new MemberType<Group>() {
        @Override
        public Group getPrincipal(int id)
        {
            return SecurityManager.getGroup(id);
        }
    };

    static MemberType<User> ACTIVE_AND_INACTIVE_USERS = new MemberType<User>() {
        @Override
        public User getPrincipal(int id)
        {
            return UserManager.getUser(id);
        }
    };

    static MemberType<User> ACTIVE_USERS = new MemberType<User>() {
        @Override
        public User getPrincipal(int id)
        {
            User user = UserManager.getUser(id);

            return (null != user && user.isActive() ? user : null);
        }
    };

    // All groups and all users (including inactive)
    static MemberType<UserPrincipal> ALL_GROUPS_AND_USERS = new MemberType<UserPrincipal>() {
        @Override
        public UserPrincipal getPrincipal(int id)
        {
            User user = ACTIVE_AND_INACTIVE_USERS.getPrincipal(id);

            if (null != user)
                return user;

            return GROUPS.getPrincipal(id);
        }
    };
}
