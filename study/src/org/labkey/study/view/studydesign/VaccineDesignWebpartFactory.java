package org.labkey.study.view.studydesign;

import org.labkey.api.view.BaseWebPartFactory;
import org.labkey.api.view.JspView;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;

/**
 * User: cnathe
 * Date: 12/27/13
 */
public class VaccineDesignWebpartFactory extends BaseWebPartFactory
{
    public static String NAME = "Vaccine Design";

    public VaccineDesignWebpartFactory()
    {
        super(NAME);
    }

    @Override
    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
    {
        JspView<Portal.WebPart> view = new JspView<>("/org/labkey/study/view/studydesign/vaccineDesignWebpart.jsp", webPart);
        view.setTitle(NAME);
        view.setFrame(WebPartView.FrameType.PORTAL);
        return view;
    }
}
