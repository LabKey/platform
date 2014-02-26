/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
package org.labkey.study.query.studydesign;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.StudyQuerySchema;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by klum on 12/12/13.
 */
public class StudyProductTable extends DefaultStudyDesignTable
{
    static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static {

        defaultVisibleColumns.add(FieldKey.fromParts("Container"));
        defaultVisibleColumns.add(FieldKey.fromParts("Label"));
        defaultVisibleColumns.add(FieldKey.fromParts("Role"));
        defaultVisibleColumns.add(FieldKey.fromParts("Type"));
    }

    public StudyProductTable(Domain domain, DbSchema dbSchema, UserSchema schema)
    {
        super(domain, dbSchema, schema);

        setName(StudyQuerySchema.PRODUCT_TABLE_NAME);
        setDescription("Contains one row per study product");
    }

    @Override
    protected void initColumn(ColumnInfo col)
    {
        if ("Type".equalsIgnoreCase(col.getName()))
        {
            col.setFk(new LookupForeignKey("Name")
            {
                public TableInfo getLookupTableInfo()
                {
                    StudyImpl study = StudyManager.getInstance().getStudy(getContainer());
                    if (study != null)
                    {
                        StudyQuerySchema schema = new StudyQuerySchema(study, _userSchema.getUser(), false);
                        return new StudyDesignImmunogenTypesTable(schema);
                    }
                    else
                        return null;
                }
            });
        }
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }
}
