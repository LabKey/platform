/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

package org.labkey.study.controllers.reports;

import org.labkey.api.reports.report.view.ManageReportsBean;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.study.Study;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.StudyManager;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Mar 1, 2007
 */
public class StudyManageReportsBean extends ManageReportsBean
{
    private boolean _isAdminView;
    private boolean _isWideView;
    private Study _study;

    public StudyManageReportsBean(ViewContext context, boolean isAdminView, boolean isWide)
    {
        super(context);

        _isAdminView = isAdminView;
        _isWideView = isWide;
        _study = StudyManager.getInstance().getStudy(context.getContainer());
    }

    public boolean getAdminView(){return _isAdminView;}
    public void setAdminView(boolean admin){_isAdminView = admin;}
    public boolean getIsWideView(){return _isWideView;}

    public ActionURL getCustomizeParticipantViewURL()
    {
        ActionURL customizeParticipantURL = new ActionURL(StudyController.CustomizeParticipantViewAction.class, _study.getContainer());
        // add a sample participant to our URL so that users can see the results of their customization.  This needs to be on the URL
        // since the default custom script reads the participant ID parameter from the URL:
        String[] participantIds = StudyManager.getInstance().getParticipantIds(_study, 1);
        if (participantIds != null && participantIds.length > 0)
            customizeParticipantURL.addParameter("participantId", participantIds[0]);
        return customizeParticipantURL;
    }
}
