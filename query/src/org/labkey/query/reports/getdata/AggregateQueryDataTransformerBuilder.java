/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.query.reports.getdata;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.labkey.api.data.Aggregate;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.query.FieldKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * JSON deserialization target for transformation steps for GetData API.
 *
 * User: jeckels
 * Date: 5/21/13
 */
@JsonTypeInfo(use=JsonTypeInfo.Id.NAME, include=JsonTypeInfo.As.PROPERTY, property="type")
@JsonSubTypes({
        @JsonSubTypes.Type(value=AggregateQueryDataTransformerBuilder.class)})
@JsonTypeName("aggregate")
public class AggregateQueryDataTransformerBuilder
{
    private List<FieldKey> _groupBy = Collections.emptyList();
    private List<FilterClauseBuilder> _filters = Collections.emptyList();
    private List<Aggregate> _aggregates = Collections.emptyList();
    private PivotBuilder _pivotBuilder = null;

    public void setAggregates(List<AggregateBuilder> aggregates)
    {
        _aggregates = new ArrayList<>(aggregates.size());
        for (AggregateBuilder aggregate : aggregates)
        {
            _aggregates.add(aggregate.create());
        }
    }

    public void setGroupBy(List<List<String>> groupBys)
    {
        _groupBy = new ArrayList<>();
        for (List<String> groupBy : groupBys)
        {
            _groupBy.add(FieldKey.fromParts(groupBy));
        }
    }

    public void setFilters(List<FilterClauseBuilder> filters)
    {
        _filters = filters;
    }

    public void setPivot(PivotBuilder builder)
    {
        _pivotBuilder = builder;
    }

    public AggregateQueryDataTransform create(QueryReportDataSource source)
    {
        SimpleFilter filter = new SimpleFilter();
        for (FilterClauseBuilder builder : _filters)
        {
            builder.append(filter);
        }
        return new AggregateQueryDataTransform(source, filter, _aggregates, _groupBy, _pivotBuilder);
    }
}
