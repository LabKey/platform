/*
 * Copyright (c) 2016-2017 LabKey Corporation
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

import org.labkey.api.security.permissions.AbstractPermission;

/**
 * A Permission that allows users to insert, delete, and edit their own messages and respond to other's messages.
 */
public class InsertMessagePermission extends AbstractPermission
{

    public InsertMessagePermission()
    {
        super("Insert Message", "Users may insert, delete, and edit their own messages and respond to other's messages.");
    }
}
