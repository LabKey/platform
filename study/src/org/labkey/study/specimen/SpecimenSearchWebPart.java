/*
 * Copyright (c) 2010-2014 LabKey Corporation
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
package org.labkey.study.specimen;

import org.labkey.api.view.JspView;

/**
 * User: brittp
 * Date: Oct 21, 2010 1:39:16 PM
 */
public class SpecimenSearchWebPart extends JspView<SpecimenSearchBean>
{
    public SpecimenSearchWebPart()
    {
        this(true);
    }

    public SpecimenSearchWebPart(boolean showVials)
    {
        super("/org/labkey/study/view/specimen/search.jsp", new SpecimenSearchBean());
        getModelBean().init(getViewContext(), showVials, true);
        getModelBean().setWebPartId(getWebPartRowId());
        setTitle(showVials ? "Vial Search" : "Vial Group Search");
    }
}
