/*
 * Copyright (c) 2017 LabKey Corporation
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
package org.labkey.api.compliance;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.User;

public interface TableRulesProvider
{
    /**
     * Returns the TableRules for this Container and User, if applicable. Otherwise returns null.
     *
     * @param settingsContainer Container to inspect for compliance settings
     * @param user Current user
     * @param permissionsResource the resource (container) in which to evaluate user permissions
     * @return The TableRules to apply here, or null if this provider is not interested
     */
    @Nullable TableRules get(Container settingsContainer, User user, SecurableResource permissionsResource);
}
