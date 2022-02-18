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

import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.permissions.ReadSomePermission;
import org.labkey.api.study.Study;

/**
 * Used exclusively in dataset security, as a marker in the study policy to indicate a group has per-dataset permissions.
 * As a result, we don't directly surface this role anywhere in the product. See #42682.
 *
 * User: Dave
 * Date: Apr 30, 2009
 */
public class RestrictedReaderRole extends AbstractRole
{
    public RestrictedReaderRole()
    {
        super("Restricted Reader", "Restricted Readers may read some information, but not all.",
                ReadSomePermission.class);
    }

    @Override
    public boolean isApplicable(SecurityPolicy policy, SecurableResource resource)
    {
        return resource instanceof Study;
    }
}
