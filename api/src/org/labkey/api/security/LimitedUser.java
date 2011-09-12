/*
 * Copyright (c) 2011 LabKey Corporation
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

/*
* User: adam
* Date: Sep 10, 2011
* Time: 3:04:41 PM
*/
public class LimitedUser extends User
{
    private int[] _groups;

    public LimitedUser(User user, int[] groups)
    {
        super(user.getEmail(), user.getUserId());
        setFirstName(user.getFirstName());
        setLastName(user.getLastName());
        setActive(user.isActive());
        setDisplayName(user.getFriendlyName());
        setLastLogin(user.getLastLogin());
        setPhone(user.getPhone());
        _groups = groups;
    }

    @Override
    public boolean isAllowedRoles()
    {
        return false;
    }

    @Override
    public int[] getGroups()
    {
        return _groups;
    }
}
