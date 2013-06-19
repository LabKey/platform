/*
 * Copyright (c) 2011-2013 LabKey Corporation
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

import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.study.Study;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.study.designer.StudyDesignInfo;
import org.labkey.study.designer.StudyDesignManager;
import org.labkey.study.model.StudyManager;

import java.awt.*;
import java.sql.SQLException;

/**
 * User: markigra
 * Date: 11/17/11
 * Time: 3:43 PM
 */
public class VaccineStudyWebPart extends JspView<VaccineStudyWebPart.Model>
{
    enum Panels {
        VACCINE("Vaccine Design"),
        IMMUNIZATIONS("Immunization Schedule"),
        ASSAYS("Assay Schedule");

        private String _title;
        Panels(String title)
        {
            _title = title;
        }

        String getTitle()
        {
            return _title;
        }

    }

    public VaccineStudyWebPart(Model model)
    {
        super(VaccineStudyWebPart.class, "vaccineStudy.jsp", model);
        assert (null != model.getStudyId() && 0 != model.getStudyId());
        String title = null;
        if (null != model.getPanel())
        {
            Panels p = Panels.valueOf(model.getPanel());
            if (null != p)
                title = p.getTitle();
        }
        if (null == title)
            title = "Vaccine Study Protocol";

        setTitle(title);
    }


    public static class Model
    {
        private String _panel;
        private boolean _editMode;
        private Integer _studyId;
        private String _finishURL;

        public String getPanel()
        {
            return _panel;
        }

        public void setPanel(String panel)
        {
            _panel = panel;
        }

        public boolean isEditMode()
        {
            return _editMode;
        }

        public void setEditMode(boolean editMode)
        {
            _editMode = editMode;
        }

        public Integer getStudyId()
        {
            return _studyId;
        }

        public void setStudyId(Integer studyId)
        {
            _studyId = studyId;
        }

        public String getFinishURL()
        {
            return _finishURL;
        }

        public void setFinishURL(String finishURL)
        {
            _finishURL = finishURL;
        }
    }
}
