/*
 * Copyright (c) 2010-2017 LabKey Corporation
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
package org.labkey.api.security;

import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.Permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Provides custom permissions handling for standard admin console actions:
 *
 * - Current container must be the root
 * - Requires {@link org.labkey.api.security.permissions.AdminReadPermission} for GET operations
 * - Requires {@link AdminPermission} (default) for POST operations
 * User: adam
 * Date: Mar 22, 2010
 */

public @Retention(java.lang.annotation.RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
@interface AdminConsoleAction
{
    Class<? extends Permission> value() default AdminPermission.class;
}
