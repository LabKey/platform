/*
 * Copyright (c) 2007-2013 LabKey Corporation
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
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.query.FieldKey;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.GridView;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.springframework.validation.BindException;

import java.util.List;

/**
 * User: jeckels
* Date: Dec 18, 2007
*/
public class ProtocolListView extends GridView
{
    public ProtocolListView(ExpProtocol protocol, Container c)
    {
        super(new DataRegion(), (BindException)null);
        TableInfo ti = ExperimentServiceImpl.get().getTinfoProtocolActionDetails();
        List<ColumnInfo> cols = ti.getColumns("RowId,Name,ActionSequence,ProtocolDescription");
        getDataRegion().setColumns(cols);
        getDataRegion().getDisplayColumn(0).setVisible(false);
        ActionURL pp = new ActionURL(ExperimentController.ProtocolPredecessorsAction.class, c);
        pp.addParameter("ParentLSID", protocol.getLSID());
        getDataRegion().getDisplayColumn(1).setURL(pp.toString() + "&Sequence=${ActionSequence}");
        getDataRegion().getDisplayColumn(2).setTextAlign("left");

        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(FieldKey.fromParts("ParentProtocolLSID"), protocol.getLSID(), CompareType.EQUAL);
        filter.addCondition(FieldKey.fromParts("ChildProtocolLSID"), protocol.getLSID(), CompareType.NEQ);
        setFilter(filter);

        setSort(new Sort("ActionSequence"));

        getDataRegion().setButtonBar(new ButtonBar());

        setTitle("Protocol Steps");
    }
}
