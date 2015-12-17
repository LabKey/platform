/*
 * Copyright (c) 2004-2015 Fred Hutchinson Cancer Research Center
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
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.springframework.web.servlet.ModelAndView;


public class BootstrapTemplate extends HomeTemplate
{
    public BootstrapTemplate(ViewContext context, Container c, ModelAndView body, PageConfig page)
    {
        super(context, c, new VBox(new HtmlView("<h1>BOOTSTRAP ME</h1>"), body), page);
    }
}
