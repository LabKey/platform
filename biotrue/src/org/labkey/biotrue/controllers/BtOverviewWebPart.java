/*
 * Copyright (c) 2007 LabKey Corporation
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

package org.labkey.biotrue.controllers;

import org.labkey.api.view.*;

public class BtOverviewWebPart extends HtmlView
{
    static public final WebPartFactory FACTORY = new WebPartFactory("BioTrue Connector Overview")
    {
        public WebPartView getWebPartView(ViewContext portalCtx, Portal.WebPart webPart) throws Exception
        {
            return new BtOverviewWebPart(portalCtx);
        }
    };

    public BtOverviewWebPart(ViewContext portalCtx) throws Exception
    {
        super(new BtOverview(portalCtx.getUser(), portalCtx.getContainer()).toString());
        setTitle("Server Management");
    }

}
