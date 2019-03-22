/*
 * Copyright (c) 2018 LabKey Corporation
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
package org.labkey.api.exp.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.util.List;

public interface ExpProtocolInputCriteria
{
    interface Factory
    {
        @NotNull
        String getName();

        @NotNull
        ExpProtocolInputCriteria create(@Nullable String config);
    }

    String getTypeName();

    List<? extends ExpRunItem> findMatching(@NotNull ExpProtocolInput protocolInput, @NotNull User user, @NotNull Container c);

    /**
     * Returns null if the item matches the criteria.  If invalid, returns a string error message.
     */
    @Nullable
    String matches(@NotNull ExpProtocolInput protocolInput, @NotNull User user, @NotNull Container c, @NotNull ExpRunItem item);

    String serializeConfig();

    boolean isCompatible(ExpProtocolInputCriteria other);
}
