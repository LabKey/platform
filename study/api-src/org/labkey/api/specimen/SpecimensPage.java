package org.labkey.api.specimen;

import org.labkey.api.annotations.Migrate;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.User;
import org.labkey.api.specimen.view.SpecimenToolsWebPartFactory;
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

    public SpecimensPage(String caption)
    {
        super(PAGE_ID, caption);
    }

    @Override
    public boolean isSelectedPage(ViewContext viewContext)
    {
        return super.isSelectedPage(viewContext) ||
                "study-samples".equals(viewContext.getActionURL().getController());
    }

    @Override
    @Migrate // Refactor check below after moving this to the specimen module
    public boolean isVisible(Container c, User user)
    {
        Study study = StudyService.get().getStudy(c);
        if (study != null)
        {
            Module specimenModule = ModuleLoader.getInstance().getModule("Specimen");
            return null != specimenModule && c.getActiveModules().contains(specimenModule);
        }
        return false;
    }

    @Override
    public List<Portal.WebPart> createWebParts()
    {
        List<Portal.WebPart> parts = new ArrayList<>();
        parts.add(Portal.getPortalPart(StudyService.SPECIMEN_SEARCH_WEBPART).createWebPart());
        parts.add(Portal.getPortalPart(StudyService.SPECIMEN_BROWSE_WEBPART).createWebPart());
        Portal.WebPart toolsWebPart = Portal.getPortalPart(SpecimenToolsWebPartFactory.SPECIMEN_TOOLS_WEBPART_NAME).createWebPart();
        toolsWebPart.setLocation(WebPartFactory.LOCATION_RIGHT);
        parts.add(toolsWebPart);
        return parts;
    }
}
