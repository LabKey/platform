/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

package org.labkey.api.reports.report.view;

import org.labkey.api.data.DataRegion;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.study.StudyService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;

import java.sql.ResultSet;

/**
 * User: Karl Lum
 * Date: Oct 6, 2006
 */
public class ReportQueryView extends QueryView
{
    protected SimpleFilter _filter;

    public ReportQueryView(UserSchema schema, QuerySettings settings)
    {
        super(schema, settings);
        setButtonBarPosition(DataRegion.ButtonBarPosition.NONE);
    }

    public void setFilter(SimpleFilter filter)
    {
        _filter = filter;
    }

    public ResultSet getResultSet(int maxRows) throws Exception
    {
        getSettings().setMaxRows(maxRows);
        DataView view = createDataView();
        DataRegion rgn = view.getDataRegion();
        return rgn.getResultSet(view.getRenderContext());
    }

    public DataView createDataView()
    {
        DataView view = super.createDataView();

        if (_filter != null)
        {
            SimpleFilter filter = (SimpleFilter) view.getRenderContext().getBaseFilter();
            if (filter != null)
                filter.addAllClauses(_filter);
            else
                filter = _filter;
            view.getRenderContext().setBaseFilter(filter);
        }
        StudyService svc = StudyService.get();
        if (svc != null)
            svc.applyDefaultQCStateFilter(view);
        return view;
    }
}
