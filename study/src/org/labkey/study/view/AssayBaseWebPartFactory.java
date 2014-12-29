/*
 * Copyright (c) 2009-2014 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.view.*;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.study.actions.ProtocolIdForm;

public abstract class AssayBaseWebPartFactory extends BaseWebPartFactory
{
    public static final String SHOW_BUTTONS_KEY = "showButtons";
    public static final String PROTOCOL_ID_KEY = "viewProtocolId";

    public AssayBaseWebPartFactory(String name)
    {
        super(name, true, true, WebPartFactory.LOCATION_BODY);
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

//    public static Integer getBatchId(Portal.WebPart webPart)
//    {
//        return getIntPropertry(webPart, BATCH_ID_KEY);
//    }
//
//    public static Integer getRunId(Portal.WebPart webPart)
//    {
//        return getIntPropertry(webPart, RUN_ID_KEY);
//    }

    public WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
    {
        Integer protocolId = getProtocolId(webPart);
        ProtocolIdForm protocolIdForm = new ProtocolIdForm();
        protocolIdForm.setRowId(protocolId);
        protocolIdForm.setUser(portalCtx.getUser());
        protocolIdForm.setContainer(portalCtx.getContainer());

        boolean showButtons = Boolean.parseBoolean(webPart.getPropertyMap().get(SHOW_BUTTONS_KEY));
        ExpProtocol protocol;
        WebPartView view;
        try
        {
            protocol = protocolIdForm.getProtocol(true);
            view = getWebPartView(portalCtx, webPart, protocol, showButtons);
        }
        catch (NotFoundException e)
        {
            view = new HtmlView("This webpart does not reference a valid assay or provider.  Please customize this webpart.  <span class='labkey-error'>" + e.getMessage() + "</span>");
            view.setTitle(getName());
        }

        view.setFrame(WebPartView.FrameType.PORTAL);
        return view;
    }

    public abstract WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart, ExpProtocol protocol, boolean showButtons);

    public abstract String getDescription();

    public static class EditViewBean
    {
        public String description;
        public Portal.WebPart webPart;
    }

    @Override
    public HttpView getEditView(Portal.WebPart webPart, ViewContext context)
    {
        EditViewBean bean = new EditViewBean();
        bean.description = getDescription();
        bean.webPart = webPart;
        return new JspView<>("/org/labkey/study/view/customizeAssayDetailsWebPart.jsp", bean);
    }
}