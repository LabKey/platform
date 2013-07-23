/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.api.ehr.history;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * User: bimber
 * Date: 2/17/13
 * Time: 4:50 PM
 *
 *
 * This class is responsible for selecting rows from a target table,
 * and converting them into the display HTML
 */
public interface HistoryDataSource
{
    public String getName();

    public boolean isAvailable(Container c, User u);

    public Set<String> getAllowableCategoryGroups(Container c, User u);

    public List<HistoryRow> getRows(Container c, User u, String subjectId, Date minDate, Date maxDate, boolean redacted);

    public List<HistoryRow> getRows(Container c, User u, String subjectId, String caseId, boolean redacted);
}
