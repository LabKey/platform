/*
 * Copyright (c) 2009 LabKey Corporation
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

import org.labkey.api.data.TableInfo;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.User;
import org.labkey.api.view.UnauthorizedException;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * User: kevink
 * Date: May 27, 2009
 */
public interface DataSet extends StudyEntity
{
    Set<String> getDefaultFieldNames();

    String getName();

    String getFileName();

    String getCategory();

    int getDataSetId();

    String getTypeURI();

    String getPropertyURI(String column);

    TableInfo getTableInfo(User user) throws UnauthorizedException;

    boolean isDemographicData();

    Study getStudy();

    Integer getCohortId();

    @Nullable
    Cohort getCohort();

    Integer getProtocolId();

    public boolean canRead(User user);

    public boolean canWrite(User user);
}
