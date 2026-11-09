/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.api.module;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Designates actions that should not enforce forbidden project checking, which is used to support impersonation
 * container restrictions and project locking. Use with caution. Allows project admins to perform actions outside
 * the project they administer and non-admins the ability to invoke actions in locked projects.
 *
 * @see org.labkey.api.action.PermissionCheckableAction#checkPermissions()
 * @see org.labkey.api.data.Container#isForbiddenProject(org.labkey.api.security.User)
 */
@Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface IgnoresForbiddenProjectCheck
{
}
