/*
 * Copyright (c) 2015-2016 LabKey Corporation
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
package org.labkey.api.data.triggers;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.module.Module;
import org.labkey.api.query.QueryService;
import org.labkey.api.resource.Resource;
import org.labkey.api.script.ScriptReference;
import org.labkey.api.script.ScriptService;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.UnexpectedException;

import javax.script.ScriptException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * User: kevink
 * Date: 12/21/15
 *
 * TriggerFactory for server-side JavaScript triggers found in file-based modules.
 */
public class ScriptTriggerFactory implements TriggerFactory
{
    @Override
    @NotNull
    public Collection<Trigger> createTrigger(Container c, TableInfo table, Map<String, Object> extraContext)
    {
        try
        {
            return createTriggerScript(c, table);
        }
        catch (ScriptException e)
        {
            throw new UnexpectedException(e);
        }
    }

    @NotNull
    protected Collection<Trigger> createTriggerScript(Container c, TableInfo table)
             throws ScriptException
    {
        ScriptService svc = ServiceRegistry.get().getService(ScriptService.class);
        assert svc != null;
        if (svc == null)
            return Collections.emptyList();

        final String schemaName = table.getPublicSchemaName();
        final String name = table.getName();
        final String title = table.getTitle();

        if (schemaName == null || name == null)
            return Collections.emptyList();

        // Create legal path name
        Path pathNew = new Path(QueryService.MODULE_QUERIES_DIRECTORY,
                FileUtil.makeLegalName(schemaName),
                FileUtil.makeLegalName(name) + ".js");

        // For backwards compat with 10.2
        Path pathOld = new Path(QueryService.MODULE_QUERIES_DIRECTORY,
                schemaName.replaceAll("\\W", "_"),
                name.replaceAll("\\W", "_") + ".js");

        Set<Path> paths = new HashSet<>();
        paths.add(pathNew);
        paths.add(pathOld);

        if (null != title && !name.equals(title))
        {
            Path pathLabel = new Path(QueryService.MODULE_QUERIES_DIRECTORY,
                    FileUtil.makeLegalName(schemaName),
                    FileUtil.makeLegalName(title) + ".js");
            paths.add(pathLabel);
        }

        Collection<Resource> rs = new ArrayList<>(10);
        for (Module m : c.getActiveModules())
        {
            for (Path p : paths)
            {
                Resource r = m.getModuleResource(p);
                if (r != null && r.isFile())
                    rs.add(r);
            }
        }
        if (rs.isEmpty())
            return Collections.emptyList();

        Collection<Trigger> scripts = new ArrayList<>();
        for (Resource r : rs)
        {
            ScriptReference script = svc.compile(r);
            if (script != null)
                scripts.add(new ScriptTrigger(c, table, script));
        }

        return scripts;
    }
}
