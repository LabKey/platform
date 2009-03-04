/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

import org.labkey.api.view.GridView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.data.*;

import java.util.List;

/**
 * User: jeckels
* Date: Dec 19, 2007
*/
public class ApplicationOutputGrid extends GridView
{
    public ApplicationOutputGrid(Container c, Integer rowIdPA, TableInfo ti)
    {
        super(new DataRegion());
        List<ColumnInfo> cols = ti.getColumns("RowId,Name");
        getDataRegion().setColumns(cols);
        getDataRegion().getDisplayColumn(0).setVisible(false);
        getDataRegion().getDisplayColumn(1).setURL(ActionURL.toPathString("Experiment", "resolveLSID", c) + "?lsid=${LSID}");
        getDataRegion().setButtonBar(ButtonBar.BUTTON_BAR_EMPTY);
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition("SourceApplicationId", rowIdPA);
        setFilter(filter);
        setTitle("Output " + ti.getName());
    }
}
