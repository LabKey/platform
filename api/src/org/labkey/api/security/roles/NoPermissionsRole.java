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
import org.labkey.api.study.Dataset;

import java.util.Collections;

/*
* User: Dave
* Date: Apr 27, 2009
* Time: 1:32:29 PM
*/
public class NoPermissionsRole extends AbstractContextualRole
{
    public NoPermissionsRole()
    {
        super("No Permissions", "Granted no access whatsoever.", Collections.emptyList());
    }

    @Override
    public boolean isApplicable(SecurityPolicy policy, SecurableResource resource)
    {
        // Don't show on dataset security page
        return !(resource instanceof Dataset);
    }
}