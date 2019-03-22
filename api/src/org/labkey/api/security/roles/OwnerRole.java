/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.ReadSomePermission;

/*
* User: Dave
* Date: Apr 27, 2009
* Time: 10:15:44 AM
*/
public class OwnerRole extends AbstractContextualRole
{
    public OwnerRole()
    {
        super("Owner Role", "A role automatically assigned if the current user 'owns' the resource being accessed.",
                ReadPermission.class,
                ReadSomePermission.class,
                UpdatePermission.class,
                DeletePermission.class);
    }
}