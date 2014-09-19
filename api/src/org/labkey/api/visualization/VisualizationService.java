package org.labkey.api.visualization;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.security.User;

/**
 * Created by matthew on 9/18/14.
 */
public interface VisualizationService
{
    public static class SQLResponse
    {
        public SchemaKey schemaKey;
        public String sql;
    }

    SQLResponse getDataGenerateSQL(Container c, User user, JSONObject json) throws SQLGenerationException;
}
