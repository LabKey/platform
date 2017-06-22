/*
 * Copyright (c) 2015-2017 LabKey Corporation
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
public interface SiteValidationService
{
    void registerProvider(String module, SiteValidationProvider provider);

    /**
     * Returns a map of module name -> map of ValidatorDescriptor -> result list for all validators registered by that module
     * Will return an empty map if no validators are registered.
     * ValidatorDescriptor map entry will have empty SiteValidationResultList if no validation errors found by that module
     */
    @NotNull
    Map<String, Map<SiteValidatorDescriptor, SiteValidationResultList>> runSiteScopeValidators(User u);

    /**
     * Returns a map of module names -> map ValidatorProviders -> map of projects names ->
     * map of container names within the project -> result list of all validators appropriate for each container
     * If run from the site level, maps of project names will be all projects on the site.
     * If run from folder level, there will be one entry in the project name maps, the project for that folder
     *
     * If no validators are appropriate for any container in a project, that project will not be in the project map.
     * If no validation errors are found for a container, that container's SiteValidationResultList will be empty.
     *
     * TODO: Not validating root folder, should we? Or would anything for it be covered by site-wide validators?
     *
     */
    @NotNull
    Map<String, Map<SiteValidatorDescriptor, Map<String, Map<String, SiteValidationResultList>>>> runContainerScopeValidators(Container topLevel, User u);
}
