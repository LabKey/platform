/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
package org.labkey.api.gwt.client;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: klum
 * Date: 10/17/12
 */
public enum AuditBehaviorType
{
    NONE("None", 1),
    DETAILED("Detailed", 3),
    SUMMARY("Summary", 2);

    private final String _label;
    private final int _priority;

    AuditBehaviorType(String label, int priority)
    {
        _label = label;
        _priority = priority;
    }

    public String getLabel()
    {
        return _label;
    }

    public int getPriority()
    {
        return _priority;
    }

    public static AuditBehaviorType getEffectiveAuditLevel(@Nullable AuditBehaviorType apiOverride, @NotNull AuditBehaviorType tableAuditBehaviorType)
    {
        if (apiOverride == null || apiOverride._priority < tableAuditBehaviorType._priority)
            return tableAuditBehaviorType;

        return apiOverride;
    }
}
