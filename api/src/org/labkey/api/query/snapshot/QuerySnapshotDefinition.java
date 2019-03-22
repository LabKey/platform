/*
 * Copyright (c) 2008-2015 LabKey Corporation
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
package org.labkey.api.query.snapshot;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.data.Container;

import java.util.List;
import java.util.Date;/*
 * User: Karl Lum
 * Date: Jul 14, 2008
 * Time: 12:51:32 PM
 */

public interface QuerySnapshotDefinition
{
    String getName();
    String getQueryTableName();
    String getQueryTableContainerId();
    int getId();
    @Nullable
    QueryDefinition getQueryDefinition(User user);

    boolean canEdit(User user);
    void save(User user) throws Exception;
    void delete(User user) throws Exception;
    User getCreatedBy();
    User getModifiedBy();
    Container getContainer();

    List<FieldKey> getColumns();
    void setColumns(List<FieldKey> columns);
    void setFilter(String filter);
    String getFilter();
    List<Integer> getParticipantGroups();
    void setParticipantGroups(List<Integer> groups);
    void setQueryTableName(String queryTableName);
    void setName(String name);

    Date getCreated();
    Date getLastUpdated();
    void setLastUpdated(Date date);
    Date getNextUpdate();
    void setNextUpdate(Date date);

    int getUpdateDelay();
    void setUpdateDelay(int delayInSeconds);
    void setOptionsId(Integer optionsId);
    @Nullable Integer getOptionsId();
}