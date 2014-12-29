/*
 * Copyright (c) 2008-2014 LabKey Corporation
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
package org.labkey.api.view;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: matthewb
 * Date: Oct 16, 2008
 * Time: 10:21:49 AM
 */
public interface WebPartFactory
{
    String LOCATION_RIGHT = "right";
    String LOCATION_MENUBAR = "menubar";
    String LOCATION_BODY = "!content";

    String getName();

    /**
     * Returns a display name that will appear in the 'add webpart' drop down.  This name can be customized for the
     * current container.  Used by the study module, for example, to provide allow the subjects webpart to appear
     * with a name that reflects the study-specific customized subject noun.
     * @param container The container in which this webpart will be added.
     * @param location The location on the page in which this webpart will be added.
     * @return A string title for the webpart.
     */
    String getDisplayName(Container container, String location);

    String getDefaultLocation();

    Set<String> getAllowableLocations();

    WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart) throws WebPartConfigurationException;

    HttpView getEditView(Portal.WebPart webPart, ViewContext context);

    Portal.WebPart createWebPart();

    Portal.WebPart createWebPart(String location);

    boolean isEditable();

    boolean showCustomizeOnInsert();

    Module getModule();

    void setModule(Module module);

    List<String> getLegacyNames();

    boolean isAvailable(Container c, String location);

    Map<String, String> serializePropertyMap(ImportContext ctx, Map<String, String> propertyMap);

    Map<String, String> deserializePropertyMap(ImportContext ctx, Map<String, String> propertyMap);
}
