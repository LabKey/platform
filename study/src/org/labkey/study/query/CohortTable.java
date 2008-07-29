package org.labkey.study.query;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.PropertyForeignKey;
import org.labkey.study.StudySchema;
import org.labkey.study.model.Cohort;
import org.labkey.study.model.StudyManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Copyright (c) 2008 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 * User: brittp
 * Created: Jan 18, 2008 12:53:27 PM
 */
public class CohortTable extends StudyTable
{
    public CohortTable(StudyQuerySchema schema)
    {
        super(schema, StudySchema.getInstance().getTableInfoCohort());

        StudyManager.getInstance().assertCohortsViewable(schema.getContainer(), schema.getUser());

        ColumnInfo labelColumn = addWrapColumn(_rootTable.getColumn("Label"));
        labelColumn.setNullable(false);

        ColumnInfo lsidColumn = addWrapColumn(_rootTable.getColumn("lsid"));
        lsidColumn.setIsHidden(true);
        lsidColumn.setUserEditable(false);
        
        ColumnInfo rowIdColumn = addWrapColumn(_rootTable.getColumn("RowId"));
        rowIdColumn.setIsHidden(true);
        rowIdColumn.setUserEditable(false);

        // Add extended columns
        List<FieldKey> visibleColumns = new ArrayList<FieldKey>();
        visibleColumns.add(FieldKey.fromParts(labelColumn.getName())); // Label is the only thing visible from the hard table

        String domainURI = StudyManager.getInstance().getDomainURI(schema.getContainer(), Cohort.class);

        Domain domain = PropertyService.get().getDomain(schema.getContainer(), domainURI);
        if (domain != null)
        {
            DomainProperty[] domainProperties = domain.getProperties();
            for (DomainProperty property : domainProperties)
            {
                ColumnInfo column = new ExprColumn(this,
                    property.getName(),
                    PropertyForeignKey.getValueSql(
                        lsidColumn.getValueSql(ExprColumn.STR_TABLE_ALIAS),
                        property.getValueSQL(),
                        property.getPropertyId(),
                        true),
                    property.getSqlType());

                column.setScale(property.getScale());
                column.setInputType(property.getInputType());
                column.setDescription(property.getDescription());
                property.initColumn(schema.getUser(), column);
                safeAddColumn(column);
                visibleColumns.add(FieldKey.fromParts(column.getName()));
            }
        }
        
        setDefaultVisibleColumns(visibleColumns);
    }
}
