/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
package org.labkey.study.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.study.StudySchema;
import org.labkey.study.model.StudyImpl;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * User: jgarms
 * Date: Aug 7, 2008
 * Time: 4:23:44 PM
 */
public class StudyPropertiesTable extends BaseStudyTable
{
    public StudyPropertiesTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoStudy());

        ColumnInfo labelColumn = addWrapColumn(_rootTable.getColumn("label"));
        labelColumn.setUserEditable(false);

        ColumnInfo startDateColumn = addWrapColumn(_rootTable.getColumn("startDate"));
        startDateColumn.setUserEditable(false);

        ColumnInfo containerColumn = addWrapColumn(_rootTable.getColumn("container"));
        containerColumn.setUserEditable(false);
        containerColumn.setKeyField(true);

        ColumnInfo timepointTypeColumn = addWrapColumn(_rootTable.getColumn("timepointType"));
        timepointTypeColumn.setUserEditable(false);

        String bTRUE = getSchema().getSqlDialect().getBooleanTRUE();
        String bFALSE = getSchema().getSqlDialect().getBooleanFALSE();

        ColumnInfo dateBasedColumn = new ExprColumn(this, "DateBased", new SQLFragment("(CASE WHEN " + ExprColumn.STR_TABLE_ALIAS + ".timepointType != 'VISIT' THEN " + bTRUE + " ELSE " + bFALSE + " END)"), Types.BOOLEAN, timepointTypeColumn);
        dateBasedColumn.setUserEditable(false);
        dateBasedColumn.setHidden(true);
        dateBasedColumn.setDescription("Deprecated.  Use 'timepointType' column instead.");
        addColumn(dateBasedColumn);

        ColumnInfo lsidColumn = addWrapColumn(_rootTable.getColumn("LSID"));
        lsidColumn.setUserEditable(false);
        lsidColumn.setHidden(true);

        List<FieldKey> visibleColumns = new ArrayList<FieldKey>();

        String domainURI = StudyImpl.DOMAIN_INFO.getDomainURI(schema.getContainer());

        Domain domain = PropertyService.get().getDomain(schema.getContainer(), domainURI);
        if (domain != null)
        {
            ColumnInfo[] extraColumns = domain.getColumns(this, lsidColumn, schema.getUser());
            for (ColumnInfo extraColumn : extraColumns)
            {
                safeAddColumn(extraColumn);
                visibleColumns.add(FieldKey.fromParts(extraColumn.getName()));
            }
        }

        setDefaultVisibleColumns(visibleColumns);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        User user = _schema.getUser();
        if (!getContainer().getPolicy().hasPermission(user, AdminPermission.class))
            return null;
        return new StudyPropertiesUpdateService();
    }
}
