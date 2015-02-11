/*
 * Copyright (c) 2008-2015 LabKey Corporation
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

package org.labkey.experiment.controllers.exp;

import org.labkey.api.data.*;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.GridView;
import org.labkey.api.view.NotFoundException;
import org.labkey.experiment.DotGraph;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.springframework.validation.BindException;

import java.util.List;


/**
 * User: jeckels
* Date: Jan 24, 2008
*/
public class GraphMoreGrid extends GridView
{
    public GraphMoreGrid(Container c, BindException errors, ActionURL url)
    {
        super(new DataRegion(), errors);

        String objectType = DotGraph.TYPECODE_PROT_APP;
        String title = "Selected ";
        TableInfo ti;
        String runIdParam;

        if (null != url.getParameter("objtype"))
            objectType = url.getParameter("objtype");

        if (objectType.equals(DotGraph.TYPECODE_MATERIAL))
        {
            ti = ExperimentServiceImpl.get().getTinfoMaterial();
            title += "Materials";
        }
        else if (objectType.equals(DotGraph.TYPECODE_DATA))
        {
            ti = ExperimentServiceImpl.get().getTinfoData();
            title += "Data Objects";
        }
        else
        {
            ti = ExperimentServiceImpl.get().getTinfoProtocolApplication();
            title += "Protocol Applications";
        }

        // for starting inputs, the runId is passed, not looked up
        if (null != url.getParameter("runId"))
            runIdParam = url.getParameter("runId");
        else
            runIdParam = "${RunId}";

        List<ColumnInfo> cols = ti.getColumns("RowId,Name,LSID,RunId");
        getDataRegion().setColumns(cols);
        getDataRegion().getDisplayColumn(0).setVisible(false);

        ActionURL resolve = new ActionURL(ExperimentController.ResolveLSIDAction.class, c);
        getDataRegion().getDisplayColumn(1).setURL(resolve.addParameter("lsid", "${LSID}").toString());
        getDataRegion().getDisplayColumn(2).setVisible(false);
        getDataRegion().getDisplayColumn(3).setVisible(false);

        getDataRegion().addDisplayColumn(new SimpleDisplayColumn("[Lineage Graph]"));
        getDataRegion().getDisplayColumn(4).setWidth("200px");

        ActionURL graphDetail = new ActionURL(ExperimentController.ShowRunGraphDetailAction.class, c);
        graphDetail.addParameter("rowId", runIdParam);
        graphDetail.addParameter("detail", true);
        graphDetail.addParameter("focusType", objectType);
        graphDetail.addParameter("focus", "${rowId}");
        getDataRegion().getDisplayColumn(4).setURL(graphDetail.toString());

        getDataRegion().setButtonBar(ButtonBar.BUTTON_BAR_EMPTY);
        String param = url.getParameter("rowId~in");
        if (param == null)
        {
            throw new NotFoundException();
        }
        String[] inValues = param.split(",");
        String separator = "";
        StringBuilder inClause = new StringBuilder(" RowId IN (");
        for (String inValue : inValues)
        {
            inClause.append(separator);
            separator = ", ";
            try
            {
                inClause.append(Integer.toString(Integer.parseInt(inValue)));
            }
            catch (NumberFormatException e)
            {
                throw new NotFoundException("Invalid RowId requested: " + inValue);
            }
        }
        inClause.append(") ");
        SimpleFilter filter = new SimpleFilter();
        filter.addWhereClause(inClause.toString(), new Object[]{});
        setFilter(filter);
        setTitle(title);
    }
}
