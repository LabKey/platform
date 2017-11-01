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
import java.util.Map;

/**
 * User: brittp
 * Date: Oct 20, 2006
 * Time: 1:02:47 PM
 */
public interface PlateTemplate extends PropertySet
{
    String getName();

    void setName(String name);

    int getRows();

    int getColumns();

    List<? extends WellGroupTemplate> getWellGroups();

    List<? extends WellGroupTemplate> getWellGroups(Position position);

    Map<WellGroup.Type, Map<String, WellGroupTemplate>> getWellGroupTemplateMap();

    WellGroupTemplate addWellGroup(String name, WellGroup.Type type, Position upperLeft, Position lowerRight);

    WellGroupTemplate addWellGroup(String name, WellGroup.Type type, List<Position> positions);

    Integer getRowId();

    Position getPosition(int row, int col);

    int getWellGroupCount();

    int getWellGroupCount(WellGroup.Type type);

    String getType();
}
