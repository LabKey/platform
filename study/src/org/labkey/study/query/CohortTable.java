package org.labkey.study.query;

import org.labkey.api.data.*;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.PropertyForeignKey;
import org.labkey.study.StudySchema;

import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
        addWrapColumn(_rootTable.getColumn("Label"));
        addWrapColumn(_rootTable.getColumn("RowId")).setIsHidden(true);

        // Add extended columns
        List<FieldKey> visibleColumns = new ArrayList<FieldKey>();
        visibleColumns.add(new FieldKey(null, "Label"));
        String sqlObjectId = "( SELECT objectid FROM exp.object WHERE exp.object.objecturi = " + ExprColumn.STR_TABLE_ALIAS + ".lsid)";

        FieldKey keyProp = new FieldKey(null, "Property");
        try
        {
            ColumnInfo colProperty = new ExprColumn(this, "property", new SQLFragment(sqlObjectId), Types.INTEGER);
            String propPrefix = new Lsid("Cohort", "Folder-" + schema.getContainer().getRowId(), "Cohort").toString();
            SimpleFilter filter = new SimpleFilter();
            filter.addCondition("PropertyURI", propPrefix, CompareType.STARTS_WITH);
            PropertyDescriptor[] pds = Table.select(OntologyManager.getTinfoPropertyDescriptor(), Table.ALL_COLUMNS, filter, null, PropertyDescriptor.class);
            Map<String, PropertyDescriptor> map = new TreeMap<String, PropertyDescriptor>();
            for(PropertyDescriptor pd : pds)
            {
                map.put(pd.getName(), pd);
                visibleColumns.add(new FieldKey(keyProp, pd.getName()));
            }
            colProperty.setFk(new PropertyForeignKey(map, schema));
            colProperty.setIsUnselectable(true);
            addColumn(colProperty);
            setDefaultVisibleColumns(visibleColumns);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }
}
