/*
 * Copyright (c) 2019 LabKey Corporation
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

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Sort.SortDirection;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.gwt.client.DefaultScaleType;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.FacetingBehaviorType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.StringExpression;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: matthewb
 * Date: Jul 21, 2008
 * Time: 12:00:28 PM
 *
 * These are fields used by ColumnInfo and PropertyDescriptor that primarily affect
 * how the field is rendered in the HTML grids, forms, and pickers
 */
public abstract class ColumnRenderPropertiesImpl implements MutableColumnRenderProperties
{
    public static final String STORAGE_UNIQUE_ID_CONCEPT_URI = "http://www.labkey.org/types#storageUniqueId";
    public static final String STORAGE_UNIQUE_ID_SEQUENCE_PREFIX = "org.labkey.api.StorageUniqueId";
    public static final String TEXT_CHOICE_CONCEPT_URI = "http://www.labkey.org/types#textChoice";

    protected SortDirection _sortDirection = SortDirection.ASC;
    protected String _inputType;
    protected int _inputLength = -1;
    protected int _inputRows = -1;
    protected String _displayWidth;
    protected String _format;
    protected String _excelFormatString;
    protected String _tsvFormatString;
    protected StringExpression _textExpression;
    protected int _scale = 0;
    protected int _precision = 0;
    protected String _propertyURI;
    protected String _conceptURI;
    protected String _rangeURI;
    protected PropertyType _propertyType;
    protected boolean _scannable = false;

    // property descriptors default to nullable, while columninfos do not; PropertyDescriptor overrides this initializer
    // in its constructor:
    protected boolean _nullable = false;
    protected boolean _required = false;
    protected String _label;
    /** The column's label, without any prefixes from parent lookups */
    protected String _shortLabel;
    protected String _description;
    protected boolean _hidden;
    protected String _nameExpression;
    protected Boolean _measure;
    protected Boolean _dimension;
    protected Boolean _recommendedVariable = false;
    protected DefaultScaleType _defaultScale = DefaultScaleType.LINEAR;
    protected boolean _shownInInsertView = true;
    protected boolean _shownInUpdateView = true;
    protected boolean _shownInDetailsView = true;
    protected StringExpression _url;
    protected String _urlTargetWindow;
    protected String _urlCls;
    protected String _onClick;
    // methods use Set<>, but I'm using a List<> here because it is simpler and more thread safe and explicitly preserves order
    protected List<String> _importAliases = List.of();
    protected DefaultValueType _defaultValueType = null;
    protected FacetingBehaviorType _facetingBehaviorType = FacetingBehaviorType.AUTOMATIC;
    protected PHI _phi = PHI.NotPHI;
    protected String _redactedText = null;
    protected Boolean _isExcludeFromShifting = false;
    protected FieldKey _crosstabColumnDimension;
    protected CrosstabMember _crosstabColumnMember;

    // OntologyService related annotations
    protected String _sourceOntology = null;
    protected String _conceptSubtree = null;
    protected String _conceptImportColumn = null;
    protected String _conceptLabelColumn = null;
    protected String _principalConceptCode = null;

    // used by exp material to distinguish aliquot vs meta fields
    protected String _derivationDataScope = null;

    abstract public void checkLocked();
    private boolean _checkLocked()
    {
        checkLocked();
        return true;
    }

    /*
     * ColumnRenderProperties live in caches so its Collection type members need to be immutable.
     */
    protected static <T> List<T> copyFixedList(Collection<T> src)
    {
        if (null == src)
            return null;
        if (src.isEmpty())
            return List.of();
        // unmodifiable arraylist does not need to be synchronized
        return List.copyOf(src);
    }

    @Override
    // TODO MutableColumnRenderProperties
    public void copyTo(ColumnRenderPropertiesImpl to)
    {
        assert to._checkLocked();
        to._sortDirection = _sortDirection;
        to.setInputType(getInputType());
        to.setInputLength(getInputLength());
        to.setInputRows(getInputRows());
        to._nullable = _nullable;
        to._required = _required;
        to._displayWidth = _displayWidth;
        to._format = _format;
        to._excelFormatString = _excelFormatString;
        to._tsvFormatString = _tsvFormatString;
        to._textExpression = _textExpression;
        to._label = _label;
        to._shortLabel = _shortLabel;
        to._description = _description;
        to._hidden = _hidden;
        to._shownInInsertView = _shownInInsertView;
        to._shownInUpdateView = _shownInUpdateView;
        to._shownInDetailsView = _shownInDetailsView;
        to._measure = _measure;
        to._dimension = _dimension;
        to._recommendedVariable = _recommendedVariable;
        to._defaultScale = _defaultScale;
        to._url = _url;
        to._importAliases = copyFixedList(_importAliases);
        to._facetingBehaviorType = _facetingBehaviorType;
        to._crosstabColumnMember = _crosstabColumnMember;
        to._phi = _phi;
        to._redactedText = _redactedText;
        to._isExcludeFromShifting = _isExcludeFromShifting;
        to._scale = _scale;
        to._precision = _precision;
        to._propertyURI = _propertyURI;
        to._conceptURI = _conceptURI;
        to._rangeURI = _rangeURI;
        to._propertyType = _propertyType;
        to._defaultValueType = _defaultValueType;
        to._principalConceptCode = _principalConceptCode;
        to._sourceOntology = _sourceOntology;
        to._conceptSubtree = _conceptSubtree;
        to._conceptImportColumn = _conceptImportColumn;
        to._conceptLabelColumn = _conceptLabelColumn;
        to._derivationDataScope = _derivationDataScope;
        to._scannable = _scannable;
    }

    @Override
    public String getNonBlankCaption()
    {
        if (_label == null || "".equals(_label.trim()))
        {
            return getName();
        }
        return _label;
    }

    @Override
    public SortDirection getSortDirection()
    {
        return _sortDirection;
    }

    @Override
    public void setSortDirection(SortDirection sortDirection)
    {
        assert _checkLocked();
        _sortDirection = sortDirection;
    }

    @Override
    public String getInputType()
    {
        return _inputType;
    }

    @Override
    public void setInputType(String inputType)
    {
        assert _checkLocked();
        _inputType = inputType;
    }

    @Override
    public int getInputLength()
    {
        return _inputLength;
    }

    @Override
    public void setInputLength(int inputLength)
    {
        assert _checkLocked();
        _inputLength = inputLength;
    }

    @Override
    public int getInputRows()
    {
        return _inputRows;
    }

    @Override
    public void setInputRows(int inputRows)
    {
        assert _checkLocked();
        _inputRows = inputRows;
    }

    @Override
    public String getDisplayWidth()
    {
        return _displayWidth;
    }

    @Override
    public void setDisplayWidth(String displayWidth)
    {
        assert _checkLocked();
        _displayWidth = displayWidth;
    }

    @Override
    public String getFormat()
    {
        return _format;
    }

    @Override
    public void setFormat(String format)
    {
        assert _checkLocked();
        _format = format;
    }

    @Override
    public String getExcelFormatString()
    {
        return _excelFormatString;
    }

    @Override
    public void setExcelFormatString(String excelFormatString)
    {
        assert _checkLocked();
        _excelFormatString = excelFormatString;
    }

    @Override
    public String getTsvFormatString()
    {
        return _tsvFormatString;
    }

    @Override
    public void setTsvFormatString(String tsvFormatString)
    {
        assert _checkLocked();
        _tsvFormatString = tsvFormatString;
    }

    @Override
    public StringExpression getTextExpression()
    {
        return _textExpression;
    }

    @Override
    public void setTextExpression(StringExpression expr)
    {
        assert _checkLocked();
        _textExpression = expr;
    }

    @Override
    public String getLabel()
    {
        return _label;
    }

    @Override
    public void setLabel(String label)
    {
        _label = label;
    }

    @Override
    public String getShortLabel()
    {
        return _shortLabel == null ? getLabel() : _shortLabel;
    }

    @Override
    public void setShortLabel(String shortLabel)
    {
        assert _checkLocked();
        _shortLabel = shortLabel;
    }

    @Override
    public String getDescription()
    {
        return _description;
    }

    @Override
    public void setDescription(String description)
    {
        assert _checkLocked();
        _description = description;
    }

    @Override
    public boolean isHidden()
    {
        return _hidden;
    }

    @Override
    public void setHidden(boolean hidden)
    {
        assert _checkLocked();
        _hidden = hidden;
    }

    @Override
    public boolean isShownInDetailsView()
    {
        return _shownInDetailsView;
    }

    @Override
    public void setShownInDetailsView(boolean shownInDetailsView)
    {
        assert _checkLocked();
        _shownInDetailsView = shownInDetailsView;
    }

    @Override
    public boolean isShownInInsertView()
    {
        return _shownInInsertView;
    }

    @Override
    public void setShownInInsertView(boolean shownInInsertView)
    {
        assert _checkLocked();
        _shownInInsertView = shownInInsertView;
    }

    @Override
    public boolean isShownInUpdateView()
    {
        return _shownInUpdateView;
    }

    @Override
    public void setShownInUpdateView(boolean shownInUpdateView)
    {
        assert _checkLocked();
        _shownInUpdateView = shownInUpdateView;
    }

    @Override
    public StringExpression getURL()
    {
        return _url;
    }

    @Override
    public void setURL(StringExpression url)
    {
        assert _checkLocked();
        _url = url;
    }

    @Override
    public String getURLTargetWindow()
    {
        return _urlTargetWindow;
    }

    @Override
    public void setURLTargetWindow(String urlTargetWindow)
    {
        assert _checkLocked();
        _urlTargetWindow = urlTargetWindow;
    }

    @Override
    public String getURLCls()
    {
        return _urlCls;
    }

    @Override
    public void setURLCls(String urlCls)
    {
        assert _checkLocked();
        _urlCls = urlCls;
    }

    @Override
    public String getOnClick()
    {
        return _onClick;
    }

    @Override
    public void setOnClick(String onClick)
    {
        assert _checkLocked();
        _onClick = onClick;
    }

    @Override
    public boolean isRecommendedVariable()
    {
        return _recommendedVariable;
    }

    @Override
    public void setRecommendedVariable(boolean recommendedVariable)
    {
        assert _checkLocked();
        _recommendedVariable = recommendedVariable;
    }

    @Override
    public DefaultScaleType getDefaultScale()
    {
        return _defaultScale;
    }

    @Override
    public void setDefaultScale(DefaultScaleType defaultScale)
    {
        assert _checkLocked();
        _defaultScale = defaultScale;
    }

    @Override
    public void setMeasure(boolean measure)
    {
        assert _checkLocked();
        _measure = measure;
    }

    @Override
    public void setDimension(boolean dimension)
    {
        assert _checkLocked();
        _dimension = dimension;
    }

    public static boolean inferIsDimension(ColumnRenderProperties col)
    {
        return inferIsDimension(col.getName(), col.isLookup(), col.isHidden());
    }

    public static boolean inferIsDimension(String name, boolean isLookup, boolean isHidden)
    {
        return isLookup &&
                !isHidden &&
                !"CreatedBy".equalsIgnoreCase(name) &&
                !"ModifiedBy".equalsIgnoreCase(name);
    }

    @Override
    public boolean isDimension()
    {
        // If dimension is unspecified/null, make a best guess based on the type of the field:
        if (_dimension == null)
            return inferIsDimension(getName(), isLookup(), isHidden());
        else
            return _dimension;
    }

    public static boolean inferIsMeasure(ColumnRenderProperties col)
    {
        return inferIsMeasure(col.getName(),
                col.getLabel(),
                col.isNumericType(),
                col.isAutoIncrement(),
                col.isLookup(),
                col.isHidden());
    }

    @Override
    public boolean isUniqueIdField()
    {
        return STORAGE_UNIQUE_ID_CONCEPT_URI.equals(getConceptURI());
    }

    /**
     * Check indicating if field is scannable: isUniqueId field OR isScannable
     */
    @Override
    public boolean isScannableField()
    {
        return isUniqueIdField() || isScannable();
    }

    public static boolean inferIsMeasure(String name, String label, boolean isNumeric, boolean isAutoIncrement, boolean isLookup, boolean isHidden)
    {
        if (label != null)
        {
            String[] parts = label.toLowerCase().split("[ _]");
            for (String part : parts)
            {
                if (part.equals("code") || part.equals("id") || part.equals("identifier") || part.equals("datafax"))
                    return false;
            }
        }
        return isNumeric &&
                !isAutoIncrement &&
                !isLookup &&
                !isHidden
                && !"ParticipantID".equalsIgnoreCase(name)
                && !"VisitID".equalsIgnoreCase(name)
                && !"SequenceNum".equalsIgnoreCase(name)
                && !"RowId".equalsIgnoreCase(name)
                && !"ObjectId".equalsIgnoreCase(name);
    }

    @Override
    public boolean isMeasure()
    {
        // If measure is unspecified/null, make a best guess based on the type of the field:
        if (_measure == null)
            return inferIsMeasure(getName(), getLabel(), isNumericType(), isAutoIncrement(), isLookup(), isHidden());
        else
            return _measure;
    }

    public String getNameExpression()
    {
        return _nameExpression;
    }

    public void setNameExpression(String nameExpression)
    {
        assert _checkLocked();
        _nameExpression = nameExpression;
    }

    /** value must not be null/empty */
    @Override
    public boolean isNullable()
    {
        return _nullable;
    }

    @Override
    public void setNullable(boolean nullable)
    {
        assert _checkLocked();
        _nullable = nullable;
    }

    /** value must not be null/empty OR a missing value indicator must be provided */
    @Override
    public boolean isRequired()
    {
        // !nullable is stricter and implies required
        return !_nullable || _required;
    }

    /** Returns the 'raw' value of required which is useful for copying attributes.  see isRequired() */
    @Override
    public boolean isRequiredSet()
    {
        return _required;
    }

    @Override
    public void setRequired(boolean required)
    {
        assert _checkLocked();
        _required = required;
    }

    @Override
    @NotNull
    public Set<String> getImportAliasSet()
    {
        if (_importAliases.isEmpty())
            return Set.of();
        return Collections.unmodifiableSet(new LinkedHashSet<>(_importAliases));
    }

    @NotNull
    @JsonGetter("_importAliases")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @SuppressWarnings("unused")
    public Set<String> getImportAliasSet_for_jackson_do_not_use()
    {
        // Jackson does not like immutable sets
        return new HashSet<>(_importAliases);
    }

    @Override
    @JsonSetter("_importAliases")
    public void setImportAliasesSet(Set<String> importAliases)
    {
        assert _checkLocked();
        assert importAliases != null;
        _importAliases = copyFixedList(importAliases);
    }

    public static String convertToString(Set<String> set)
    {
        if (set.isEmpty())
        {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        String separator = "";
        for (String alias : set)
        {
            sb.append(separator);
            separator = ", ";
            alias = alias.trim();
            if (alias.contains(" "))
            {
                // Quote any values with spaces
                sb.append("\"");
                sb.append(alias);
                sb.append("\"");
            }
            else
            {
                sb.append(alias);
            }
        }
        return sb.toString();
    }

    @Override
    @Nullable
    public PropertyType getPropertyType()
    {
        if (_propertyType == null && getRangeURI() != null)
            _propertyType = PropertyType.getFromURI(getConceptURI(), getRangeURI(), null);

        return _propertyType;
    }

    @Override
    public void setPropertyType(PropertyType propertyType)
    {
        assert _checkLocked();
        _propertyType = propertyType;
    }

    @Override
    public String getPropertyURI()
    {
        return _propertyURI;
    }

    @Override
    public String getConceptURI()
    {
        return _conceptURI;
    }

    @Override
    public String getRangeURI()
    {
        return _rangeURI;
    }

    private static final Pattern STRING_PATTERN = Pattern.compile("[^,; \\t\\n\\f\"]+|\"[^\"]*\"");

    public static Set<String> convertToSet(String s)
    {
        Set<String> result = new LinkedHashSet<>();
        if (s != null)
        {
            Matcher m = STRING_PATTERN.matcher(s);
            while (m.find())
            {
                String alias = m.group();
                if (alias.startsWith("\"") && alias.endsWith("\""))
                {
                    // Strip off the leading and trailing quotes
                    alias = alias.substring(1, alias.length() - 1);
                }
                result.add(alias);
            }
        }
        return result;
    }


    @Override
    public boolean isDateTimeType()
    {
        JdbcType type = getJdbcType();
        return type==JdbcType.DATE || type==JdbcType.TIME || type==JdbcType.TIMESTAMP;
    }

    @Override
    public boolean isStringType()
    {
        JdbcType type = getJdbcType();
        return type.cls == String.class;
    }

    @Override
    public boolean isLongTextType()
    {
        JdbcType type = getJdbcType();
        return type == JdbcType.LONGVARCHAR;
    }

    @Override
    public boolean isBooleanType()
    {
        return getJdbcType() == JdbcType.BOOLEAN;
    }

    @Override
    public boolean isNumericType()
    {
        return getJdbcType().isNumeric();
    }

    /** Don't return TYPEs just real java objects */
    @Override
    public final Class getJavaObjectClass()
    {
        return getJavaClass(true);
    }

    /** Return Class or TYPE, based on isNullable */
    @Override
    public final Class getJavaClass()
    {
        return getJavaClass(isNullable());
    }

    @Override
    public Class getJavaClass(boolean isNullable)
    {
        PropertyType pt = getPropertyType();
        if (pt != null)
            return pt.getJavaType();

        return getJdbcType().getJavaClass(isNullable);
    }

    @Override
    public void setFacetingBehaviorType(FacetingBehaviorType type)
    {
        assert _checkLocked();
        _facetingBehaviorType = type;
    }

    @Override
    public FacetingBehaviorType getFacetingBehaviorType()
    {
        return _facetingBehaviorType;
    }

    @Override
    public FieldKey getCrosstabColumnDimension()
    {
        return _crosstabColumnDimension;
    }

    @Override
    public void setCrosstabColumnDimension(FieldKey crosstabColumnDimension)
    {
        assert _checkLocked();
        _crosstabColumnDimension = crosstabColumnDimension;
    }

    @Override
    public CrosstabMember getCrosstabColumnMember()
    {
        return _crosstabColumnMember;
    }

    @Override
    public void setCrosstabColumnMember(CrosstabMember member)
    {
        assert _checkLocked();
        _crosstabColumnMember = member;
    }

    @Override
    public void setPHI(PHI phi)
    {
        assert _checkLocked();
        _phi = phi;
    }

    @Override
    public PHI getPHI()
    {
        return _phi;
    }

    @Override
    public String getRedactedText()
    {
        return _redactedText;
    }

    @Override
    public void setRedactedText(String redactedText)
    {
        assert _checkLocked();
        _redactedText = redactedText;
    }

    @Override
    public boolean isExcludeFromShifting()
    {
        return _isExcludeFromShifting;
    }

    @Override
    public void setExcludeFromShifting(boolean isExcludeFromShifting)
    {
        assert _checkLocked();
        _isExcludeFromShifting = isExcludeFromShifting;
    }

    @Override
    public int getScale()
    {
        return _scale;
    }

    @Override
    public void setScale(int scale)
    {
        assert _checkLocked();
        _scale = scale;
    }

    @Override
    public boolean isScannable()
    {
        return _scannable;
    }

    @Override
    public void setScannable(boolean scannable)
    {
        _scannable = scannable;
    }

    @Override
    public int getPrecision()
    {
        return _precision;
    }

    @Override
    public void setPrecision(int precision)
    {
        assert _checkLocked();
        _precision = precision;
    }

    @Override
    public String getDerivationDataScope()
    {
        return _derivationDataScope;
    }

    @Override
    public void setDerivationDataScope(String scope)
    {
        assert _checkLocked();
        _derivationDataScope = scope;
    }


    /* Ontology */
    @Override
    public String getPrincipalConceptCode()
    {
        return _principalConceptCode;
    }

    @Override
    public void setPrincipalConceptCode(String code)
    {
        assert _checkLocked();
        _principalConceptCode = code;
    }

    @Override
    public String getSourceOntology()
    {
        return _sourceOntology;
    }

    @Override
    public void setSourceOntology(String sourceOntology)
    {
        assert _checkLocked();
        _sourceOntology = sourceOntology;
    }

    @Override
    public void setConceptSubtree(String path)
    {
        assert _checkLocked();
        _conceptSubtree = path;
    }

    @Override
    public String getConceptSubtree()
    {
        return _conceptSubtree;
    }

    @Override
    public String getConceptImportColumn()
    {
        return _conceptImportColumn;
    }

    @Override
    public void setConceptImportColumn(String conceptImportColumn)
    {
        assert _checkLocked();
        _conceptImportColumn = conceptImportColumn;
    }

    @Override
    public String getConceptLabelColumn()
    {
        return _conceptLabelColumn;
    }

    @Override
    public void setConceptLabelColumn(String conceptLabelColumn)
    {
        assert _checkLocked();
        _conceptLabelColumn = conceptLabelColumn;
    }
    /* /Ontology */
}
