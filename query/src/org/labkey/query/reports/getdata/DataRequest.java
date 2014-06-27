/*
 * Copyright (c) 2013-2014 LabKey Corporation
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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;

import java.util.Collections;
import java.util.List;

/**
 * JSON deserialization target for the top-level request sent to the GetData API.
 *
 * User: jeckels
 * Date: 5/15/13
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataRequest
{
    private AbstractReportDataSourceBuilder _source;
    private List<AggregateQueryDataTransformerBuilder> _transforms = Collections.emptyList();
    private ReportDataRenderer _renderer = new JSONReportDataRenderer();

    public void setSource(AbstractReportDataSourceBuilder source)
    {
        _source = source;
    }

    public void setTransforms(List<AggregateQueryDataTransformerBuilder> transforms)
    {
        _transforms = transforms;
    }

    public void setRenderer(ReportDataRenderer renderer)
    {
        _renderer = renderer;
    }

    public ApiResponse render(ViewContext context, BindException errors)
    {
        if (_source == null)
        {
            throw new IllegalStateException("No source object was included in the request");
        }
        QueryReportDataSource source = _source.create(context.getUser(), context.getContainer());
        for (AggregateQueryDataTransformerBuilder transform : _transforms)
        {
            source = transform.create(source);
        }
        return _renderer.render(source, context, errors);
    }
}
