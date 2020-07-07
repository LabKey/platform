/*
 * Copyright (c) 2015-2018 LabKey Corporation
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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.module.Module;
import org.labkey.api.query.QueryService;
import org.labkey.api.script.ScriptReference;
import org.labkey.api.script.ScriptService;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.Path;
import org.labkey.api.util.UnexpectedException;

import javax.script.ScriptException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * User: kevink
 * Date: 12/21/15
 *
 * TriggerFactory for server-side JavaScript triggers found in file-based modules.
 */
public class ScriptTriggerFactory implements TriggerFactory
{
    private static final Logger LOG = LogManager.getLogger(ScriptTriggerFactory.class);

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
    protected Collection<Trigger> createTriggerScript(Container c, TableInfo table) throws ScriptException
    {
        ScriptService svc = ScriptService.get();
        assert svc != null;
        if (svc == null)
            return Collections.emptyList();

        return getDefaultTriggers(c, table, svc);
    }

    @NotNull
    private Collection<Trigger> getDefaultTriggers(Container c, TableInfo table, @NotNull ScriptService svc) throws ScriptException
    {

        final String schemaName = table.getPublicSchemaName();
        final String name = table.getName();
        final String title = table.getTitle();

        if (schemaName == null || name == null)
            return Collections.emptyList();

        // Create legal path name
        Path pathNew = QueryService.MODULE_QUERIES_PATH.append(
                FileUtil.makeLegalName(schemaName),
                FileUtil.makeLegalName(name) + ".js");
        Collection<Trigger> scripts = new LinkedHashSet<>(checkPaths(c, table, svc, pathNew));

        // For backwards compat with 10.2
        Path pathOld = QueryService.MODULE_QUERIES_PATH.append(
                schemaName.replaceAll("\\W", "_"),
                name.replaceAll("\\W", "_") + ".js");
        scripts.addAll(checkPaths(c, table, svc, pathOld));

        if (null != title && !name.equals(title))
        {
            Path pathLabel = QueryService.MODULE_QUERIES_PATH.append(
                    FileUtil.makeLegalName(schemaName),
                    FileUtil.makeLegalName(title) + ".js");

            Collection<Trigger> titleTriggers = checkPaths(c, table, svc, pathLabel);
            scripts.addAll(titleTriggers);

            if (!titleTriggers.isEmpty())
                LOG.warn("Rename the file from using title - " + title + ".js to use table name - " + name);

        }

        return scripts;
    }

    @NotNull
    protected Collection<Trigger> checkPaths(Container c, TableInfo table, @NotNull ScriptService svc, Path path) throws ScriptException
    {
        Collection<Trigger> scripts = new LinkedHashSet<>();

        for (Module m : c.getActiveModules())
        {
            ScriptReference script = svc.compile(m, path);
            if (script != null)
                scripts.add(new ScriptTrigger(c, table, script));

        }
        return scripts;
    }

}
