/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

package org.labkey.demo.view;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.demo.DemoController;
import org.labkey.demo.model.DemoManager;
import org.labkey.demo.model.Person;

import java.util.Arrays;
import java.util.List;

/**
 * User: brittp
 * Date: Jan 23, 2006
 * Time: 12:59:21 PM
 */
public class DemoWebPart extends JspView<List<Person>>
{
    private static final Logger _log = Logger.getLogger(DemoWebPart.class);

    public DemoWebPart()
    {
        super("/org/labkey/demo/view/demoWebPart.jsp", null);

        Container c = getViewContext().getContainer();
        setModelBean(Arrays.asList(DemoManager.getInstance().getPeople(c)));
        setTitle("Demo Summary");
        setTitleHref(new ActionURL(DemoController.BeginAction.class, c));
    }
}
