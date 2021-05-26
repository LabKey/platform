/*
 * Copyright (c) 2008-2013 LabKey Corporation
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
package org.labkey.api.assay.query;

import org.labkey.api.assay.AssayProtocolSchema;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DetailsColumn;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.springframework.web.servlet.mvc.Controller;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * User: brittp
 * Created: Feb 27, 2008 3:45:13 PM
 */
public class RunListDetailsQueryView extends RunListQueryView
{
    private Class<? extends Controller> _detailsActionClass;
    private String _detailsIdColumn;
    private String _dataIdColumn;
    private Map<String, Object> _extraDetailsUrlParams = new HashMap<>();

    public RunListDetailsQueryView(AssayProtocolSchema schema, QuerySettings settings, Class<? extends Controller> detailsActionClass,
                                   String detailsIdColumn, String dataIdColumn)
    {
        super(schema, settings);
        _detailsActionClass = detailsActionClass;
        _detailsIdColumn = detailsIdColumn;
        _dataIdColumn = dataIdColumn;
    }

    @Override
    public DataView createDataView()
    {
        DataView view = super.createDataView();
        DataRegion rgn = view.getDataRegion();
        ActionURL url = new ActionURL(_detailsActionClass, getContainer());
        if (!_extraDetailsUrlParams.isEmpty())
            url.addParameters(_extraDetailsUrlParams);
        DetailsURL detailsURL = new DetailsURL(url, Collections.singletonMap(_detailsIdColumn, _dataIdColumn));
        rgn.addDisplayColumn(new DetailsColumn(detailsURL, view.getTable()));

        return view;
    }

    public void setExtraDetailsUrlParams(Map<String, Object> extraDetailsUrlParams)
    {
        _extraDetailsUrlParams = extraDetailsUrlParams;
    }
}
