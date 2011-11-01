package org.labkey.study.samples;

import org.labkey.api.view.JspView;
import org.labkey.api.view.ViewContext;

/**
 * Copyright (c) 2010 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 * <p/>
 * User: brittp
 * Date: Oct 21, 2010 1:39:16 PM
 */
public class SampleSearchWebPart extends JspView<SampleSearchBean>
{
    public SampleSearchWebPart()
    {
        this(true);
    }

    public SampleSearchWebPart(boolean showVials)
    {
        super("/org/labkey/study/view/samples/search.jsp", new SampleSearchBean());
        getModelBean().init(getViewContext(), showVials, true);
        setTitle(showVials ? "Vial Search" : "Vial Group Search");
    }
}
