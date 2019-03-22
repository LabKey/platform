/*
 * Copyright (c) 2009-2015 LabKey Corporation
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
package org.labkey.api.security.roles;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.view.ViewContext;
import org.labkey.api.security.RequiresPermission;

import java.util.Set;

/**
 * User: kevink
 * Date: Jun 1, 2009 11:35:01 AM
 *
 * Get any contextual {@link Role}s for a given {@link ViewContext}.
 * You may annotate an action with {@link RequiresPermissionClass#contextual()}
 * and have contextual roles added during permissions checking.
 *
 * @see RequiresPermissionClass
 */
public interface HasContextualRoles
{
    @Nullable
    public Set<Role> getContextualRoles(ViewContext context);
}
