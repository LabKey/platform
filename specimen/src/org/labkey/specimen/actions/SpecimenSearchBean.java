/*
 * Copyright (c) 2014-2019 LabKey Corporation
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

import org.labkey.specimen.query.SpecimenQueryView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

/**
 * User: brittp
 * Date: Oct 21, 2010 4:01:33 PM
 */
public class SpecimenSearchBean
{
    private boolean _detailsView;
    private ActionURL _baseViewURL;
    private String _dataRegionName;
    private boolean _inWebPart;
    private int _webPartId = 0;
    private boolean _advancedExpanded;

    public SpecimenSearchBean()
    {
    }

    public SpecimenSearchBean(ViewContext context, boolean detailsView, boolean inWebPart)
    {
        init(context, detailsView, inWebPart);
    }

    public void init(ViewContext context, boolean detailsView, boolean inWebPart)
    {
        _inWebPart = inWebPart;
        _detailsView = detailsView;
        SpecimenQueryView view = SpecimenQueryView.createView(context, detailsView ? SpecimenQueryView.ViewType.VIALS :
                SpecimenQueryView.ViewType.SUMMARY);

        _dataRegionName = view.getDataRegionName();
        _baseViewURL = view.getBaseViewURL();
    }

    public boolean isDetailsView()
    {
        return _detailsView;
    }

    public ActionURL getBaseViewURL()
    {
        return _baseViewURL;
    }

    public String getDataRegionName()
    {
        return _dataRegionName;
    }

    public boolean isInWebPart()
    {
        return _inWebPart;
    }

    public boolean isAdvancedExpanded()
    {
        return _advancedExpanded;
    }

    public void setAdvancedExpanded(boolean advancedExpanded)
    {
        _advancedExpanded = advancedExpanded;
    }

    public int getWebPartId()
    {
        return _webPartId;
    }

    public void setWebPartId(int webPartId)
    {
        _webPartId = webPartId;
    }
}
