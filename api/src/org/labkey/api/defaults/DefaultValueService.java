/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
package org.labkey.api.defaults;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;

import java.util.Map;
import java.util.List;

/**
 * Manages sets of values to be used as defaults when a user comes to a data entry form.
 *
 * Persistence is based on the ontology manager schema, so this requires a Domain as the source of the fields.
 *
 * Multiple scopes are supported, so that different defaults can be stored on a per-user, per-container, or other
 * approaches.
 *
 * User: brittp
 * Date: Jan 30, 2009
 */

public interface DefaultValueService
{
    static DefaultValueService get()
    {
        return ServiceRegistry.get().getService(DefaultValueService.class);
    }

    static void setInstance(DefaultValueService impl)
    {
        ServiceRegistry.get().registerService(DefaultValueService.class, impl);
    }

    boolean hasDefaultValues(Container container, Domain domain, boolean inherit);

    boolean hasDefaultValues(Container container, Domain domain, User user, boolean inherit);

    boolean hasDefaultValues(Container container, Domain domain, User user, @Nullable String scope, boolean inherit);

    Map<DomainProperty, Object> getDefaultValues(Container container, Domain domain);

    Map<DomainProperty, Object> getDefaultValues(Container container, Domain domain, User user);

    Map<DomainProperty, Object> getDefaultValues(Container container, Domain domain, User user, @Nullable String scope);

    Map<DomainProperty, Object> getMergedValues(Domain domain, Map<DomainProperty, Object> userValues, Map<DomainProperty, Object> globalValues);

    void setDefaultValues(Container container, Map<DomainProperty, Object> values) throws ExperimentException;

    void setDefaultValues(Container container, Map<DomainProperty, Object> values, User user) throws ExperimentException;

    void setDefaultValues(Container container, Map<DomainProperty, Object> values, User user, @Nullable String scope) throws ExperimentException;

    void clearDefaultValues(Container container, Domain domain);

    void clearDefaultValues(Container container, Domain domain, User user);

    void clearDefaultValues(Container container, Domain domain, User user, String scope);

    List<Container> getDefaultValueOverriders(Container currentContainer, Domain domain);

    List<Container> getDefaultValueOverridees(Container currentContainer, Domain domain);
}