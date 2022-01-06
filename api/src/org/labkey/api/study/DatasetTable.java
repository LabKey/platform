/*
 * Copyright (c) 2015-2019 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.roles.Role;

import java.util.Map;
import java.util.function.Predicate;

/**
 * Table backed by a {@link Dataset}
 * User: brittp
 * Date: Sep 28, 2011 5:03:09 PM
 */
public interface DatasetTable extends TableInfo
{
    Dataset getDataset();
    DataIterator getPrimaryKeyDataIterator(DataIterator it, DataIteratorContext context);
    void addContextualRole(Role contextualRole);

    /* for user with restricted edit permissions */
    void setCanModifyParticipantPredicate(Predicate<String> edit);
    boolean canUpdateRowForParticipant(String ptid);

    // pass-through to DatasetQueryUpdateService.getParticipant(Row,user,container)
    String getParticipant(Map<String, Object> row);
}
