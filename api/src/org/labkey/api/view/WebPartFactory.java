/*
 * Copyright (c) 2008-2016 LabKey Corporation
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
 * Factory for creating {@link WebPartView} instances. Used to assemble portal pages, where admins can add, remove,
 * and configure their desired web parts.
 * User: matthewb
 * Date: Oct 16, 2008
 */
public interface WebPartFactory
{
    String LOCATION_RIGHT = "right";
    String LOCATION_MENUBAR = "menubar";
    String LOCATION_BODY = "!content";

    /** Web parts should have unique names across all modules */
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

    /** @return the locations ({@link #LOCATION_RIGHT}, {@link #LOCATION_MENUBAR}, {@link #LOCATION_BODY} in which the web part is allowed to be placed */
    Set<String> getAllowableLocations();

    WebPartView getWebPartView(@NotNull ViewContext portalCtx, @NotNull Portal.WebPart webPart) throws WebPartConfigurationException;

    HttpView getEditView(Portal.WebPart webPart, ViewContext context);

    Portal.WebPart createWebPart();

    /**
     * For web parts that can be placed in multiple locations and configure themselves differently
     * depending on where they are placed, create an instance for the desired location
     */
    Portal.WebPart createWebPart(String location);

    boolean isEditable();

    /**
     * @return true if after adding this web part to a portal page, the user should be taken to the configuration
     * page to finish setting it up
     */
    boolean showCustomizeOnInsert();

    Module getModule();

    void setModule(Module module);

    /** For backwards compatibility, names that this web part might have been previously called and should still match it for existing portal configurations */
    List<String> getLegacyNames();

    boolean isAvailable(Container c, String location);

    Map<String, String> serializePropertyMap(ImportContext ctx, Map<String, String> propertyMap);

    Map<String, String> deserializePropertyMap(ImportContext ctx, Map<String, String> propertyMap);

    /*
     * This method is used to determine if the given web part should be included in folder
     * export (e.g. in PageWriterFactory.addWebPartsToPage())
     * It was added to fix Issue 22261: Incorrect links in the "Wiki Table of Contents" web part.
     */
    boolean includeInExport(ImportContext ctx, Portal.WebPart webPart);
}
