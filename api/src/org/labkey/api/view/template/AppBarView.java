/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.api.view.template;

import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.ViewContext;

import java.util.List;

public class AppBarView extends JspView<AppBar>
{
    public AppBarView(AppBar appBar)
    {
        super(AppBarView.class, "appBar.jsp", appBar);
    }
}
