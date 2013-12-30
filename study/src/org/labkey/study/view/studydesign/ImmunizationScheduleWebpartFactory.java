package org.labkey.study.view.studydesign;

import org.labkey.api.data.Container;
import org.labkey.api.study.Study;
import org.labkey.api.study.TimepointType;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.StudyManager;
import org.labkey.study.security.permissions.ManageStudyPermission;

/**
 * User: cnathe
 * Date: 12/30/13
 */
public class ImmunizationScheduleWebpartFactory extends BaseWebPartFactory
{
    public static String NAME = "Immunization Schedule";

    public ImmunizationScheduleWebpartFactory()
    {
        super(NAME);
    }

    @Override
    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
    {
        JspView<Portal.WebPart> view = new JspView<>("/org/labkey/study/view/studydesign/immunizationScheduleWebpart.jsp", webPart);
        view.setTitle(NAME);
        view.setFrame(WebPartView.FrameType.PORTAL);

        Container c = portalCtx.getContainer();
        Study study = StudyManager.getInstance().getStudy(c);
        if (c.hasPermission(portalCtx.getUser(), ManageStudyPermission.class))
        {
            String timepointMenuName;
            if (study != null && study.getTimepointType() == TimepointType.DATE)
                timepointMenuName = "Manage Timepoints";
            else
                timepointMenuName = "Manage Visits";

            NavTree menu = new NavTree();
            menu.addChild(timepointMenuName, new ActionURL(StudyController.ManageVisitsAction.class, c));
            view.setNavMenu(menu);
        }

        return view;
    }
}
