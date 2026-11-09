/*
 * Copyright (c) 2023-2026 LabKey Corporation
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
package org.labkey.api.data;

import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.User;

import java.util.Collection;

/** A provider for SecurableResource implementations that are scoped to a container */
public interface ContainerSecurableResourceProvider
{
    /** @return all of the resources scoped to the container that are visible by the user */
    Collection<? extends SecurableResource> getSecurableResources(Container c, User user);
}
