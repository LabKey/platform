/*
 * Copyright (c) 2014 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.security.permissions.Permission;

/**
* User: adam
* Date: 1/17/14
* Time: 3:20 PM
*/
public enum PHI
{
    NotPHI(0, null),
    Limited(1, LimitedPHIPermission.class),
    PHI(2, FullPHIPermission.class),
    Restricted(3, RestrictedPHIPermission.class);

    public static PHI fromString(@Nullable String value)
    {
        for (PHI phi : values())
            if (phi.name().equals(value))
                return phi;

        return null;
    }

    private final int rank;
    private final Class<? extends Permission> permission;
    private PHI(int rank, @Nullable Class<? extends Permission> permission) {
        this.rank = rank;
        this.permission = permission;
    }
    public int getRank() {
        return rank;
    }
    public boolean isLevelAllowed(PHI level) { return this.rank <= level.getRank(); }

    @Nullable
    public Class<? extends Permission> getRequiredPermission()
    {
        return this.permission;
    }
}
