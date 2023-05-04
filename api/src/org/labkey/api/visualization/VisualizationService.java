/*
 * Copyright (c) 2014-2018 LabKey Corporation
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
package org.labkey.api.visualization;

import org.json.JSONObject;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.Pair;
import org.labkey.api.visualization.VisualizationProvider.MeasureSetRequest;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public interface VisualizationService
{
    static VisualizationService get()
    {
        return ServiceRegistry.get().getService(VisualizationService.class);
    }

    static void setInstance(VisualizationService impl)
    {
        ServiceRegistry.get().registerService(VisualizationService.class, impl);
    }

    class SQLResponse
    {
        public SchemaKey schemaKey;
        public String sql;
    }

    SQLResponse getDataGenerateSQL(Container c, User user, JSONObject json) throws SQLGenerationException, IOException;

    SQLResponse getDataCDSGenerateSQL(Container c, User user, JSONObject json) throws SQLGenerationException, SQLException, BindException, IOException;

    Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> getDimensions(Container c, User u, MeasureSetRequest measureRequest);

    Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> getMeasures(Container c, User u, MeasureSetRequest measureRequest);

    List<Map<String, Object>> toJSON(Map<Pair<FieldKey, ColumnInfo>, QueryDefinition> dimMeasureCols);
}
