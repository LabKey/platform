/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.api.admin.sitevalidation;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.util.Map;

/**
 * User: tgaluhn
 * Date: 4/8/2015
 *
 * Service for registering/running validators for site configuration, schemas, data heuristics, etc.
 *
 * Validators may declare themselves to either apply to site wide scope (e.g., a schema integrity check),
 * or to container level scope (e.g., business rules for the data visible within that container).
 *
 */
public class SiteValidationService
{
    static private Interface instance;

    public static final String MODULE_NAME = "Core";

    @NotNull
    static public Interface get()
    {
        if (instance == null)
            throw new IllegalStateException("Service has not been set.");
        return instance;
    }

    static public void setInstance(Interface impl)
    {
        if (instance != null)
            throw new IllegalStateException("Service has already been set.");
        instance = impl;
    }

    public interface Interface
    {
        void registerProvider(String module, SiteValidationProvider provider);

        // TODO: Allow module specification?

        @NotNull
        Map<String, SiteValidationResultList> runSiteScopeValidators(User u);
        @NotNull
        Map<Container, SiteValidationResultList> runContainerScopeValidators(Container topLevel, User u);

    }


}
