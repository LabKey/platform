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
package org.labkey.api.settings;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;

import java.util.HashMap;
import java.util.Map;

public interface CustomLabelProvider
{
    /**
     * @param container
     * @return the set of label key/value
     */
    Map<String, String> getCustomLabels(@Nullable Container container);

    void resetLabels(@Nullable Container container, @Nullable User auditUser);

    void saveLabels(HashMap<String, String> updatedLabels, @Nullable Container container, @Nullable User auditUser) throws ValidationException;

    String getName();

    Map<String, Integer> getMetrics();
}
