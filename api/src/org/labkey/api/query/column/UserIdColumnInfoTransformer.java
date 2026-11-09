/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.api.query.column;

import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.query.UserIdQueryForeignKey;
import org.labkey.api.query.UserSchema;

public class UserIdColumnInfoTransformer implements ConceptURIColumnInfoTransformer
{
    @Override
    public @NotNull String getConceptURI()
    {
        return BuiltInColumnTypes.USERID_CONCEPT_URI;
    }

    @Override
    public MutableColumnInfo apply(MutableColumnInfo column)
    {
        if (column.getJdbcType() != JdbcType.INTEGER)
        {
            LogManager.getLogger(UserIdColumnInfoTransformer.class).error("Column is not of type INT: {}", column.getName());
            return column;
        }

        UserSchema schema = column.getParentTable().getUserSchema();
        BuiltInColumnTypes builtin = BuiltInColumnTypes.findBuiltInType(column);

        if (null == column.getFk() && null != schema && schema.getDbSchema().getScope().isLabKeyScope())
            column.setFk(new UserIdQueryForeignKey(schema, builtin!=null));
        column.setDisplayColumnFactory(UserIdQueryForeignKey._factoryBlank);

        if (null != builtin)
        {
            column.setUserEditable(false);
            column.setShownInInsertView(false);
            column.setShownInUpdateView(false);
            column.setReadOnly(true);
        }
        return column;
    }
}