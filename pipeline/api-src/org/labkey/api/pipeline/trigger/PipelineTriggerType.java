package org.labkey.api.pipeline.trigger;

import org.json.JSONObject;

import java.sql.ResultSet;
import java.sql.SQLException;

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
     * @param isEnabled Whether or not the configuration is set to enabled.
     * @param json The configuration (JSON) object
     * @return A message about why the configuration is invalid for the given trigger type. Default null.
     */
    default String validateConfiguration(boolean isEnabled, JSONObject json)
    {
        return null;
    }
}