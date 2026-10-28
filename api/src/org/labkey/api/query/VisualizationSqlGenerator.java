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
package org.labkey.api.query;

import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;

// SQL generation from getData requests, needed by olap member queries and implemented by the visualization service
public interface VisualizationSqlGenerator
{
    static @Nullable VisualizationSqlGenerator get()
    {
        return ServiceRegistry.get().getService(VisualizationSqlGenerator.class);
    }

    String getDataSql(Container c, User user, JSONObject json);

    String getDataCDSSql(Container c, User user, JSONObject json);
}
