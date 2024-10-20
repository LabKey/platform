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
import org.labkey.api.exp.Identifiable;
import org.labkey.api.study.PropertySet;
import org.labkey.api.view.ActionURL;

import java.util.List;
import java.util.Map;

public interface Plate extends PropertySet, Identifiable
{
    @Override
    String getName();

    void setName(String name);

    String getBarcode();

    void setBarcode(String barcode);

    int getRows();

    int getColumns();

    int getPlateNumber();

    boolean isArchived();

    boolean isTemplate();

    @NotNull PlateType getPlateType();

    @Nullable PlateSet getPlateSet();

    @NotNull String getPlateId();

    /**
     * Returns an existing well, or creates a new well if one
     * had not previously existed.
     */
    @NotNull Well getWell(int row, int col);

    @Nullable Well getWell(int rowId);

    @NotNull List<Well> getWells();

    @Nullable WellGroup getWellGroup(WellGroup.Type type, String wellGroupName);

    @Nullable WellGroup getWellGroup(int rowId);

    @NotNull List<WellGroup> getWellGroups();

    @NotNull List<WellGroup> getWellGroups(Position position);

    @NotNull List<WellGroup> getWellGroups(WellGroup.Type type);

    @NotNull Map<WellGroup.Type, Map<String, WellGroup>> getWellGroupMap();

    @NotNull WellGroup addWellGroup(String name, WellGroup.Type type, Position upperLeft, Position lowerRight);

    @NotNull WellGroup addWellGroup(String name, WellGroup.Type type, List<Position> positions);

    Integer getRowId();

    @NotNull Position getPosition(int row, int col);

    int getWellGroupCount();

    int getWellGroupCount(WellGroup.Type type);

    String getAssayType();

    @Override
    @Nullable ActionURL detailsURL();

    /**
     * The list of metadata fields that are configured for this plate
     */
    @NotNull List<PlateCustomField> getCustomFields();

    Plate copy();

    /**
     * Returns the domain ID for the plate metadata domain.
     */
    @Nullable Integer getMetadataDomainId();

    @Nullable Integer getRunCount();

    boolean isIdentifierMatch(String id);

    boolean isNew();
}
