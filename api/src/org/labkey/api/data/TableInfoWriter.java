/*
 * Copyright (c) 2009-2013 LabKey Corporation
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

package org.labkey.api.data;

import org.labkey.api.util.DateUtil;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.FacetingBehaviorType;
import org.labkey.data.xml.TableType;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Date;

/*
* User: adam
* Date: Sep 24, 2009
* Time: 1:21:59 PM
*/
public class TableInfoWriter
{
    private final Container _c;
    private final TableInfo _ti;
    private final Collection<ColumnInfo> _columns;
    private final String _defaultDateFormat;

    protected TableInfoWriter(Container c, TableInfo ti, Collection<ColumnInfo> columns)
    {
        _c = c;
        _ti = ti;
        _columns = columns;
        _defaultDateFormat = DateUtil.getDateFormatString(_c);
    }

    // Append a new table to the Tables document
    public void writeTable(TableType tableXml)
    {
        // Write metadata
        tableXml.setTableName(_ti.getName());
        tableXml.setTableDbType("TABLE");
        if (null != _ti.getDescription())
            tableXml.setDescription(_ti.getDescription());

        if (!_ti.hasDefaultTitleColumn())
            tableXml.setTitleColumn(_ti.getTitleColumn());

        TableType.Columns columnsXml = tableXml.addNewColumns();

        for (ColumnInfo column : _columns)
        {
            if (column.isMvIndicatorColumn())
                continue;
            ColumnType columnXml = columnsXml.addNewColumn();
            writeColumn(column, columnXml);
        }
    }

    public void writeColumn(ColumnInfo column, ColumnType columnXml)
    {
        String columnName = column.getName();
        columnXml.setColumnName(columnName);

        JdbcType jdbcType = column.getJdbcType();
        if (jdbcType == JdbcType.OTHER)
            jdbcType = JdbcType.valueOf(column.getJavaClass());

        if (null == jdbcType)
            throw new IllegalStateException(columnName + " in table " + column.getParentTable().getName() + " has unknown java class " + column.getJavaClass());

        columnXml.setDatatype(jdbcType.name().toLowerCase());

        if (column.getInputType().equals("textarea"))
            columnXml.setInputType(column.getInputType());

        if (null != column.getLabel())
            columnXml.setColumnTitle(column.getLabel());

        if (null != column.getDescription())
            columnXml.setDescription(column.getDescription());

        String propertyURI = getPropertyURI(column);
        if (propertyURI != null)
            columnXml.setPropertyURI(propertyURI);

        String conceptURI = getConceptURI(column);
        if (conceptURI != null)
            columnXml.setConceptURI(conceptURI);

        if (!column.isNullable())
            columnXml.setNullable(false);

        if (column.isHidden())
            columnXml.setIsHidden(true);
        if (!column.isShownInInsertView())
            columnXml.setShownInInsertView(false);
        if (!column.isShownInUpdateView())
            columnXml.setShownInUpdateView(false);
        if (!column.isShownInDetailsView())
            columnXml.setShownInDetailsView(false);

        if (column.isDimension() != ColumnRenderProperties.inferIsDimension(column))
            columnXml.setDimension(column.isDimension());

        if (column.isMeasure() != ColumnRenderProperties.inferIsMeasure(column))
            columnXml.setMeasure(column.isMeasure());

        if (null != column.getURL())
            columnXml.setUrl(column.getURL().getSource());

        if (!column.getImportAliasSet().isEmpty())
        {
            ColumnType.ImportAliases importAliasesXml = columnXml.addNewImportAliases();
            for (String importAlias : column.getImportAliasSet())
            {
                importAliasesXml.addImportAlias(importAlias);
            }
        }

        String formatString = column.getFormat();

        // Write only if it's non-null (and in the case of dates, different from the global default)
        if (null != formatString && (!Date.class.isAssignableFrom(column.getJavaClass()) || !formatString.equals(_defaultDateFormat)))
            columnXml.setFormatString(formatString);

        if (column.isMvEnabled())
            columnXml.setIsMvEnabled(true);

        ForeignKey fk = column.getFk();

        if (null != fk && null != fk.getLookupColumnName())
        {
            TableInfo tinfo = fk.getLookupTableInfo();
            if (tinfo != null)
            {
                // Make sure public Name and SchemaName aren't null before adding the FK
                String tinfoPublicName = tinfo.getPublicName();
                String tinfoPublicSchemaName = tinfo.getPublicSchemaName();
                if (null != tinfoPublicName && null != tinfoPublicSchemaName)
                {
                    ColumnType.Fk fkXml = columnXml.addNewFk();

                    Container fkContainer = fk.getLookupContainer();

                    // If null or explicitly set to current container then don't set anything in the XML
                    if (null != fkContainer && !fkContainer.equals(_c))
                    {
                        fkXml.setFkFolderPath(fkContainer.getPath());
                    }

                    fkXml.setFkDbSchema(tinfoPublicSchemaName);
                    fkXml.setFkTable(tinfoPublicName);
                    fkXml.setFkColumnName(fk.getLookupColumnName());
                }
            }
        }

        // TODO: Field validators?
        // TODO: Default values / Default value types
        ConditionalFormat.convertToXML(column.getConditionalFormats(), columnXml);

        if (column.getFacetingBehaviorType() != null)
        {
            // issue 14809: only export faceting behavior if not set to AUTOMATIC
            String typeName = column.getFacetingBehaviorType().name();
            if (!FacetingBehaviorType.AUTOMATIC.toString().equals(typeName))
            {
                FacetingBehaviorType.Enum type = FacetingBehaviorType.Enum.forString(typeName);
                columnXml.setFacetingBehavior(type);
            }
        }

        if (column.isProtected())
            columnXml.setProtected(true);

        if (column.isExcludeFromShifting())
            columnXml.setExcludeFromShifting(true);
    }

    protected String getConceptURI(ColumnInfo column)
    {
        return column.getConceptURI();
    }

    /**
     * Get the propertyURI of the ColumnInfo or null if no uri should be written.
     * @return The propertyURI to be written or null.
     */
    @Nullable
    protected String getPropertyURI(ColumnInfo column)
    {
        String propertyURI = column.getPropertyURI();
        if (propertyURI != null && !propertyURI.startsWith(ColumnInfo.DEFAULT_PROPERTY_URI_PREFIX))
            return propertyURI;

        return null;
    }
}
