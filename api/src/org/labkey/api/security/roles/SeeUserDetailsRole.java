/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
package org.labkey.api.security.roles;

import org.labkey.api.security.Group;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.permissions.SeeGroupDetailsPermission;
import org.labkey.api.security.permissions.SeeUserDetailsPermission;

/*
* User: adam
* Date: Jan 22, 2010
* Time: 1:22:04 PM
*/
public class SeeUserDetailsRole extends AbstractRootContainerRole
{
    public static final String NAME = "See User and Group Details";

    public SeeUserDetailsRole()
    {
        super(NAME, "Allows viewing email addresses and contact information of other users as well as information about security groups.",
                SeeUserDetailsPermission.class, SeeGroupDetailsPermission.class);

        addExcludedPrincipal(SecurityManager.getGroup(Group.groupGuests));
    }
}
