/*
 * Copyright (c) 2024-2026 LabKey Corporation
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

import org.labkey.api.exp.Identifiable;

import java.util.List;

public interface PlateSet extends Identifiable
{
    int MAX_PLATES = 60;
    int MAX_PLATE_WELL_SIZE = 384;
    int MAX_PLATE_SET_WELLS = MAX_PLATES * MAX_PLATE_WELL_SIZE;

    Long getRowId();

    String getDescription();

    String getPlateSetId();

    boolean isArchived();

    boolean isAssay();

    boolean isPrimary();

    boolean isStandalone();

    boolean isTemplate();

    List<? extends Plate> getPlates();

    PlateSetType getType();

    Long getRootPlateSetId();
}
