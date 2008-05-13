/*
 * Copyright (c) 2006-2007 LabKey Corporation
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

package org.labkey.study.view;

import org.labkey.api.view.ViewContext;
import org.labkey.api.view.JspView;

import javax.servlet.ServletException;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Aug 8, 2006
 * Time: 3:54:21 PM
 */
public class DatasetsWebPartView extends JspView<Object>
{
    public DatasetsWebPartView()
    {
        super("/org/labkey/study/view/datasets.jsp");
        setTitle("Study Datasets");
    }

    @Override
    protected void prepareWebPart(Object model) throws ServletException
    {
        super.prepareWebPart(model);
    }
}
