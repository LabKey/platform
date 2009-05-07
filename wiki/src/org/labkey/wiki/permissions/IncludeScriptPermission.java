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
package org.labkey.wiki.permissions;

import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.permissions.AbstractPermission;
import org.labkey.wiki.WikiModule;

/*
* User: Dave
* Date: May 7, 2009
* Time: 11:36:42 AM
*/
public class IncludeScriptPermission extends AbstractPermission
{
    public IncludeScriptPermission()
    {
        super("Include Script", "Allows the user to include client-side script in wiki pages",
                ModuleLoader.getInstance().getModule(WikiModule.class));
    }
}