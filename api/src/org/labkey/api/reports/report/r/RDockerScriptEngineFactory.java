/*
 * Copyright (c) 2017-2019 LabKey Corporation
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
package org.labkey.api.reports.report.r;

import org.labkey.api.docker.DockerService;
import org.labkey.api.reports.ExternalScriptEngineDefinition;
import org.labkey.api.reports.ExternalScriptEngineFactory;
import org.labkey.api.rstudio.RStudioService;
import org.labkey.api.settings.AppProps;

import javax.script.ScriptEngine;

public class RDockerScriptEngineFactory extends ExternalScriptEngineFactory
{
    private final DockerService.DockerImage _dockerImage;
    public RDockerScriptEngineFactory(ExternalScriptEngineDefinition def)
    {
        super(def);
        DockerService ds = DockerService.get();
        DockerService.DockerImage image = null;
        if (ds != null && ds.isDockerEnabled())
            image = ds.getDockerImage(def.getDockerImageRowId());
        this._dockerImage = image;
    }

    @Override
    public synchronized ScriptEngine getScriptEngine()
    {
        DockerService ds = DockerService.get();
        if (null != ds && ds.isDockerEnabled()
                && AppProps.getInstance().isExperimentalFeatureEnabled(RStudioService.R_DOCKER_SANDBOX)
                && this._dockerImage != null)
            return new RDockerScriptEngine(_def, ds, this._dockerImage);
        else return null;
    }
}
