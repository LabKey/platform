/*
 * Copyright (c) 2014 LabKey Corporation
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
package org.labkey.visualization;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.view.ViewContext;
import org.labkey.api.visualization.SQLGenerationException;
import org.labkey.api.visualization.VisualizationService;
import org.labkey.visualization.sql.VisualizationSQLGenerator;

/**
 * Created by matthew on 9/18/14.
 */
public class VisualizationServiceImpl implements VisualizationService
{
    public SQLResponse getDataGenerateSQL(Container c, User user, JSONObject json) throws SQLGenerationException
    {
        ViewContext context = new ViewContext();
        context.setUser(user);
        context.setContainer(c);

        VisualizationSQLGenerator generator = new VisualizationSQLGenerator();
        generator.setViewContext(context);
        generator.bindProperties(json);
        generator.setMetaDataOnly(true);

        SQLResponse ret = new SQLResponse();
        ret.schemaKey = generator.getPrimarySchema().getSchemaPath();
        ret.sql = generator.getSQL();
        return ret;
    }
}
