/*
 * Copyright (c) 2014-2016 LabKey Corporation
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
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;

/**
 * Captures the different levels of access a user might have to view some, all, or no PHI data.
 *
 * User: adam
 * Date: 1/17/14
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

    private final int _rank;
    private final Class<? extends Permission> _permission;

    PHI(int rank, @Nullable Class<? extends Permission> permission)
    {
        _rank = rank;
        _permission = permission;
    }
    public int getRank() {
        return _rank;
    }
    public boolean isLevelAllowed(PHI level)
    {
        return _rank <= level.getRank();
    }
    public boolean isExportLevelAllowed(PHI level)
    {
        return _rank < level.getRank();
    }

    @Nullable
    public Class<? extends Permission> getRequiredPermission()
    {
        return _permission;
    }

    // Find the maximum PHI permission this user has in this container
    public static PHI determinePhiAllowed(Container c, User user)
    {
        if (c.hasPermission(user, RestrictedPHIPermission.class))
            return Restricted;

        if (c.hasPermission(user, FullPHIPermission.class))
            return PHI;

        if (c.hasPermission(user, LimitedPHIPermission.class))
            return Limited;

        return NotPHI;
    }
}
