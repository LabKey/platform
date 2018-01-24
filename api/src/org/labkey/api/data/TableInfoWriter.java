/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.exp.property.Type;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.DefaultScaleType;
import org.labkey.data.xml.DefaultValueEnumType;
import org.labkey.data.xml.FacetingBehaviorType;
import org.labkey.data.xml.PHIType;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.ValidatorsType;

import java.util.Collection;
import java.util.List;

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

        if (column.isRequiredSet())
            columnXml.setRequired(true);

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
            // Export default scale only if not set to LINEAR
            String typeName = column.getDefaultScale().name();
            if (!DefaultScaleType.LINEAR.toString().equals(typeName))
                columnXml.setDefaultScale(DefaultScaleType.Enum.forString(typeName));
        }

        if (null != column.getURL())
            columnXml.setUrl(column.getURL().toXML());

        if (null != column.getTextExpression())
            columnXml.setTextExpression(column.getTextExpression().toXML());

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

        // GWT PropertyEditor always saves Attachment columns with DefaultValueType set to FIXED_EDITABLE. That's a bug we should fix,
        // but we'll workaround it here for now. Also, consider Type.allowsDefaultValue() to generalize this concept.
        if (t != Type.AttachmentType)
        {
            DefaultValueType defaultValueType = column.getDefaultValueType();

            if (null != defaultValueType)
            {
                DefaultValueEnumType.Enum defaultValueTypeXmlBeanEnum = DefaultValueEnumType.Enum.forString(defaultValueType.name());
                columnXml.setDefaultValueType(defaultValueTypeXmlBeanEnum);
            }

            if (defaultValueType != DefaultValueType.LAST_ENTERED && null != column.getDefaultValue())
            {
                columnXml.setDefaultValue(column.getDefaultValue());
            }
        }

        ConditionalFormat.convertToXML(column.getConditionalFormats(), columnXml);

        List<? extends IPropertyValidator> validators = column.getValidators();

        if (!validators.isEmpty())
        {
            // Add the "validators" element and ask each validator to serialize itself to XML.
            ValidatorsType validatorsXml = columnXml.addNewValidators();
            validators.forEach(v->v.getType().convertToXml(v, validatorsXml));

            // Remove the "validators" element if no validators wrote XML. For example, the collection might have just a "text length"
            // validator, which isn't included in the "validators" element (these are serialized as the "scale" property).
            if (0 == validatorsXml.sizeOfValidatorArray())
                columnXml.unsetValidators();
        }

        if (column.getFacetingBehaviorType() != null)
        {
            // issue 14809: export faceting behavior only if not set to AUTOMATIC
            String typeName = column.getFacetingBehaviorType().name();
            if (!FacetingBehaviorType.AUTOMATIC.toString().equals(typeName))
            {
                FacetingBehaviorType.Enum type = FacetingBehaviorType.Enum.forString(typeName);
                columnXml.setFacetingBehavior(type);
            }
        }

        if (column.isExcludeFromShifting())
            columnXml.setExcludeFromShifting(true);

        if (PHI.NotPHI != column.getPHI())
            columnXml.setPhi(PHIType.Enum.forString(column.getPHI().toString()));

        // Export scale only if column is a string
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
