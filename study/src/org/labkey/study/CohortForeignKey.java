/*
 * Copyright (c) 2012-2014 LabKey Corporation
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
package org.labkey.study;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.NullColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.LookupForeignKey;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.CohortTable;
import org.labkey.study.query.StudyQuerySchema;

/**
 * User: matthewb
 * Date: 2012-12-11
 * Time: 9:55 AM
 */
public class CohortForeignKey extends LookupForeignKey
{
    final StudyQuerySchema _schema;
    final boolean _showCohorts;
    final String _labelCaption;


    public CohortForeignKey(StudyQuerySchema schema)
    {
        this(schema, StudyManager.getInstance().showCohorts(schema.getContainer(), schema.getUser()), null);
    }


    public CohortForeignKey(StudyQuerySchema schema, boolean showCohorts, String labelCaption)
    {
        assert showCohorts == StudyManager.getInstance().showCohorts(schema.getContainer(), schema.getUser());
        _schema = schema;
        _showCohorts = showCohorts;
        _labelCaption = labelCaption;
//        addJoin(new FieldKey(null,"Folder"), "Folder", false);
    }


    @Override
    public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
    {
        ColumnInfo c = super.createLookupColumn(parent, displayField);
        if (null == c)
            return null;

        if (!_showCohorts)
            c = new NullColumnInfo(parent.getParentTable(), c.getFieldKey(), c.getJdbcType());

        if (c.getFieldKey().getName().equalsIgnoreCase("Label") && !StringUtils.isEmpty(_labelCaption))
            c.setLabel(_labelCaption);
        return c;
    }


    public TableInfo getLookupTableInfo()
    {
        return new CohortTable(_schema);
    }
}
