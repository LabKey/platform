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
package org.labkey.api.study.permissions;

import org.labkey.api.security.permissions.AbstractPermission;

/*
* User: Dave
* Date: May 19, 2009
* Time: 4:35:02 PM
*/
public class DesignAssayPermission extends AbstractPermission
{
    public DesignAssayPermission()
    {
        super("Design Assays",
                "May design new assays and change the designs of existing assays.");
    }    
}