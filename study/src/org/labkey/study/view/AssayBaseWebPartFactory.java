package org.labkey.study.view;

import org.labkey.api.view.*;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;

public abstract class AssayBaseWebPartFactory extends BaseWebPartFactory
{
    public static final String SHOW_BUTTONS_KEY = "showButtons";
    public static final String PROTOCOL_ID_KEY = "viewProtocolId";
    public static final String BATCH_ID_KEY = "viewBatchesId";
    public static final String RUN_ID_KEY = "viewRunId";

    public AssayBaseWebPartFactory(String name)
    {
        super(name, null, true, true);
    }

    protected static Integer getIntPropertry(Portal.WebPart webPart, String propertyName)
    {
        String value = webPart.getPropertyMap().get(propertyName);
        if (value != null)
        {
            try
            {
                return new Integer(value);
            }
            catch (NumberFormatException e)
            {
            }
        }
        return null;
    }

    public static Integer getProtocolId(Portal.WebPart webPart)
    {
        return getIntPropertry(webPart, PROTOCOL_ID_KEY);
    }

    public static Integer getBatchId(Portal.WebPart webPart)
    {
        return getIntPropertry(webPart, BATCH_ID_KEY);
    }

    public static Integer getRunId(Portal.WebPart webPart)
    {
        return getIntPropertry(webPart, RUN_ID_KEY);
    }

    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
    {
        Integer protocolId = getProtocolId(webPart);
        boolean showButtons = Boolean.parseBoolean(webPart.getPropertyMap().get(SHOW_BUTTONS_KEY));
        ExpProtocol protocol = null;
        if (protocolId != null)
            protocol = ExperimentService.get().getExpProtocol(protocolId.intValue());
        WebPartView view;
        if (protocol == null)
        {
            view = new HtmlView("This webpart does not reference a valid assay.  Please customize the webpart.");
            view.setTitle(getName());
        }
        else
        {
            view = getWebPartView(portalCtx, webPart, protocol, showButtons);
        }
        view.setFrame(WebPartView.FrameType.PORTAL);
        return view;
    }

    public abstract WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart, ExpProtocol protocol, boolean showButtons);

    @Override
    public HttpView getEditView(Portal.WebPart webPart)
    {
        return new JspView<Portal.WebPart>("/org/labkey/study/view/customizeAssayDetailsWebPart.jsp", webPart);
    }
}