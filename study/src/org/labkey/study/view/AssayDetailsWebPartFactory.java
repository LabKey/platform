package org.labkey.study.view;

import org.labkey.api.view.*;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayRunsView;
import org.labkey.api.util.PageFlowUtil;

/**
 * User: jeckels
 * Date: Jul 19, 2007
 */
public class AssayDetailsWebPartFactory extends WebPartFactory
{
    public static final String PREFERENCE_KEY = "viewProtocolId";
    public static final String SHOW_BUTTONS_KEY = "showButtons";

    public AssayDetailsWebPartFactory()
    {
        super("Assay Details", null, true, true);
    }

    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
    {
        String viewSetting = webPart.getPropertyMap().get(PREFERENCE_KEY);
        boolean showButtons = Boolean.parseBoolean(webPart.getPropertyMap().get(SHOW_BUTTONS_KEY));
        ExpProtocol protocol = null;
        if (viewSetting != null)
        {
            try
            {
                int protocolId = Integer.parseInt(viewSetting);
                protocol = ExperimentService.get().getExpProtocol(protocolId);
            }
            catch (NumberFormatException e)
            {
                // fall through
            }
        }
        WebPartView view;
        if (protocol == null)
        {
            view = new HtmlView("This webpart does not reference a valid assay.  Please customize the webpart.");
            view.setTitle("Assay Details");
        }
        else
        {
            view = new AssayRunsView(protocol, !showButtons);
            view.setTitleHref(AssayService.get().getAssayRunsURL(portalCtx.getContainer(), protocol).getLocalURIString());
            view.setTitle(PageFlowUtil.filter(protocol.getName()) + " Runs");
        }
        view.setFrame(WebPartView.FrameType.PORTAL);
        return view;
    }

    @Override
    public HttpView getEditView(Portal.WebPart webPart)
    {
        return new JspView<Portal.WebPart>("/org/labkey/study/view/customizeAssayDetailsWebPart.jsp", webPart);
    }
}
