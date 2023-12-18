package org.labkey.specimen.view;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.HtmlString;
import org.labkey.api.view.DefaultWebPartFactory;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.view.WebPartView;

public class SpecimenWebPartFactory extends DefaultWebPartFactory
{
    public SpecimenWebPartFactory()
    {
        super("Specimens", SpecimenWebPart.class, WebPartFactory.LOCATION_BODY, WebPartFactory.LOCATION_RIGHT);
        addLegacyNames("Specimen Browse (Experimental)");
    }

    @Override
    public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
    {
        if (!portalCtx.hasPermission(ReadPermission.class))
            return new HtmlView("Specimens", HtmlString.of(portalCtx.getUser().isGuest() ? "Please log in to see this data." : "You do not have permission to see this data"));

        Study study = StudyService.get().getStudy(portalCtx.getContainer());
        if (null == study)
            return new HtmlView("Specimens", HtmlString.of("This folder does not contain a study."));
        return new SpecimenWebPart(webPart.getLocation().equals(HttpView.BODY), study);
    }
}
