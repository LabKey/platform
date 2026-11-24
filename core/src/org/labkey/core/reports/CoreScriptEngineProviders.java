/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.core.reports;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.docker.DockerService;
import org.labkey.api.premium.PremiumFeatureNotEnabledException;
import org.labkey.api.premium.PremiumService;
import org.labkey.api.reports.ExternalScriptEngineDefinition;
import org.labkey.api.reports.ExternalScriptEngineDefinition.EngineKind;
import org.labkey.api.reports.ExternalScriptEngineDefinition.Type;
import org.labkey.api.reports.ExternalScriptEngineFactory;
import org.labkey.api.reports.ScriptEngineProvider;
import org.labkey.api.reports.ScriptEngineProviderRegistry;
import org.labkey.api.reports.report.r.RScriptEngineFactory;
import org.labkey.api.reports.report.r.RemoteRNotEnabledException;
import org.labkey.api.rstudio.RStudioService;
import org.labkey.api.settings.OptionalFeatureService;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.core.reports.report.python.PythonScriptEngine;
import org.labkey.core.reports.report.r.RDockerScriptEngineFactory;
import org.labkey.core.reports.report.r.RserveScriptEngineFactory;

import javax.script.ScriptEngine;
import java.util.function.Function;

public final class CoreScriptEngineProviders
{
    private static final Logger LOG = LogHelper.getLogger(CoreScriptEngineProviders.class, "Unavailable script engines");

    private CoreScriptEngineProviders()
    {
    }

    public static void register()
    {
        ScriptEngineProviderRegistry registry = ScriptEngineProviderRegistry.get();

        registry.register(Type.R, EngineKind.LOCAL, new LocalProvider(def -> new RScriptEngineFactory(def).getScriptEngine()));
        registry.register(Type.R, EngineKind.RSERVE, new RserveProvider());
        registry.register(Type.R, EngineKind.DOCKER, new RDockerProvider());
        registry.register(Type.Python, EngineKind.LOCAL, new LocalProvider(PythonScriptEngine::new));

        ScriptEngineProvider external = new LocalProvider(def -> new ExternalScriptEngineFactory(def).getScriptEngine());
        registry.register(Type.Perl, EngineKind.LOCAL, external);
        registry.register(Type.External, EngineKind.LOCAL, external);

        registry.register(Type.Jupyter, EngineKind.REMOTE, new JupyterProvider());
    }

    private record LocalProvider(Function<ExternalScriptEngineDefinition, ScriptEngine> factory) implements ScriptEngineProvider
    {
        @Override
        public boolean isAvailable()
        {
            return true;
        }

        @Override
        public void ensureAvailable(@NotNull ExternalScriptEngineDefinition def)
        {
        }

        @Override
        public @Nullable ScriptEngine create(@NotNull ExternalScriptEngineDefinition def)
        {
            return factory.apply(def);
        }
    }

    private static class RserveProvider implements ScriptEngineProvider
    {
        @Override
        public boolean isAvailable()
        {
            return PremiumService.get().isRemoteREnabled();
        }

        @Override
        public void ensureAvailable(@NotNull ExternalScriptEngineDefinition def)
        {
            if (!isAvailable())
            {
                RemoteRNotEnabledException ex = new RemoteRNotEnabledException(def);
                LOG.error(ex.getMessage());
                throw ex;
            }
        }

        @Override
        public @Nullable ScriptEngine create(@NotNull ExternalScriptEngineDefinition def)
        {
            return new RserveScriptEngineFactory(def).getScriptEngine();
        }
    }

    private static class RDockerProvider implements ScriptEngineProvider
    {
        @Override
        public boolean isAvailable()
        {
            DockerService ds = DockerService.get();
            return ds != null && ds.isDockerEnabled() && OptionalFeatureService.get().isFeatureEnabled(RStudioService.R_DOCKER_SANDBOX);
        }

        // Callers read a null engine as "no R engine configured", so unavailable Docker returns null from create() instead
        @Override
        public void ensureAvailable(@NotNull ExternalScriptEngineDefinition def)
        {
        }

        @Override
        public @Nullable ScriptEngine create(@NotNull ExternalScriptEngineDefinition def)
        {
            return new RDockerScriptEngineFactory(def).getScriptEngine();
        }
    }

    private static class JupyterProvider implements ScriptEngineProvider
    {
        @Override
        public boolean isAvailable()
        {
            return PremiumService.get().isEnabled();
        }

        @Override
        public void ensureAvailable(@NotNull ExternalScriptEngineDefinition def)
        {
            if (!isAvailable())
            {
                LOG.error("Jupyter Report engine [{}] requested, but premium module not available/enabled.", def.getName());
                throw new PremiumFeatureNotEnabledException("Jupyter Reports are not available. Please talk to your account representative for additional information.");
            }
        }

        @Override
        public @Nullable ScriptEngine create(@NotNull ExternalScriptEngineDefinition def)
        {
            return new ExternalScriptEngineFactory(def).getScriptEngine();
        }
    }
}
