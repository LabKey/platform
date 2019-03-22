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
package org.labkey.api.exp.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveTreeMap;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.security.User;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

/**
 * User: kevink
 * Date: 9/24/15
 */
public class DataClassUserSchema extends AbstractExpSchema
{
    public static final String NAME = ExpSchema.NestedSchemas.data.name();
    private static final String DESCR = "Contains data about the registered datas";

    private Map<String, ExpDataClass> _map;

    static private Map<String, ExpDataClass> getDataClassMap(Container container, User user)
    {
        Map<String, ExpDataClass> map = new CaseInsensitiveTreeMap<>();
        // User can be null if we're running in a background thread, such as doing a study export.
        for (ExpDataClass dataClass : ExperimentService.get().getDataClasses(container, user, true))
        {
            map.put(dataClass.getName(), dataClass);
        }
        return map;
    }

    public DataClassUserSchema(Container container, User user)
    {
        this(container, user, null);
    }

    private DataClassUserSchema(Container container, User user, Map<String, ExpDataClass> map)
    {
        super(SchemaKey.fromParts(ExpSchema.SCHEMA_NAME, NAME), DESCR, user, container, ExperimentService.get().getSchema());
        _map = map;
    }

    private Map<String, ExpDataClass> getDataClasses()
    {
        if (_map == null)
            _map = getDataClassMap(getContainer(), getUser());
        return _map;
    }

    @Override
    public Set<String> getTableNames()
    {
        return getDataClasses().keySet();
    }

    @Nullable
    @Override
    public TableInfo createTable(String name)
    {
        ExpDataClass dataClass = getDataClasses().get(name);
        if (dataClass == null)
            return null;

        return createTable(dataClass);
    }

    public ExpDataClassDataTable createTable(@NotNull ExpDataClass dataClass)
    {
        ExpDataClassDataTable ret = ExperimentService.get().createDataClassDataTable(dataClass.getName(), this, dataClass);
        if (_containerFilter != null)
            ret.setContainerFilter(_containerFilter);
        ret.populate();
        ret.overlayMetadata(ret.getPublicName(), DataClassUserSchema.this, new ArrayList<>());
        return ret;
    }

    @Override
    public QueryView createView(ViewContext context, @NotNull QuerySettings settings, BindException errors)
    {
        if (settings.getQueryName() != null && getTableNames().contains(settings.getQueryName()))
        {
            return new QueryView(this, settings, errors) {
                @Override
                public @Nullable ActionButton createDeleteButton()
                {
                    // Use default delete button, but without showing the confirmation text
                    ActionButton button = super.createDeleteButton();
                    if (button != null)
                    {
                        button.setRequiresSelection(true);
                    }

                    return button;
                }
            };
        }

        return super.createView(context, settings, errors);
    }

    @Override
    public String getDomainURI(String queryName)
    {
        Container container = getContainer();
        ExpDataClass mts = getDataClasses().get(queryName);
        if (mts == null)
            throw new NotFoundException("DataClass '" + queryName + "' not found in this container '" + container.getPath() + "'.");

        return mts.getDomain().getTypeURI();
    }

}
