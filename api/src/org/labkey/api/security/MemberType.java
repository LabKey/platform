/*
 * Copyright (c) 2012 LabKey Corporation
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

// This is a "generic enum"... it was a true enum (SecurityManager.GroupMemberType), but I migrated it to this
// interface / implementation approach because using generics cleaned up getGroupMembers(), getAllGroupMembers(), etc.
// Standard enums don't support generics.
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

    static MemberType<User> USERS = new MemberType<User>() {
        @Override
        public User getPrincipal(int id)
        {
            return UserManager.getUser(id);
        }
    };

    static MemberType<UserPrincipal> BOTH = new MemberType<UserPrincipal>() {
        @Override
        public UserPrincipal getPrincipal(int id)
        {
            User user = USERS.getPrincipal(id);

            if (null != user)
                return user;

            return GROUPS.getPrincipal(id);
        }
    };
}
