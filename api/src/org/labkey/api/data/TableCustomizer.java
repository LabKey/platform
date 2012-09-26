package org.labkey.api.data;

/**
 * A simple interface to provide a hook in .query.xml files for customizing a TableInfo via Java code.
 * To add a reference, include this inside of the &lt;table> element in the XML:
 * &lt;javaCustomizer>org.labkey.ehr.TableCustomizerImpl&lt;/javaCustomizer>
 *
 * User: jeckels
 * Date: 9/25/12
 */
public interface TableCustomizer
{
    public void customize(TableInfo tableInfo);
}
