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
package org.labkey.api.pipeline.trigger;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.util.Pair;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * Pipeline Trigger type (e.g., scheduled, file-watcher, email, etc.)
 */
public interface PipelineTriggerType<C extends PipelineTriggerConfig>
{
    String getName();

    void startAll();

    void stopAll();

    void start(C config);

    void stop(C config);

    /**
     * Create a PipelineTriggerConfig specific to this trigger type based on the ResultSet from a database query.
     * @param rs ResultSet from a database query
     * @return The trigger type specific PipelineTriggerConfig object
     */
    C createConfig(ResultSet rs) throws SQLException;

    /**
     * Allows the trigger type a chance to validate the configuration string on insert/update.
     * @param pipelineId The pipelineId
     * @param isEnabled Whether or not the configuration is set to enabled.
     * @param json The configuration (JSON) object
     * @return Error messages why the configuration is invalid for the given trigger type. Default empty list.
     */
    @NotNull
    default List<Pair<String, String>> validateConfiguration(String pipelineId, boolean isEnabled, JSONObject json)
    {
        return Collections.emptyList();
    }
}
