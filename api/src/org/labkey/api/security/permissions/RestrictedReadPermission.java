/*
 * Copyright (c) 2009-2014 LabKey Corporation
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
 * By default, this is this permission will NOT grant the ability to read rows. Some modules may implement special rules
 * to allow restricted ability to read to users with this permission.
 */
public class RestrictedReadPermission extends AbstractPermission
{
    public RestrictedReadPermission()
    {
        super("Restricted Read", "User may be able to read some data based on module configured restrictions.");
    }
}