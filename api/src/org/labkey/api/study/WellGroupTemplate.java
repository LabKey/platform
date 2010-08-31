/*
 * Copyright (c) 2006-2010 LabKey Corporation
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
 * User: brittp
 * Date: Oct 23, 2006
 * Time: 1:33:19 PM
 */
public interface WellGroupTemplate extends PropertySet
{
    Integer getRowId();

    List<Position> getPositions();

    WellGroup.Type getType();

    String getName();

    boolean contains(Position position);

    String getPositionDescription();
}
