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
package org.labkey.study.view;

import org.labkey.api.view.*;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Nov 17, 2008
 * Time: 9:28:08 PM
 */
public class StudyListWebPartFactory extends AlwaysAvailableWebPartFactory
{
   public StudyListWebPartFactory()
   {
       super("Study List", "menubar", false, false);
   }

    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
    {
        WebPartView view = new JspView(this.getClass(), "studyList.jsp", null);
        view.setTitle("Studies");
        return view;
    }
}
