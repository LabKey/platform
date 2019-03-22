/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

package org.labkey.api.study;

import java.util.List;

/**
 * Represents a plate for an assay run (typically 96 or 384 wells).
 * User: brittp
 * Date: Oct 20, 2006
 * Time: 10:15:44 AM
 */
public interface Plate extends PlateTemplate
{
    Well getWell(int row, int col);

    WellGroup getWellGroup(WellGroup.Type type, String wellGroupName);

    List<? extends WellGroup> getWellGroups(WellGroup.Type type);

    List<? extends WellGroup> getWellGroups(Position position);

    List<? extends WellGroup> getWellGroups();

    int getPlateNumber();
}
