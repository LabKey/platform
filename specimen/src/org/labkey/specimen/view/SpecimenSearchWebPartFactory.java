package org.labkey.specimen.view;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.HtmlString;
import org.labkey.api.view.DefaultWebPartFactory;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;

public class SpecimenSearchWebPartFactory extends DefaultWebPartFactory
{
    public SpecimenSearchWebPartFactory(String position)
    {
        super("Specimen Search", SpecimenSearchWebPart.class, position);
        addLegacyNames("Specimen Search (Experimental)");
    }

    @Override
    public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
    {
        if (!portalCtx.hasPermission(ReadPermission.class))
            return new HtmlView("Specimens", HtmlString.of(portalCtx.getUser().isGuest() ? "Please log in to see this data." : "You do not have permission to see this data"));

        if (null == StudyService.get().getStudy(portalCtx.getContainer()))
            return new HtmlView("Specimens", HtmlString.of("This folder does not contain a study."));
        return new SpecimenSearchWebPart(true);
    }
}
