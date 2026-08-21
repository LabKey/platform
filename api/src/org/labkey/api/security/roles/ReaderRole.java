/*
 * Copyright (c) 2009-2026 LabKey Corporation
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
import org.labkey.api.security.permissions.AssayReadPermission;
import org.labkey.api.security.permissions.DataClassReadPermission;
import org.labkey.api.security.permissions.MediaReadPermission;
import org.labkey.api.security.permissions.NotebookReadPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;

import java.util.Collection;
import java.util.stream.Stream;

public class ReaderRole extends AbstractRole
{
    static final Collection<Class<? extends Permission>> PERMISSIONS = Stream.concat(
        RestrictedReaderRole.PERMISSIONS.stream(),
        Stream.of(
            AssayReadPermission.class,
            DataClassReadPermission.class,
            MediaReadPermission.class,
            NotebookReadPermission.class,
            ReadPermission.class
        )
    ).toList();

    public ReaderRole()
    {
        super("Reader", "Readers may read information but may not change anything.",
            PERMISSIONS
        );
    }

    @Override
    public boolean isApplicable(SecurableResource resource)
    {
        // reader applies to just about anything other than the root container
        return !(resource instanceof Container) || !((Container)resource).isRoot();
    }
}
