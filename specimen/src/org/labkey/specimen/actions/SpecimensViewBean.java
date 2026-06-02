/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.specimen.actions;

import org.labkey.api.specimen.Vial;
import org.labkey.specimen.query.SpecimenQueryView;
import org.labkey.api.view.ViewContext;

import java.util.List;

public abstract class SpecimensViewBean
{
    protected SpecimenQueryView _specimenQueryView;
    protected List<Vial> _vials;

    public SpecimensViewBean(ViewContext context, List<Vial> vials, boolean showHistoryLinks,
                             boolean showRecordSelectors, boolean disableLowVialIndicators, boolean restrictRecordSelectors)
    {
        _vials = vials;
        if (vials != null && !vials.isEmpty())
        {
            _specimenQueryView = SpecimenQueryView.createView(context, vials, SpecimenQueryView.ViewType.VIALS);
            _specimenQueryView.setShowHistoryLinks(showHistoryLinks);
            _specimenQueryView.setShowRecordSelectors(showRecordSelectors);
            _specimenQueryView.setDisableLowVialIndicators(disableLowVialIndicators);
            _specimenQueryView.setRestrictRecordSelectors(restrictRecordSelectors);
        }
    }

    public SpecimenQueryView getSpecimenQueryView()
    {
        return _specimenQueryView;
    }

    public List<Vial> getVials()
    {
        return _vials;
    }
}
