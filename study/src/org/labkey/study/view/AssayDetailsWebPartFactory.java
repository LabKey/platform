/*
 * Copyright (c) 2007-2008 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.study.view;

import org.labkey.api.view.*;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayRunsView;

/**
 * User: jeckels
 * Date: Jul 19, 2007
 */
public class AssayDetailsWebPartFactory extends BaseWebPartFactory
{
    public static final String PREFERENCE_KEY = "viewProtocolId";
    public static final String SHOW_BUTTONS_KEY = "showButtons";
    public static final String INCLUDE_SUBFOLDERS = "includeSubfolders";

    public AssayDetailsWebPartFactory()
    {
        super("Assay Details", null, true, true);
    }

    public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
    {
        String viewSetting = webPart.getPropertyMap().get(PREFERENCE_KEY);
        boolean showButtons = Boolean.parseBoolean(webPart.getPropertyMap().get(SHOW_BUTTONS_KEY));
        boolean includeSubfolders = Boolean.parseBoolean(webPart.getPropertyMap().get(INCLUDE_SUBFOLDERS));
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
            view = new AssayRunsView(protocol, !showButtons, includeSubfolders);
            view.setTitleHref(AssayService.get().getAssayRunsURL(portalCtx.getContainer(), protocol).getLocalURIString());
            view.setTitle(protocol.getName() + " Runs");
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
