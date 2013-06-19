/*
 * Copyright (c) 2010-2013 LabKey Corporation
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

package org.labkey.query.sql;

import org.labkey.api.query.FieldKey;

/**
 * User: matthewb
 * Date: Dec 1, 2010
 * Time: 3:21:19 PM
 *
 * Marker interface implemented by QTable and QJoin
 */

public interface QJoinOrTable
{
    public void appendSource(SourceBuilder builder);

    public void appendSql(SqlBuilder sql, QuerySelect query);
}
