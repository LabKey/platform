/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.study.writer;

import org.labkey.api.data.Container;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.VirtualFile;
import org.labkey.study.model.Study;

import java.util.List;
import java.util.Set;

/**
 * User: adam
 * Date: May 23, 2009
 * Time: 8:25:19 AM
 */
public class CustomViewWriter implements Writer<Study>
{
    private static final String DEFAULT_DIRECTORY = "views";  // TODO: customViews?

    public String getSelectionText()
    {
        return "Custom Views";
    }

    public void write(Study object, ExportContext ctx, VirtualFile fs) throws Exception
    {
        Container c = ctx.getContainer();
        User user = ctx.getUser();

        // TODO: Export views from external schemas as well?
        DefaultSchema folderSchema = DefaultSchema.get(user, c);

        // TODO: Export views from external schemas as well?  folderSchema.getSchemaNames?
        Set<String> userSchemaNames = folderSchema.getUserSchemaNames();

        for (String schemaName : userSchemaNames)
        {
            UserSchema schema = QueryService.get().getUserSchema(user, c, schemaName);

            List<String> tableAndQueryNames = schema.getTableAndQueryNames(false);

            for (String tableName : tableAndQueryNames)
            {
                List<CustomView> customViews = QueryService.get().getCustomViews(user, c, schemaName, tableName);

                for (CustomView customView : customViews)
                {
                    VirtualFile customViewDir = fs.getDir(DEFAULT_DIRECTORY);
                    customView.serialize(customViewDir);
                }
            }
        }
    }
}
