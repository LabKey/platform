/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

package org.labkey.study.plate.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.module.Module;
import org.labkey.api.query.*;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.security.User;
import org.labkey.api.view.ViewContext;
import org.labkey.api.study.PlateQueryView;
import org.labkey.api.study.WellGroup;
import org.labkey.api.study.PlateService;
import org.labkey.api.study.PlateTemplate;
import org.labkey.study.StudySchema;

import java.util.Set;
import java.util.HashSet;

/**
 * User: brittp
 * Date: Nov 1, 2006
 * Time: 4:33:11 PM
 */
public class PlateSchema extends UserSchema
{
    public static final String SCHEMA_NAME = "plate";
    public static final String SCHEMA_DESCR = "Contains data about defined plates";

    static public class Provider extends DefaultSchema.SchemaProvider
    {
        public Provider(@Nullable Module module)
        {
            super(module);
        }

        @Override
        public boolean isAvailable(DefaultSchema schema, Module module)
        {
            PlateTemplate[] templates = PlateService.get().getPlateTemplates(schema.getContainer());
            return templates != null && templates.length > 0;
        }

        public QuerySchema createSchema(DefaultSchema schema, Module module)
        {
            return new PlateSchema(schema.getUser(), schema.getContainer());
        }
    }

    public PlateSchema(User user, Container container)
    {
        super(SCHEMA_NAME, null, user, container, StudySchema.getInstance().getSchema());
    }

    public Set<String> getTableNames()
    {
        Set<String> tableSet = new HashSet<>();
        tableSet.add("Plate");
        tableSet.add("WellGroup");
        for (WellGroup.Type type : WellGroup.Type.values())
            tableSet.add("WellGroup_" + type.name());
        return tableSet;
    }   

    public static PlateQueryView createPlateQueryView(ViewContext context, SimpleFilter filter)
    {
        String name = "Plate";
        UserSchema schema = QueryService.get().getUserSchema(context.getUser(), context.getContainer(), SchemaKey.fromParts(SCHEMA_NAME));
        QuerySettings settings = schema.getSettings(context, name, name);
        return new PlateQueryViewImpl(context, settings, filter);
    }

    public static PlateQueryView createWellGroupQueryView(ViewContext context, SimpleFilter filter, WellGroup.Type type)
    {
        String name = "WellGroup";
        if (type != null)
            name += "_" + type.name();

        UserSchema schema = QueryService.get().getUserSchema(context.getUser(), context.getContainer(), SchemaKey.fromParts(SCHEMA_NAME));
        QuerySettings settings = schema.getSettings(context, name, name);
        return new PlateQueryViewImpl(context, settings, filter);
    }

    public static PlateQueryView createWellGroupQueryView(ViewContext context, SimpleFilter filter)
    {
        return createWellGroupQueryView(context, filter, null);
    }

    @Override
    public TableInfo createTable(String name)
    {
        if (name.equals("Plate"))
            return new PlateTable(this);
        else if (name.equals("WellGroup"))
            return new WellGroupTable(this, null);
        else if (name.startsWith("WellGroup_"))
        {
            String typeName = name.substring("WellGroup_".length());
            return new WellGroupTable(this, WellGroup.Type.valueOf(typeName));
        }

        return null;
    }

}
