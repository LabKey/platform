/*
 * Copyright (c) 2009-2014 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.view.*;

/**
 * User: Mark Igra
 * Date: Nov 17, 2008
 * Time: 9:28:08 PM
 */
public class AssayList2WebPartFactory extends AlwaysAvailableWebPartFactory
{
   public AssayList2WebPartFactory()
   {
       super("AssayList2", false, false, WebPartFactory.LOCATION_MENUBAR);
   }

    public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
    {
        WebPartView view = new JspView(this.getClass(), "assayList2.jsp", null);
        view.setTitle("Assays");
        return view;
    }
}
