/*
 * Copyright (c) 2017-2018 LabKey Corporation
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

import org.labkey.api.security.permissions.AssayReadPermission;
import org.labkey.api.security.permissions.DataClassReadPermission;
import org.labkey.api.security.permissions.EditSharedViewPermission;
import org.labkey.api.security.permissions.MediaReadPermission;
import org.labkey.api.security.permissions.ReadPermission;

/**
 * Created by Josh on 3/1/2017.
 */
public class SharedViewEditorRole extends AbstractRole
{
    protected SharedViewEditorRole()
    {
        super("Shared View Editor", "Shared view editors may create and update shared custom grid views",
                ReadPermission.class,
                AssayReadPermission.class,
                DataClassReadPermission.class,
                EditSharedViewPermission.class,
                MediaReadPermission.class
        );
        excludeGuests();
    }
}
