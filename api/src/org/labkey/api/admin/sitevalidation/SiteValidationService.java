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

        /**
         * Returns a map of module name -> result list for all validators registered by that module
         * Will return an empty map if no validators are registered.
         * Module map entry will have empty SiteValidationResultList if no validation errors found by that module
         */
        @NotNull
        Map<String, SiteValidationResultList> runSiteScopeValidators(User u);

        /**
         * Returns a map of projects names -> map of container names within the project -> result list of all validators appropriate for each container
         * If run from the site level, top level map will be all projects.
         * If run from folder level, there will be one entry in the top level map, the project for that folder
         *
         * If no validators are appropriate for any container in a project, that project will not be in the top level map.
         * If no validation errors are found for a container, that container's SiteValidationResultList will be empty.
         *
         * TODO: Not validating root folder, should we? Or would anything for it be covered by site-wide validators?
         *
         */
        @NotNull
        Map<String, Map<String, SiteValidationResultList>> runContainerScopeValidators(Container topLevel, User u);

        boolean hasSiteValidators();
        boolean hasContainerValidators();

    }


}
