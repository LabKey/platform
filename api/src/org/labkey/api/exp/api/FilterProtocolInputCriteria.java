/*
 * Copyright (c) 2018 LabKey Corporation
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
package org.labkey.api.exp.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.FilterInfo;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.query.CustomViewInfo;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.UnexpectedException;

import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;

public class FilterProtocolInputCriteria extends AbstractProtocolInputCriteria
{
    public static final String FACTORY_NAME = "filter";

    public static class Factory implements ExpProtocolInputCriteria.Factory
    {
        @Override
        public @NotNull String getName()
        {
            return FACTORY_NAME;
        }

        @Override
        public @NotNull ExpProtocolInputCriteria create(@Nullable String config)
        {
            return new FilterProtocolInputCriteria(config);
        }
    }

    private final SimpleFilter _filter;

    public FilterProtocolInputCriteria(@Nullable String config)
    {
        super(config);
        try
        {
            CustomViewInfo.FilterAndSort filterAndSort = CustomViewInfo.FilterAndSort.fromString(config);
            List<FilterInfo> filter = filterAndSort.getFilter();
            _filter = new SimpleFilter();
            for (FilterInfo f : filter)
                _filter.addCondition(f.getField(), f.getValue(), f.getOp());
        }
        catch (URISyntaxException e)
        {
            throw new UnexpectedException(e);
        }
    }

    @Override
    public String getTypeName()
    {
        return FACTORY_NAME;
    }

    @Override
    public List<? extends ExpRunItem> findMatching(@NotNull ExpProtocolInput protocolInput, @NotNull User user, @NotNull Container c)
    {
        TableInfo table = null;
        if (protocolInput instanceof ExpDataProtocolInput)
        {
            ExpDataProtocolInput dpi = (ExpDataProtocolInput)protocolInput;
            ExpDataClass dc = dpi.getType();
            if (dc != null)
            {
                UserSchema schema = QueryService.get().getUserSchema(user, c, SchemaKey.fromParts(ExpSchema.SCHEMA_NAME, ExpSchema.NestedSchemas.data.toString()));
                table = schema.getTable(dc.getName());
            }
            else
            {
                UserSchema schema = QueryService.get().getUserSchema(user, c, SchemaKey.fromParts(ExpSchema.SCHEMA_NAME));
                table = schema.getTable(ExpSchema.TableType.Data.toString());
            }
        }
        else if (protocolInput instanceof ExpMaterialProtocolInput)
        {
            ExpMaterialProtocolInput mpi = (ExpMaterialProtocolInput)protocolInput;
            ExpSampleType ss = mpi.getType();
            if (ss != null)
            {
                UserSchema schema = QueryService.get().getUserSchema(user, c, SchemaKey.fromParts(SamplesSchema.SCHEMA_NAME));
                table = schema.getTable(ss.getName());
            }
            else
            {
                UserSchema schema = QueryService.get().getUserSchema(user, c, SchemaKey.fromParts(ExpSchema.SCHEMA_NAME));
                table = schema.getTable(ExpSchema.TableType.Materials.toString());
            }
        }

        if (table == null)
            return Collections.emptyList();

        // First select the RowId from the query table and then use ExperimentService to get the ExpData/ExpMaterial objects
        TableSelector ts = new TableSelector(table, table.getColumns("RowId"), _filter, null);
        if (protocolInput instanceof ExpDataProtocolInput)
            return ExperimentService.get().getExpDatas(ts.getArrayList(Integer.class));
        else if (protocolInput instanceof ExpMaterialProtocolInput)
            return ExperimentService.get().getExpMaterials(ts.getArrayList(Integer.class));
        else
            throw new IllegalStateException();
    }

    @Override
    public String matches(@NotNull ExpProtocolInput protocolInput, @NotNull User user, @NotNull Container c, @NotNull ExpRunItem item)
    {
        // TODO: validate the input matches the query criteria
        // valid
        return null;
    }

    @Override
    public boolean isCompatible(ExpProtocolInputCriteria other)
    {
        if (other == null || other.getClass() != getClass())
            return false;

        FilterProtocolInputCriteria otherFilterCriteria = (FilterProtocolInputCriteria)other;
        if (!_filter.equals(otherFilterCriteria._filter))
            return false;

        return true;
    }
}
