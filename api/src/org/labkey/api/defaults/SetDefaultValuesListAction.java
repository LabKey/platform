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
package org.labkey.api.defaults;

import org.labkey.api.security.RequiresPermission;
import org.labkey.api.lists.permissions.DesignListPermission;

/*
* User: Dave
* Date: May 20, 2009
* Time: 12:04:03 PM
*/

@RequiresPermission(DesignListPermission.class)
public class SetDefaultValuesListAction extends SetDefaultValuesAction
{
}