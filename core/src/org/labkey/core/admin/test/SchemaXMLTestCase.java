/*
 * Copyright (c) 2015-2018 LabKey Corporation
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
import org.labkey.api.admin.sitevalidation.SiteValidationResultList;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.test.TestTimeout;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.DOM;
import org.labkey.api.view.ActionURL;
import org.labkey.core.admin.AdminController;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.TableType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@TestWhen(TestWhen.When.SMOKE)
@RunWith(Parameterized.class)
@TestTimeout(240)
public class SchemaXMLTestCase extends Assert
{
    @Parameterized.Parameters(name = "{1}")
    public static Collection<Object[]> schemas()
    {
        List<Object[]> parameters = new ArrayList<>();

        for (DbSchema schema : DbSchema.getAllSchemasToTest())
        {
            parameters.add(new Object[]{schema, schema.getDisplayName()});
        }

        return parameters;
    }

    private final DbSchema schemaToTest;

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
        SiteValidationResultList mismatches = TableXmlUtils.compareXmlToMetaData(schema, false, false, true);
        // Not using assertNotNull, because it appends non-legal HTML text to our message
        if (mismatches.hasErrors())
        {
            ActionURL url = new ActionURL(AdminController.GetSchemaXmlDocAction.class, ContainerManager.getRoot()).addParameter("dbSchema", schema.getDisplayName());
            fail(DOM.DIV("Errors in schema " + schema.getDisplayName() + ".xml ",
                DOM.A(DOM.at(DOM.Attribute.href, url), "Click here for an XML doc with fixes"),
                DOM.BR(),
                mismatches.getResults().stream().map(r -> DOM.DIV(r.getMessage()))
            ).renderToString());
        }

        StringBuilder typeErrors = new StringBuilder();
        var xmlTableMap = schema.getTableXmlMap();

        for (var tableName : schema.getTableNames())
        {
            SchemaTableInfo ti = schema.getTable(tableName);
            var xmlColMap = new CaseInsensitiveHashMap<ColumnType>();
            TableType xmlTable = xmlTableMap.get(ti.getMetaDataName());
            if (null != xmlTable)
            {
                for (ColumnType ct : xmlTable.getColumns().getColumnArray())
                    xmlColMap.put(ct.getColumnName(), ct);
            }

            for (ColumnInfo ci : ti.getColumns())
            {
                if ( DatabaseTableType.NOT_IN_DB == ti.getTableType())
                    continue;
                String sqlTypeName = ci.getSqlTypeName();
                var jdbcType = ci.getJdbcType();
                if ("OTHER".equalsIgnoreCase(sqlTypeName) || JdbcType.OTHER == jdbcType)
                {
                    // postgres "interval" really is OTHER (for now at least), see Fermentation.Timezones
                    if ("INTERVAL".equalsIgnoreCase(sqlTypeName) || SqlDialect.isJSONType(sqlTypeName))
                    {
                        /* pass; this is fine */
                    }
                    else
                    {
                        typeErrors.append("%s.%s: getSqlTypeName() returned '%s', getJdbcType() returned '%s'<br>".formatted(ti.getName(), ci.getColumnName(), sqlTypeName, jdbcType.name()));
                    }
                }
                if ("TIMESTAMP".equalsIgnoreCase(sqlTypeName))
                {
                    // TIMESTAMP is a legal type, however we discourage its use in xml schema because of the non-standard SQL Server usage of the name
                    var colType = xmlColMap.get(ci.getColumnName());
                    var xmlDatatype = null != colType && colType.isSetDatatype() ? colType.getDatatype() : null;
                    if ("TIMESTAMP".equalsIgnoreCase(xmlDatatype))
                        typeErrors.append("%s.%s: use DATETIME rather than TIMESTAMP in schema xml file<br>".formatted(ti.getName(), ci.getColumnName()));
                }
            }
        }
        assertTrue("<div>Type errors in schema " + schema.getName() + ":<br><br>" + typeErrors + "<div>", typeErrors.toString().isEmpty());
    }
}
