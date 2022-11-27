/*
 * Copyright (c) 2009-2018 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.permissions.AssayReadPermission;
import org.labkey.api.security.permissions.DataClassReadPermission;
import org.labkey.api.security.permissions.MediaReadPermission;
import org.labkey.api.security.permissions.NotebookReadPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.ReadSomePermission;

import java.util.Collection;

/*
* User: Dave
* Date: Apr 27, 2009
* Time: 1:22:04 PM
*/
public class ReaderRole extends AbstractRole
{
    public ReaderRole()
    {
        super("Reader", "Readers may read information but may not change anything.",
                ReadPermission.class,
                ReadSomePermission.class,
                AssayReadPermission.class,
                DataClassReadPermission.class,
                NotebookReadPermission.class,
                MediaReadPermission.class
        );
    }

    public ReaderRole(String name, String description, Collection<Class<? extends Permission>>... permCollections)
    {
        super(name, description, permCollections);
    }

    @Override
    public boolean isApplicable(SecurityPolicy policy, SecurableResource resource)
    {
        // reader applies to just about anything other than the root container
        return !(resource instanceof Container) || !((Container)resource).isRoot();
    }
}
