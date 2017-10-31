/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
package org.labkey.api.di;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.view.NotFoundException;

/**
 * Services provided for running ETLs within the server.
 * User: matthewb
 * Date: 2013-04-03
 */
public interface DataIntegrationService
{
    String MODULE_NAME = "DataIntegration";

    static DataIntegrationService get()
    {
        return ServiceRegistry.get(DataIntegrationService.class);
    }

    static void setInstance(DataIntegrationService impl)
    {
        ServiceRegistry.get().registerService(DataIntegrationService.class, impl);
    }

    void registerStepProviders();
    @Nullable Integer runTransformNow(Container c, User u, String transformId) throws PipelineJobException, NotFoundException;
}
