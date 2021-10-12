/*
 * Copyright (c) 2017-2018 LabKey Corporation
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
package org.labkey.api.assay.sample;

import org.labkey.api.module.Module;
import org.labkey.api.services.ServiceRegistry;

import java.util.List;

public interface SampleAssayResultsService
{
    static SampleAssayResultsService get()
    {
        return ServiceRegistry.get().getService(SampleAssayResultsService.class);
    }

    static void setInstance(SampleAssayResultsService impl)
    {
        ServiceRegistry.get().registerService(SampleAssayResultsService.class, impl);
    }

    /** @return list of module SampleAssayResultsConfig objects */
    List<SampleAssayResultsConfig> getConfigs();

    /**
     *  Method to register a module's SampleAssayResultsConfig. Call from doStartup() / startupAfterSpringConfiguration()
     *
     * @param module The LK module
     * @param config The SampleAssayResultsConfig object defining the schemaName, queryName, etc. properties to use
     *               for showing the module's assay results in the LKSM app's sample assays tab.
     */
    void registerConfig(Module module, SampleAssayResultsConfig config);
}
