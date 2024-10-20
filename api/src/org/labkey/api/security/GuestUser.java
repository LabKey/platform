/*
 * Copyright (c) 2011-2018 LabKey Corporation
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

import java.util.List;

/**
 * A special kind of user, representing anonymous access.
 * That is, users who have not authenticated with an account.
 */
class GuestUser extends User
{
    private static final PrincipalArray GUEST_GROUPS = new PrincipalArray(List.of(Group.groupGuests));

    GuestUser(String name, String displayName)
    {
        super(name, 0);
        _groups = GUEST_GROUPS;
        setDisplayName(displayName);
    }

    GuestUser(String name)
    {
        this(name, null);
    }

    // For serialization
    @SuppressWarnings("unused")
    protected GuestUser() { }

    @Override
    public boolean isGuest()
    {
        return true;
    }

    @Override
    public boolean isActive()
    {
        return true;
    }

    @Override
    public void refreshGroups()
    {
        // Don't clear out GuestUser's groups since they're set in its constructor
    }
}
