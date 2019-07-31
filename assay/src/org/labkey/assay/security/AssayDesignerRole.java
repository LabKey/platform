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
package org.labkey.assay.security;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.assay.security.DesignAssayPermission;
import org.labkey.api.lists.permissions.DesignListPermission;
import org.labkey.api.security.roles.AbstractRole;
import org.labkey.assay.AssayModule;

import java.util.Collection;
import java.util.Collections;

/*
* User: Dave
* Date: May 19, 2009
* Time: 2:23:17 PM
*/
public class AssayDesignerRole extends AbstractRole
{
    public AssayDesignerRole()
    {
        super("Assay Designer",
                "Assay designers may perform several actions related to designing assays.",
                AssayModule.class,
                DesignAssayPermission.class,
                DesignListPermission.class
        );

        excludeGuests();
    }

    @Override
    public @NotNull Collection<String> getSerializationAliases()
    {
        // This class was repackaged when the assay framework was moved out of study into a separate assay module.
        // Add an alias for the previous package to allow import of serialized role assignments in old folder archives
        // (e.g., QC Trend Report Testing.folder.zip).
        return Collections.singleton("org.labkey.study.security.roles.AssayDesignerRole");
    }
}