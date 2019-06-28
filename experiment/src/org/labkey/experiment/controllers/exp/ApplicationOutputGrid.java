/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.GridView;
import org.springframework.validation.BindException;

import java.util.List;

/**
 * User: jeckels
* Date: Dec 19, 2007
*/
public class ApplicationOutputGrid extends GridView
{
    public ApplicationOutputGrid(Container c, Integer rowIdPA, TableInfo ti)
    {
        super(new DataRegion(), (BindException)null);
        List<ColumnInfo> cols = ti.getColumns("RowId,Name");
        getDataRegion().setColumns(cols);
        getDataRegion().getDisplayColumn(0).setVisible(false);

        ActionURL resolve = new ActionURL(ExperimentController.ResolveLSIDAction.class, c)
                .addParameter("type", ti.getName());
        DetailsURL url = new DetailsURL(resolve, "lsid", FieldKey.fromParts("LSID"));
        getDataRegion().getDisplayColumn(1).setURLExpression(url);

        getDataRegion().setButtonBar(new ButtonBar());
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("SourceApplicationId"), rowIdPA);
        setFilter(filter);
        setTitle("Output " + ti.getName());
    }
}
