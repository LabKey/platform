/*
 * Copyright (c) 2017 LabKey Corporation
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
package org.labkey.api.data;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.query.FieldKey;

/**
 * Indicates that inserts, updates and deletes on a table can be auditable and supports setting
 * different AuditBehaviorType configurations.
 *
 * Created by klum on 4/26/2017.
 */
public interface AuditConfigurable extends TableInfo
{
    void setAuditBehavior(AuditBehaviorType type);
    AuditBehaviorType getAuditBehavior();

    /**
     * Returns the row primary key column to use for audit history details. Note, this must
     * be a single key as we don't support multiple column primary keys for audit details.
     */
    @Nullable
    FieldKey getAuditRowPk();
}
