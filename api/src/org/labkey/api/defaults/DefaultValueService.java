/*
 * Copyright (c) 2009-2014 LabKey Corporation
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

import java.util.Map;
import java.util.List;

/*
 * User: brittp
 * Date: Jan 30, 2009
 * Time: 11:08:04 AM
 */

public abstract class DefaultValueService
{
    static private DefaultValueService _instance;

    static public DefaultValueService get()
    {
        return _instance;
    }

    static public void setInstance(DefaultValueService impl)
    {
        _instance = impl;
    }

    public abstract boolean hasDefaultValues(Container container, Domain domain, boolean inherit);

    public abstract boolean hasDefaultValues(Container container, Domain domain, User user, boolean inherit);

    public abstract boolean hasDefaultValues(Container container, Domain domain, User user, @Nullable String scope, boolean inherit);

    public abstract Map<DomainProperty, Object> getDefaultValues(Container container, Domain domain);

    public abstract Map<DomainProperty, Object> getDefaultValues(Container container, Domain domain, User user);

    public abstract Map<DomainProperty, Object> getDefaultValues(Container container, Domain domain, User user, @Nullable String scope);

    public abstract Map<DomainProperty, Object> getMergedValues(Domain domain, Map<DomainProperty, Object> userValues, Map<DomainProperty, Object> globalValues);

    public abstract void setDefaultValues(Container container, Map<DomainProperty, Object> values) throws ExperimentException;

    public abstract void setDefaultValues(Container container, Map<DomainProperty, Object> values, User user) throws ExperimentException;

    public abstract void setDefaultValues(Container container, Map<DomainProperty, Object> values, User user, @Nullable String scope) throws ExperimentException;

    public abstract void clearDefaultValues(Container container, Domain domain);

    public abstract void clearDefaultValues(Container container, Domain domain, User user);

    public abstract void clearDefaultValues(Container container, Domain domain, User user, String scope);

    public abstract List<Container> getDefaultValueOverriders(Container currentContainer, Domain domain);

    public abstract List<Container> getDefaultValueOverridees(Container currentContainer, Domain domain);
}