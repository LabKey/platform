package org.labkey.api.pipeline.trigger;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Pipeline Trigger type (e.g., scheduled, file-watcher, email, etc.)
 */
public interface PipelineTriggerType<C extends PipelineTriggerConfig>
{
    String getName();

    C createConfig(ResultSet rs) throws SQLException;

    void startAll();

    void stopAll();

    void start(C config);

    void stop(C config);
}