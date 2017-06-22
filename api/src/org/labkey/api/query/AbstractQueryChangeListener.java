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
package org.labkey.api.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.security.User;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * User: kevink
 * Date: 4/17/13
 */
public abstract class AbstractQueryChangeListener implements QueryChangeListener
{
    @Override
    public void queryCreated(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull Collection<String> queries)
    {
        for (String query : queries)
            queryCreated(user, container, scope, schema, query);
    }

    protected abstract void queryCreated(User user, Container container, ContainerFilter scope, SchemaKey schema, String query);

    @Override
    public void queryChanged(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull QueryProperty property, @NotNull Collection<QueryPropertyChange> changes)
    {
        for (QueryPropertyChange change : changes)
            queryChanged(user, container, scope, schema, change);
    }

    protected abstract void queryChanged(User user, Container container, ContainerFilter scope, SchemaKey schema, QueryPropertyChange change);

    @Override
    public void queryDeleted(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull Collection<String> queries)
    {
        for (String query : queries)
            queryDeleted(user, container, scope, schema, query);
    }

    protected abstract void queryDeleted(User user, Container container, ContainerFilter scope, SchemaKey schema, String query);

    @Override
    public Collection<String> queryDependents(User user, Container container, ContainerFilter scope, SchemaKey schema, @NotNull Collection<String> queries)
    {
        List<String> ret = new ArrayList<>();
        for (String query : queries)
            ret.addAll(queryDependents(container, scope, schema, query));
        return ret;
    }

    protected abstract Collection<String> queryDependents(Container container, ContainerFilter scope, SchemaKey schema, String query);
}
