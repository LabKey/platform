package org.labkey.api.pipeline.trigger;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

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
    default List<String> validateConfiguration(String pipelineId, boolean isEnabled, JSONObject json)
    {
        return Collections.emptyList();
    }
}
