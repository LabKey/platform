package org.labkey.core.admin.test;

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.admin.TableXmlUtils;
import org.labkey.api.data.DbSchema;
import org.labkey.api.settings.AppProps;

import java.util.Set;

/**
 * User: tgaluhn
 * Date: 5/1/2015
 *
 * Migrated from DbSchema; no changes made for the migration
 */
public class SchemaXMLTestCase extends Assert
{
    // Compare schema XML vs. meta data for all module schemas
    @Test
    public void testSchemaXML() throws Exception
    {
        Set<DbSchema> schemas = DbSchema.getAllSchemasToTest();

        for (DbSchema schema : schemas)
            testSchemaXml(schema);
    }

    private void testSchemaXml(DbSchema schema) throws Exception
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
