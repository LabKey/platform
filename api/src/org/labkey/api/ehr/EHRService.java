/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.api.ehr;

import org.labkey.api.data.Container;
import org.labkey.api.data.TableCustomizer;
import org.labkey.api.module.Module;
import org.labkey.api.resource.Resource;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: bimber
 * Date: 9/14/12
 * Time: 4:44 PM
 */
abstract public class EHRService
{
    static EHRService instance;

    public static EHRService get()
    {
        return instance;
    }

    static public void setInstance(EHRService instance)
    {
        EHRService.instance = instance;
    }

    abstract public void registerModule(Module module);

    abstract public Set<Module> getRegisteredModules();

    abstract public void registerTriggerScript(Module owner, Resource script);

    abstract public List<Resource> getExtraTriggerScripts(Container c);

    abstract public void registerTableCustomizer(TableCustomizer customizer);

    abstract public void registerTableCustomizer(TableCustomizer customizer, String schema);

    abstract public void registerTableCustomizer(TableCustomizer customizer, String schema, String query);

    abstract public List<TableCustomizer> getCustomizers(String schema, String query);
}
