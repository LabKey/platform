/*
 * Copyright (c) 2015-2016 LabKey Corporation
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
package org.labkey.core.admin.test;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.labkey.api.admin.TableXmlUtils;
import org.labkey.api.data.DbSchema;
import org.labkey.api.settings.AppProps;
import org.labkey.api.test.TestTimeout;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@RunWith(Parameterized.class)
@TestTimeout(240)
public class SchemaXMLTestCase extends Assert
{
    @Parameterized.Parameters(name = "{1}")
    public static Collection schemas()
    {
        List<Object[]> parameters = new ArrayList<>();

        for (DbSchema schema : DbSchema.getAllSchemasToTest())
        {
            parameters.add(new Object[]{schema, schema.getDisplayName()});
        }

        return parameters;
    }

    private DbSchema schemaToTest;

    public SchemaXMLTestCase(DbSchema schemaToTest, String displayName)
    {
        this.schemaToTest = schemaToTest;
    }

    // Compare schema XML vs. meta data for all module schemas
    @Test
    public void testSchemaXML()
    {
        testSchemaXml(schemaToTest);
    }

    private void testSchemaXml(DbSchema schema)
    {
        String sOut = TableXmlUtils.compareXmlToMetaData(schema, false, false, true).getResultsString();

        // Not using assertNotNull, because it appends non-legal HTML text to our message
        if (null != sOut)
            fail("<div>Errors in schema " + schema.getDisplayName()
                    + ".xml.  <a href=\"" + AppProps.getInstance().getContextPath() + "/admin/getSchemaXmlDoc.view?dbSchema="
                    + schema.getDisplayName() + "\">Click here for an XML doc with fixes</a>."
                    + "<br>"
                    + sOut + "</div>");

/* TODO: Uncomment once we change to all generic type names in schema .xml files

        StringBuilder typeErrors = new StringBuilder();

        for (TableInfo ti : schema.getTables())
        {
            for (ColumnInfo ci : ti.getColumns())
            {
                String sqlTypeName = ci.getSqlTypeName();

                if ("OTHER".equals(sqlTypeName))
                    typeErrors.append(ti.getName()).append(".").append(ci.getColumnName()).append(": getSqlTypeName() returned 'OTHER'<br>");

                int sqlTypeInt = ci.getSqlTypeInt();

                if (Types.OTHER == sqlTypeInt)
                    typeErrors.append(ti.getName()).append(".").append(ci.getColumnName()).append(": getSqlTypeInt() returned 'Types.OTHER'<br>");
            }
        }

        assertTrue("<div>Type errors in schema " + schema.getName() + ":<br><br>" + typeErrors + "<div>", "".equals(typeErrors.toString()));
*/
    }
}
