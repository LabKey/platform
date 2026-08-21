/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
    public WebPartView<?> getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart)
    {
        if (!portalCtx.hasPermission(ReadPermission.class))
            return new HtmlView("Specimens", HtmlString.of(portalCtx.getUser().isGuest() ? "Please log in to see this data." : "You do not have permission to see this data"));

        if (null == StudyService.get().getStudy(portalCtx.getContainer()))
            return new HtmlView("Specimens", HtmlString.of("This folder does not contain a study."));
        return new SpecimenSearchWebPart(true);
    }
}
