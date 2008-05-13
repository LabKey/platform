/*
 * Copyright (c) 2004-2007 Fred Hutchinson Cancer Research Center
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

import org.labkey.api.data.Container;
import org.labkey.api.view.*;

import java.util.ArrayList;


public class FastTemplate extends HomeTemplate
{
    static ArrayList mainMenu = null;
    static NavTree adminMenu = null;

    public FastTemplate(ViewContext context, HttpView body)
    {
        this(context, context.getContainer(), body, null);
    }

    public FastTemplate(ViewContext context, HttpView body, NavTrailConfig trailConfig)
    {
        this(context, context.getContainer(), body, trailConfig);
    }

    public FastTemplate(ViewContext context, Container c, HttpView body)
    {
        this(context, c, body, null);
    }

    public FastTemplate(ViewContext context, Container c, HttpView body, NavTrailConfig trailConfig)
    {
        super("/org/labkey/api/view/template/FastTemplate.jsp", context, c, body, trailConfig);
    }
}
