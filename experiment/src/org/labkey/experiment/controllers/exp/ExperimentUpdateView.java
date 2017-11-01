/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.view.UpdateView;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.springframework.validation.BindException;

/**
 * User: jeckels
* Date: Dec 19, 2007
*/
public class ExperimentUpdateView extends UpdateView
{
    public ExperimentUpdateView(DataRegion drg, ExperimentForm form, BindException errors)
    {
        super(drg, form, errors);
        drg.addColumns(ExperimentServiceImpl.get().getTinfoExperiment(), "RowId,Name,LSID,ContactId,ExperimentDescriptionURL,Hypothesis,Comments,Created");

        DisplayColumn col = drg.getDisplayColumn("RowId");
        col.setVisible(false);
        drg.getDisplayColumn("LSID").setVisible(false);
        drg.getDisplayColumn("Created").setVisible(false);

        ButtonBar bb = new ButtonBar();
        bb.setStyle(ButtonBar.Style.separateButtons);
        bb.add(ActionButton.BUTTON_DO_UPDATE);

        drg.setButtonBar(bb);
    }
}
