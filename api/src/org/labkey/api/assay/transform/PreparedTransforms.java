/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.api.assay.transform;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * The {@link PreparedTransform}s for the runs of one save request, keyed by each run's JSON object.
 * Close it only after the transaction that recorded them has committed or rolled back.
 */
public class PreparedTransforms implements AutoCloseable
{
    private final Map<JSONObject, PreparedTransform> _byRunJson = new IdentityHashMap<>();

    public void put(@NotNull JSONObject runJson, @NotNull PreparedTransform prepared)
    {
        _byRunJson.put(runJson, prepared);
    }

    public @Nullable PreparedTransform get(@NotNull JSONObject runJson)
    {
        return _byRunJson.get(runJson);
    }

    @Override
    public void close()
    {
        _byRunJson.values().forEach(PreparedTransform::close);
    }
}
