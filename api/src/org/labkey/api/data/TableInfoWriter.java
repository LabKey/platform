/*
 * Copyright (c) 2009-2015 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.exp.property.Type;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.DefaultScaleType;
import org.labkey.data.xml.FacetingBehaviorType;
import org.labkey.data.xml.PhiType;
import org.labkey.data.xml.TableType;

import java.util.Collection;

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

    protected TableInfoWriter(Container c, TableInfo ti, Collection<ColumnInfo> columns)
    {
        _c = c;
        _ti = ti;
        _columns = columns;
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

        Class clazz = column.getJavaClass();
        Type t = Type.getTypeByClass(clazz);

        if (null == t)
            throw new IllegalStateException(columnName + " in table " + column.getParentTable().getName() + " has unknown java class " + clazz.getName());

        columnXml.setDatatype(t.getSqlTypeName());

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

        String rangeURI = getRangeURI(column);
        if (rangeURI != null)
            columnXml.setRangeURI(rangeURI);

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

        if (column.isRecommendedVariable())
            columnXml.setRecommendedVariable(true);

        if (column.getDefaultScale() != null)
        {
            // only export default scale if not set to LINEAR
            String typeName = column.getDefaultScale().name();
            if (!DefaultScaleType.LINEAR.toString().equals(typeName))
                columnXml.setDefaultScale(DefaultScaleType.Enum.forString(typeName));
        }

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

        if (column.isFormatStringSet())
            columnXml.setFormatString(column.getFormat());

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

        if (PHI.NotPHI != column.getPHI())
            columnXml.setPhi(PhiType.Enum.forString(column.getPHI().toString()));

        //Only export scale if column is a string
        if (column.isStringType())
            columnXml.setScale(column.getScale());
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

    protected String getRangeURI(ColumnInfo column)
    {
        return column.getRangeURI();
    }
}
