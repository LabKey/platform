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
