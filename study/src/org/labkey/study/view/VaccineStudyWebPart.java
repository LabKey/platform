package org.labkey.study.view;

import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.study.designer.StudyDesignInfo;
import org.labkey.study.designer.StudyDesignManager;

import java.awt.*;
import java.sql.SQLException;

/**
 * Created by IntelliJ IDEA.
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
        if (null == model.getStudyId() || 0 == model.getStudyId())
        {
            try
            {
                //This is just test code. ViscStudies needs to create & manage design properly for study
                StudyDesignInfo[] designInfos = StudyDesignManager.get().getStudyDesigns(HttpView.getContextContainer());
                if (null != designInfos && designInfos.length > 0)
                    model.setStudyId(designInfos[0].getStudyId());
                else
                    model.setEditMode(true);

            }
            catch(SQLException x)
            {
                throw new RuntimeSQLException(x);
            }
        }
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
