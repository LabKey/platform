package org.labkey.api.specimen;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.view.FolderTab;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;

import java.util.ArrayList;
import java.util.List;

public class SpecimensPage extends FolderTab.PortalPage
{
    public static final String PAGE_ID = "study.SPECIMENS";
    public static final String SPECIMEN_TOOLS_WEBPART_NAME = "Specimen Tools";  // TODO: Move back to SpecimenToolsWebPartFactory

    public SpecimensPage(String caption)
    {
        super(PAGE_ID, caption);
    }

    @Override
    public boolean isSelectedPage(ViewContext viewContext)
    {
        return super.isSelectedPage(viewContext) ||
                "specimen".equals(viewContext.getActionURL().getController());
    }

    @Override
    public boolean isVisible(Container c, User user)
    {
        Study study = StudyService.get().getStudy(c);
        if (study != null)
        {
            return SpecimenManager.get().isSpecimenModuleActive(c);
        }
        return false;
    }

    @Override
    public List<Portal.WebPart> createWebParts()
    {
        List<Portal.WebPart> parts = new ArrayList<>();
        parts.add(Portal.getPortalPart(StudyService.SPECIMEN_SEARCH_WEBPART).createWebPart());
        parts.add(Portal.getPortalPart(StudyService.SPECIMEN_BROWSE_WEBPART).createWebPart());
        Portal.WebPart toolsWebPart = Portal.getPortalPart(SPECIMEN_TOOLS_WEBPART_NAME).createWebPart();
        toolsWebPart.setLocation(WebPartFactory.LOCATION_RIGHT);
        parts.add(toolsWebPart);
        return parts;
    }
}
