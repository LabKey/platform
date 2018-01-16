/*
 * Copyright (c) 2014-2017 LabKey Corporation
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
package org.labkey.api.exp;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.admin.ImportException;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ColumnRenderProperties;
import org.labkey.api.data.ConditionalFormat;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PHI;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.exp.OntologyManager.ImportPropertyDescriptorsList;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.exp.property.ValidatorKind;
import org.labkey.api.gwt.client.DefaultScaleType;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.FacetingBehaviorType;
import org.labkey.api.query.ValidationException;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.StringExpressionType;
import org.labkey.data.xml.TableType;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.labkey.api.exp.OntologyManager.MV_INDICATOR_SUFFIX;

/**
 * Created by klum on 2/7/14.
 */
public class ImportTypesHelper
{
    protected TableType _tableXml;
    protected String _typeColumnName;
    protected Object _typeColumnValue;

    public ImportTypesHelper(TableType tableXml, String typeColumnName, Object typeColumnValue)
    {
        _tableXml = tableXml;
        _typeColumnName = typeColumnName;
        _typeColumnValue = typeColumnValue;
    }

    protected boolean acceptColumn(String columnName, ColumnType columnXml) throws Exception
    {
        return true;
    }

    public List<Builder> createPropertyDescriptorBuilders(Container defaultContainer) throws Exception
    {
        List<Builder> builders = new ArrayList<>();

        if (_tableXml.getColumns() != null)
        {
            for (ColumnType columnXml : _tableXml.getColumns().getColumnArray())
            {
                String columnName = columnXml.getColumnName();
                if (!acceptColumn(columnName, columnXml))
                    continue;

                String dataType = columnXml.getDatatype();
                PropertyType pt = PropertyType.getFromURI(columnXml.getConceptURI(), columnXml.getRangeURI(), null);
                if (pt == null && dataType != null)
                {
                    pt = PropertyType.getFromJdbcTypeName(dataType);
                    if (pt == null)
                        pt = PropertyType.getFromXarName(dataType, null);
                }

                if ("entityid".equalsIgnoreCase(dataType))
                {
                    // Special case handling for GUID keys
                    pt = PropertyType.STRING;
                    columnXml.setScale(100);
                }

                if (pt == null)
                    throw new ImportException("Unknown property type \"" + dataType + "\" for property \"" + columnXml.getColumnName() + "\".");

                Builder builder = new Builder(defaultContainer, pt);
                builder.setName(columnName);
                builder.setDomainName((String)_typeColumnValue);
                builder.setConceptURI(columnXml.getConceptURI());
                builder.setPropertyURI(columnXml.getPropertyURI());

                // Assume nullable if not specified
                builder.setNullable(!(columnXml.isSetNullable() && !columnXml.getNullable()));
                builder.setRequired(columnXml.isSetRequired() && columnXml.getRequired());

                builder.setMvEnabled(columnXml.isSetIsMvEnabled() ? columnXml.getIsMvEnabled() : null != columnXml.getMvColumnName());

                // These default to being visible if nothing's specified in the XML
                builder.setShowInInsertView(!columnXml.isSetShownInInsertView() || columnXml.getShownInInsertView());
                builder.setShowInUpdateView(!columnXml.isSetShownInUpdateView() || columnXml.getShownInUpdateView());
                builder.setShowInDetailView(!columnXml.isSetShownInDetailsView() || columnXml.getShownInDetailsView());

                if (columnXml.isSetMeasure())
                    builder.setMeasure(columnXml.getMeasure());
                else
                    builder.setMeasure(ColumnRenderProperties.inferIsMeasure(columnXml.getColumnName(), columnXml.getColumnTitle(), pt.getJdbcType().isNumeric(), columnXml.getIsAutoInc(), columnXml.getFk() != null, columnXml.getIsHidden()));

                if (columnXml.isSetDimension())
                    builder.setDimension(columnXml.getDimension());
                else
                    builder.setDimension(ColumnRenderProperties.inferIsDimension(columnXml.getColumnName(), columnXml.getFk() != null, columnXml.getIsHidden()));

                if (columnXml.isSetRecommendedVariable())
                    builder.setRecommendedVariable(columnXml.getRecommendedVariable());
                else if (columnXml.isSetKeyVariable())
                    builder.setRecommendedVariable(columnXml.getKeyVariable());

                org.labkey.data.xml.DefaultScaleType.Enum scaleType = columnXml.getDefaultScale();
                builder.setDefaultScale(scaleType != null ? scaleType.toString() : DefaultScaleType.LINEAR.toString());

                org.labkey.data.xml.FacetingBehaviorType.Enum type = columnXml.getFacetingBehavior();
                builder.setFacetingBehavior(type != null ? type.toString() : FacetingBehaviorType.AUTOMATIC.toString());

                Set<String> importAliases = new LinkedHashSet<>();
                if (columnXml.isSetImportAliases())
                {
                    importAliases.addAll(Arrays.asList(columnXml.getImportAliases().getImportAliasArray()));
                    builder.setImportAliases(ColumnRenderProperties.convertToString(importAliases));
                }

                org.labkey.data.xml.PHIType.Enum phi = org.labkey.data.xml.PHIType.NOT_PHI;
                if (columnXml.isSetProtected())  // column is removed from LabKey but need to support old archives, see spec #28920
                {
                    if (columnXml.getProtected())
                        phi = org.labkey.data.xml.PHIType.LIMITED;  // always convert protected to limited PHI; may be overridden by getPhi(), though
                }
                if (columnXml.isSetPhi())
                    phi = columnXml.getPhi();

                builder.setPHI(phi.toString());
                builder.setExcludeFromShifting(columnXml.isSetExcludeFromShifting() && columnXml.getExcludeFromShifting());

                ColumnType.Fk fk = columnXml.getFk();
                if (fk != null)
                {
                    builder.setLookupContainer(fk.getFkFolderPath());
                    builder.setLookupSchema(fk.getFkDbSchema());
                    builder.setLookupQuery(fk.getFkTable());
                }

                if (columnXml.isSetScale())
                    builder.setScale(columnXml.getScale());
                builder.setLabel(columnXml.getColumnTitle());
                builder.setDescription(columnXml.getDescription());
                builder.setFormat(columnXml.getFormatString());
                builder.setInputType(columnXml.isSetInputType() ? columnXml.getInputType() : null);
                builder.setHidden(columnXml.getIsHidden());
                builder.setUrl(columnXml.getUrl());

                builder.setValidators(ValidatorKind.convertFromXML(columnXml.getValidators()));
                builder.setConditionalFormats(ConditionalFormat.convertFromXML(columnXml.getConditionalFormats()));

                if (columnXml.isSetDefaultValueType())
                    builder.setDefaultValueType(DefaultValueType.valueOf(columnXml.getDefaultValueType().toString()));

                if (columnXml.isSetDefaultValue())
                    builder.setDefaultValue(columnXml.getDefaultValue());

                builders.add(builder);
            }
        }
        return builders;
    }

    public ImportPropertyDescriptorsList getImportPropertyDescriptors(DomainURIFactory factory,
                                                                      Collection<String> errors,
                                                                      Container defaultContainer) throws Exception
    {
        return getImportPropertyDescriptors(createPropertyDescriptorBuilders(defaultContainer), factory, errors, defaultContainer);
    }

    public static ImportPropertyDescriptorsList getImportPropertyDescriptors(Collection<Builder> propertyDescriptorBuilders,
                                                                             DomainURIFactory factory,
                                                                             Collection<String> errors,
                                                                             Container defaultContainer)
    {
        OntologyManager.ImportPropertyDescriptorsList ret = new OntologyManager.ImportPropertyDescriptorsList();
        CaseInsensitiveHashSet mvColumns = new CaseInsensitiveHashSet();

        for (Builder builder : propertyDescriptorBuilders)
        {
            PropertyDescriptor pd = builder.build();

            String columnName = pd.getName();

            if (columnName.length() == 0)
            {
                String e = "'property' field is required";
                if (!errors.contains(e))
                    errors.add(e);
                continue;
            }

            if (StringUtils.endsWithIgnoreCase(columnName, MV_INDICATOR_SUFFIX))
            {
                mvColumns.add(columnName);
                continue;
            }

            String domainName = builder.getDomainName();
            Pair<String, Container> p = factory.getDomainURI(domainName);
            String domainURI = p.first;
            Container container = null != p.second ? p.second : defaultContainer;
            pd.setContainer(container);

            String propertyURI = StringUtils.trimToEmpty(pd.getPropertyURI());
            if (propertyURI.length() == 0)
            {
                pd.setPropertyURI(domainURI + "." + Lsid.encodePart(columnName));
            }

            // try use existing SystemProperty PropertyDescriptor from Shared container.
            if (!propertyURI.startsWith(domainURI) && !propertyURI.startsWith(ColumnInfo.DEFAULT_PROPERTY_URI_PREFIX))
            {
                PropertyDescriptor shared = OntologyManager.getPropertyDescriptor(propertyURI, ContainerManager.getSharedContainer());
                if (shared != null)
                    pd = shared;
            }

            ret.add(domainName,
                    domainURI,
                    pd,
                    builder.getValidators(),
                    builder.getConditionalFormats(),
                    builder.getDefaultValue());
        }

        if (!mvColumns.isEmpty())
        {
            // There really shouldn't be mvindicator columns in the map, so this is just being defensive
            // they should be implied by isMvEnabled() in the parent column
            CaseInsensitiveHashMap<PropertyDescriptor> nameMap = new CaseInsensitiveHashMap<>();
            for (OntologyManager.ImportPropertyDescriptor ipd : ret.properties)
                nameMap.put(ipd.pd.getName(), ipd.pd);
            for (String mv : mvColumns)
            {
                String data = mv.substring(0, mv.length() - MV_INDICATOR_SUFFIX.length());
                if (data.endsWith("_"))
                    data = data.substring(0, data.length() - 1);
                PropertyDescriptor pd = nameMap.get(data);
                if (null == pd)
                    errors.add("Missing value field does not have corresponding data field: " + mv);
                else
                    pd.setMvEnabled(true);
            }
        }
        return ret;
    }

    public static class Builder implements org.labkey.api.data.Builder<PropertyDescriptor>
    {
        private PropertyType _type;
        private Container _container;
        private String _propertyURI;
        private String _name;
        private String _label;
        private String _conceptURI;
        private String _rangeURI;
        private boolean _nullable;
        private boolean _required;
        private boolean _hidden;
        private boolean _mvEnabled;
        private String _description;
        private String _format;
        private StringExpression _url;
        private String _importAliases;
        private String _lookupContainer;
        private String _lookupSchema;
        private String _lookupQuery;
        private boolean _showInInsertView = true;
        private boolean _showInUpdateView = true;
        private boolean _showInDetailView = true;
        private boolean _dimension;
        private boolean _measure;
        private boolean _recommendedVariable;
        private DefaultScaleType _defaultScale = DefaultScaleType.LINEAR;
        private FacetingBehaviorType _facetingBehavior = FacetingBehaviorType.AUTOMATIC;
        private PHI _phi = PHI.NotPHI;
        private String _redactedText;
        private boolean _excludeFromShifting;
        private int _scale;
        private DefaultValueType _defaultValueType = null;

        // not part of PropertyDescriptors, this class could eventually become a builder for DomainProperty
        private String _domainName;
        private List<? extends IPropertyValidator> _validators;
        private List<ConditionalFormat> _formats;
        private String _defaultValue;

        public Builder(Container container, PropertyType type)
        {
            _container = container;
            _type = type;
            _rangeURI = type.getTypeUri();
            init();
        }

        public Builder(Container container, String conceptURI, String rangeURI) throws ValidationException
        {
            _container = container;
            _type = PropertyType.getFromURI(conceptURI, rangeURI, null);
            if (_type == null)
            {
                throw new ValidationException("Unrecognized type URI : " + ((null == conceptURI) ? rangeURI : conceptURI));
            }
            init();
        }

        private void init()
        {
            _scale = (_type.getJdbcType() == JdbcType.VARCHAR || _type.getJdbcType() == JdbcType.LONGVARCHAR)
                    ? PropertyStorageSpec.DEFAULT_SIZE
                    : _type.getScale();
        }

        @Override
        public PropertyDescriptor build()
        {
            PropertyDescriptor pd = new PropertyDescriptor();
            pd.setContainer(_container);
            pd.setPropertyURI(_propertyURI);
            pd.setName(_name);
            pd.setLabel(_label != null ? _label : _name);
            pd.setConceptURI(_conceptURI);
            pd.setRangeURI(_rangeURI);
            pd.setNullable(_nullable);
            pd.setRequired(_required);
            pd.setHidden(_hidden);
            pd.setMvEnabled(_mvEnabled);
            pd.setDescription(_description);
            pd.setFormat(_format);
            pd.setURL(_url);
            pd.setImportAliases(_importAliases);
            pd.setLookupContainer(_lookupContainer);
            pd.setLookupSchema(_lookupSchema);
            pd.setLookupQuery(_lookupQuery);
            pd.setShownInInsertView(_showInInsertView);
            pd.setShownInUpdateView(_showInUpdateView);
            pd.setShownInDetailsView(_showInDetailView);
            pd.setDimension(_dimension);
            pd.setMeasure(_measure);
            pd.setRecommendedVariable(_recommendedVariable);
            pd.setDefaultScale(_defaultScale);
            pd.setFacetingBehaviorType(_facetingBehavior);
            pd.setPHI(_phi);
            pd.setRedactedText(_redactedText);
            pd.setExcludeFromShifting(_excludeFromShifting);
            pd.setScale(_scale);
            pd.setDefaultValueTypeEnum(_defaultValueType);

            return pd;
        }

        public Builder setPropertyURI(String propertyURI)
        {
            _propertyURI = propertyURI;
            return this;
        }

        public Builder setName(String name)
        {
            _name = name;
            return this;
        }

        public Builder setLabel(String label)
        {
            _label = label;
            return this;
        }

        public Builder setConceptURI(String conceptURI)
        {
            _conceptURI = conceptURI;
            return this;
        }

        public Builder setNullable(boolean nullable)
        {
            _nullable = nullable;
            return this;
        }

        public Builder setRequired(boolean required)
        {
            _required = required;
            return this;
        }

        public Builder setHidden(boolean hidden)
        {
            _hidden = hidden;
            return this;
        }

        public Builder setMvEnabled(boolean mvEnabled)
        {
            _mvEnabled = mvEnabled;
            return this;
        }

        public Builder setDescription(String description)
        {
            _description = description;
            return this;
        }

        public Builder setInputType(String inputType)
        {
            if ("textarea".equals(inputType) && _type == PropertyType.STRING)
                _type = PropertyType.MULTI_LINE;

            return this;
        }

        public Builder setFormat(String format)
        {
            format = StringUtils.trimToNull(format);
            if (format != null)
            {
                try
                {
                    switch (_type)
                    {
                        case INTEGER:
                        case DOUBLE:
                            _format = convertNumberFormatChars(format);
                            (new DecimalFormat(_format)).format(1.0);
                            break;
                        case DATE_TIME:
                            _format = convertDateFormatChars(format);
                            (new SimpleDateFormat(_format)).format(new Date());
                            // UNDONE: don't import date format until we have default format for study
                            // UNDONE: it looks bad to have mixed formats
                            break;
                        case STRING:
                        case MULTI_LINE:
                        default:
                            _format = null;
                    }
                }
                catch (Exception x)
                {
                    _format = null;
                }
            }
            return this;
        }

        public Builder setUrl(String url)
        {
            if (url != null)
                _url = StringExpressionFactory.createURL(url);
            return this;
        }

        public Builder setUrl(StringExpressionType url)
        {
            if (url != null)
                _url = StringExpressionFactory.fromXML(url, true);
            return this;
        }

        public Builder setImportAliases(String importAliases)
        {
            _importAliases = importAliases;
            return this;
        }

        public Builder setLookupContainer(String lookupContainer)
        {
            if (lookupContainer != null)
            {
                Container c = ContainerManager.getForPath(lookupContainer);
                _lookupContainer = c != null ? c.getId() : null;
            }
            return this;
        }

        public Builder setLookupSchema(String lookupSchema)
        {
            _lookupSchema = lookupSchema;
            return this;
        }

        public Builder setLookupQuery(String lookupQuery)
        {
            _lookupQuery = lookupQuery;
            return this;
        }

        public Builder setShowInInsertView(boolean showInInsertView)
        {
            _showInInsertView = showInInsertView;
            return this;
        }

        public Builder setShowInUpdateView(boolean showInUpdateView)
        {
            _showInUpdateView = showInUpdateView;
            return this;
        }

        public Builder setShowInDetailView(boolean showInDetailView)
        {
            _showInDetailView = showInDetailView;
            return this;
        }

        public Builder setDimension(boolean dimension)
        {
            _dimension = dimension;
            return this;
        }

        public Builder setMeasure(boolean measure)
        {
            _measure = measure;
            return this;
        }

        public Builder setRecommendedVariable(boolean recommendedVariable)
        {
            _recommendedVariable = recommendedVariable;
            return this;
        }

        public Builder setDefaultScale(String defaultScale)
        {
            if (defaultScale != null)
            {
                DefaultScaleType type = DefaultScaleType.valueOf(defaultScale);
                if (type != null)
                    _defaultScale = type;
            }
            return this;
        }

        public Builder setFacetingBehavior(String facetingBehavior)
        {
            if (facetingBehavior != null)
            {
                FacetingBehaviorType type = FacetingBehaviorType.valueOf(facetingBehavior);
                if (type != null)
                    _facetingBehavior = type;
            }
            return this;
        }

        public Builder setPHI(String phi)
        {
            if (phi != null)
            {
                PHI type = PHI.valueOf(phi);
                if (type != null)
                    _phi = type;
            }
            return this;
        }

        public Builder setRedactedText(String redactedText)
        {
            _redactedText = redactedText;
            return this;
        }

        public Builder setExcludeFromShifting(boolean excludeFromShifting)
        {
            _excludeFromShifting = excludeFromShifting;
            return this;
        }

        public Builder setScale(int scale)
        {
            _scale = scale;
            return this;
        }

        public Builder setDefaultValueType(DefaultValueType defaultValueType)
        {
            _defaultValueType = defaultValueType;
            return this;
        }

        public String getDomainName()
        {
            return _domainName;
        }

        public void setDomainName(String domainName)
        {
            _domainName = domainName;
        }

        public List<? extends IPropertyValidator> getValidators()
        {
            return _validators;
        }

        public void setValidators(List<? extends IPropertyValidator> validators)
        {
            _validators = validators;
        }

        public List<ConditionalFormat> getConditionalFormats()
        {
            return _formats;
        }

        public void setConditionalFormats(List<ConditionalFormat> formats)
        {
            _formats = formats;
        }

        public String getDefaultValue()
        {
            return _defaultValue;
        }

        public void setDefaultValue(String defaultValue)
        {
            _defaultValue = defaultValue;
        }

        private String convertNumberFormatChars(String format)
        {
            int length = format.length();
            int decimal = format.indexOf('.');
            if (-1 == decimal)
                decimal = length;
            StringBuilder s = new StringBuilder(format);
            for (int i = 0; i < s.length(); i++)
            {
                if ('n' == s.charAt(i))
                    s.setCharAt(i, i < decimal - 1 ? '#' : '0');
            }
            return s.toString();
        }

        private String convertDateFormatChars(String format)
        {
            if (format.toUpperCase().equals(format))
                return format.replace('Y', 'y').replace('D', 'd');
            return format;
        }

        @Override
        public String toString()
        {
            return "Builder for " + _domainName + "." + _name;
        }
    }
}
