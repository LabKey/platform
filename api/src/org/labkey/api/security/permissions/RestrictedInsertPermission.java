/*
 * Copyright (c) 2009-2016 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;

/**
 * By default this permission will NOT grant the ability to insert rows.  Some modules may implement special rules
 * to allow restricted ability to insert to users with this permission.
 */
public class RestrictedInsertPermission extends AbstractPermission
{
    public RestrictedInsertPermission()
    {
        this("Restricted Insert", "User may be able to insert some data based on module configured restrictions.");
    }

    protected RestrictedInsertPermission(@NotNull String name, @NotNull String description)
    {
        super(name, description);
    }
}