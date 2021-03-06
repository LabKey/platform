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

/**
 * Part of an ExpProtocol design that describes the expected input data.
 * CONSIDER: Add matching based on <code>DataType</code> or file name suffix.
 */
public interface ExpDataProtocolInput extends ExpProtocolInput
{
    /**
     * Returns null if the ExpData matches the requirements of this ExpProtocolInput or a String error message.
     */
    String matches(@NotNull User user, @NotNull Container c, @NotNull ExpData data);

    /**
     * If non-null, the ExpDataClass that the input ExpData must come from.
     */
    @Nullable ExpDataClass getType();
}
