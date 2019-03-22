/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
package org.labkey.api.module;

import org.apache.log4j.Logger;

import java.util.List;
import java.util.ListIterator;

/**
 * Handles running the upgrade scripts for modules, both those that are performed synchronously before
 * the server is considered started, and the async updates that fire after the normal startup is complete.
 * User: adam
 * Date: Nov 5, 2008
 */
public class ModuleUpgrader
{
    private static final Logger _log = Logger.getLogger(ModuleUpgrader.class);

    private final List<Module> _modules;

    public enum Execution
    {
        Synchronous
        {
            @Override
            void run(Runnable runnable)
            {
                runnable.run();
            }
        },
        Asynchronous
        {
            @Override
            void run(Runnable runnable)
            {
                Thread thread = new Thread(runnable, "Module Upgrade");
                thread.start();
            }
        };

        abstract void run(Runnable runnable);}

    ModuleUpgrader(List<Module> modules)
    {
        _modules = modules;
    }


    public static Logger getLogger()
    {
        return _log;
    }


    void upgrade() throws Exception
    {
        ListIterator<Module> iter = _modules.listIterator(_modules.size());

        while (iter.hasPrevious())
        {
            Module module = iter.previous();
            ModuleContext ctx = ModuleLoader.getInstance().getModuleContext(module);
            module.beforeUpdate(ctx);
        }

        for (Module module : _modules)
        {
            ModuleContext ctx = ModuleLoader.getInstance().getModuleContext(module);
            module.versionUpdate(ctx);
            module.afterUpdate(ctx);
            ctx.upgradeComplete(module);
        }
    }


    void upgrade(Runnable afterUpgrade, Execution execution)
    {
        execution.run(() -> {
            try
            {
                upgrade();
                afterUpgrade.run();
            }
            catch (Throwable t)
            {
                ModuleLoader.getInstance().setStartupFailure(t);
                _log.error("Failure during module upgrade", t);
            }
        });
    }
}
