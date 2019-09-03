package org.labkey.study.view;

import org.labkey.study.controllers.BaseStudyController;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.StudyImpl;
import org.springframework.validation.BindException;

public class TypeSummaryView extends BaseStudyController.StudyJspView<DatasetDefinition>
{
    public TypeSummaryView(StudyImpl study, DatasetDefinition dataset, BindException errors)
    {
        super(study, "typeSummary.jsp", dataset, errors);
    }
}
