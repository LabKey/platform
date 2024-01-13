/*
 * Copyright (c) 2019 LabKey Corporation
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

package org.labkey.api.assay.plate;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;

import java.util.List;
import java.sql.SQLException;
import java.util.Map;

/**
 * Represents a handler that can create assay specific plate layouts
 */
public interface PlateLayoutHandler
{
    @NotNull
    String getAssayType();

    @NotNull List<String> getLayoutTypes(PlateType plateType);

    /**
     * createTemplate will be given a null value for templateTypeName when it is creating a new template which is a
     * default for that assay type.
     */
    Plate createTemplate(@Nullable String templateTypeName, Container container, int rowCount, int colCount) throws SQLException;

    List<PlateType> getSupportedPlateTypes();

    List<WellGroup.Type> getWellGroupTypes();

    /**
     * Issue 47210 : some assays don't support changing or adding new well groups
     */
    boolean canCreateNewGroups(WellGroup.Type type);

    /**
     * Validate a new or edited plate template for handler specific errors.
     */
    void validateTemplate(Container container, User user, Plate template) throws ValidationException;

    Map<String, List<String>> getDefaultGroupsForTypes();

    boolean showEditorWarningPanel();
}
