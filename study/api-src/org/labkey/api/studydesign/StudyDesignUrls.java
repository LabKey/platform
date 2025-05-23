package org.labkey.api.studydesign;

import org.labkey.api.action.UrlProvider;
import org.labkey.api.data.Container;
import org.labkey.api.view.ActionURL;

public interface StudyDesignUrls extends UrlProvider
{
    ActionURL getManageAssayScheduleURL(Container container, boolean useAlternateLookupFields);
    ActionURL getManageStudyProductsURL(Container container);
    ActionURL getManageTreatmentsURL(Container container, boolean useSingleTableEditor);
}
