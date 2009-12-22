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
package org.labkey.announcements.model;

import org.labkey.announcements.AnnouncementModule;
import org.labkey.api.security.permissions.AbstractPermission;

/*
* User: adam
* Date: Dec 22, 2009
* Time: 10:33:21 AM
*/
public class SecureMessageBoardReadPermission extends AbstractPermission
{
    public SecureMessageBoardReadPermission()
    {
        super("Secure message board read", "Allows user to read any message in a secure message board", AnnouncementModule.class);
    }
}
