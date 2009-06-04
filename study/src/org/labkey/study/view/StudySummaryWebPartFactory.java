/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import org.labkey.api.security.ACL;
import org.labkey.api.view.*;
import org.labkey.study.controllers.BaseStudyController;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.springframework.validation.BindException;

import java.lang.reflect.InvocationTargetException;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Aug 9, 2006
 * Time: 4:38:42 PM
 */
public class StudySummaryWebPartFactory extends BaseWebPartFactory
{
    public static String NAME = "Study Overview";

    public StudySummaryWebPartFactory()
    {
        super(NAME);
    }

    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws IllegalAccessException, InvocationTargetException
    {
        if (!portalCtx.hasPermission(ACL.PERM_READ))
            return new HtmlView(NAME, portalCtx.getUser().isGuest() ? "Please log in to see this data" : "You do not have permission to see this data");

        StudyImpl study = StudyManager.getInstance().getStudy(portalCtx.getContainer());

        BindException errors = (BindException) HttpView.currentRequest().getAttribute("errors");
        WebPartView v = new BaseStudyController.StudyJspView<Object>(study, "studySummary.jsp", null, errors);
        v.setTitle(NAME);

        return v;
    }
}
