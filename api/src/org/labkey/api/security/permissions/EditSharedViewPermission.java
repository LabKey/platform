/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
package org.labkey.api.security.permissions;

/**
 * Allows a user to make a saved custom view shared with other users, or edit a previously saved shared view.
 * User: dave
 * Date: Jun 22, 2009
 * Time: 3:10:06 PM
 */
public class EditSharedViewPermission extends AbstractPermission
{
    public EditSharedViewPermission()
    {
        super("Edit Shared Query Views", "Allows users to create and edit shared query custom views.");
    }
}
