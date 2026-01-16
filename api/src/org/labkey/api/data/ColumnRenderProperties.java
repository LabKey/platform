/*
 * Copyright (c) 2009-2019 LabKey Corporation
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

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.gwt.client.DefaultScaleType;
import org.labkey.api.gwt.client.FacetingBehaviorType;
import org.labkey.api.ontology.KindOfQuantity;
import org.labkey.api.ontology.OntologyService;
import org.labkey.api.ontology.Quantity;
import org.labkey.api.ontology.Unit;
import org.labkey.api.query.FieldKey;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.OptionalFeatureService;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.logging.LogHelper;

import java.io.File;
import java.math.BigDecimal;
import java.text.DecimalFormatSymbols;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.labkey.api.ontology.OntologyService.conceptCodeConceptURI;

public interface ColumnRenderProperties extends ImportAliasable, SimpleConvert
{
    void copyTo(ColumnRenderPropertiesImpl to);

    String getNonBlankCaption();

    Sort.SortDirection getSortDirection();

    String getInputType();

    int getInputLength();

    int getInputRows();

    String getDisplayWidth();

    String getFormat();

    String getExcelFormatString();

    String getTsvFormatString();

    StringExpression getTextExpression();

    @Override
    String getLabel();

    String getShortLabel();

    String getDescription();

    boolean isHidden();

    boolean isShownInDetailsView();

    boolean isShownInInsertView();

    boolean isShownInUpdateView();

    boolean isShownInLookupView();

    StringExpression getURL();

    String getURLTarget();

    String getURLCls();

    String getOnClick();

    boolean isRecommendedVariable();

    DefaultScaleType getDefaultScale();

    String getNameExpression();

    boolean isDimension();

    boolean isMeasure();

    /** value must not be null/empty */
    boolean isNullable();

    /** value must not be null/empty OR a missing value indicator must be provided */
    boolean isRequired();

    /** Returns the 'raw' value of required which is useful for copying attributes.  see isRequired() */
    boolean isRequiredSet();

    @Override
    @NotNull Set<String> getImportAliasSet();

    @Nullable PropertyType getPropertyType();

    @Override
    String getPropertyURI();

    String getConceptURI();

    String getRangeURI();

    @NotNull JdbcType getJdbcType();

    boolean isLookup();

    boolean isAutoIncrement();

    boolean isDateTimeType();

    boolean isStringType();

    boolean isLongTextType();

    boolean isBooleanType();

    boolean isNumericType();

    boolean isUniqueIdField();

    /**
     * Check indicating if field is UniqueId or is marked as Scannable
     */
    boolean isScannableField();

    default String getFriendlyTypeName()
    {
        return getFriendlyTypeName(getJavaClass());
    }

    static String getFriendlyTypeName(@NotNull Class<?> javaClass)
    {
        if (javaClass.equals(String.class))
            return "Text (String)";
        else if (javaClass.equals(Integer.class) || javaClass.equals(Integer.TYPE) || javaClass.equals(Short.class) || javaClass.equals(Short.TYPE))
            return "Integer";
        else if (javaClass.equals(Double.class) || javaClass.equals(Double.TYPE) || javaClass.equals(BigDecimal.class))
            return "Number (Double)";
        else if (javaClass.equals(Float.class) || javaClass.equals(Float.TYPE))
            return "Number (Float)";
        else if (javaClass.equals(Boolean.class) || javaClass.equals(Boolean.TYPE))
            return "True/False (Boolean)";
        else if (javaClass.equals(Long.class) || javaClass.equals(Long.TYPE))
            return "Long Integer";
        else if (javaClass.equals(File.class))
            return "File";
        else if (java.sql.Date.class.isAssignableFrom(javaClass))
            return "Date";
        else if (Date.class.isAssignableFrom(javaClass))
            return "Date and Time";
        else if (List.class.isAssignableFrom(javaClass) || javaClass.isArray())
            return "Array";
        return "Other";
    }

    /** Don't return TYPEs just real java objects */
    Class<?> getJavaObjectClass();

    /** Return Class or TYPE, based on isNullable */
    Class<?> getJavaClass();

    Class<?> getJavaClass(boolean isNullable);

    FacetingBehaviorType getFacetingBehaviorType();

    FieldKey getCrosstabColumnDimension();

    CrosstabMember getCrosstabColumnMember();

    PHI getPHI();

    String getRedactedText();

    boolean isExcludeFromShifting();

    /**
    * For decimal values, scale is the number of digits to the right of the decimal. For all other types, scale is the
    * total number of characters or digits
    * */
    int getScale();

    /**
     * Only used for decimal values. This is the total number of digits in the value
     */
    int getPrecision();

    /**
     * Field value can be used as a barcode, similar to UniqueId type -- w/o requirement of being unique.
     */
    boolean isScannable();

    /* Properties loaded by OntologyService */

    // any column can be annotated with PrincipalConceptCode
    default String getPrincipalConceptCode()
    {
        return null;
    }

    // For concept lookup columns only we have SourceOntology, ConceptSubTree (must be in SourceOntology),
    // also to support import features we have ImportColumn and LabelColumn
    default boolean isConceptColumn()
    {
        return getJdbcType().isText() && conceptCodeConceptURI.equals(getConceptURI()) && null != OntologyService.get();
    }

    default String getSourceOntology()
    {
        return null;
    }

    /**
     * This is a specification of a subtree in which we expect to find the concept code in this column.
     * From an implementation point of view we need a path from the ontology.hierarchy table.  From the user's
     * point of view we only need a concept (assuming hierarchy table is complete and consistent WRT subclass
     * relationship between concepts).
     * <br>
     * This should be set to an unambiguous path of conceptid (e.g. /NCI:concept1/NCI:concept2).  However, we will
     * accept a simple conceptid and map to a path and hope the hierarchy tree is consistent e.g. {set of concepts
     * under (path1)/CONCEPT/} == {set of concepts under (alternate path2)/CONCEPT/}
     * <br>
     * Use OntologyManager.resolveSubtreePath(crp.getConceptSubtree()) to get ontology.hierarchy.path.
     */
    default String getConceptSubtree()
    {
        return null;
    }

    default String getConceptImportColumn()
    {
        return null;
    }

    default String getConceptLabelColumn()
    {
        return null;
    }

    default KindOfQuantity getKindOfQuantity()
    {
        var unit = getDisplayUnit();
        if (null == unit)
            return null;
        return unit.getKindOfQuantity();
    }

    default Unit getDisplayUnit()
    {
        if (OptionalFeatureService.get().isFeatureEnabled(AppProps.QUANTITY_COLUMN_SUFFIX_TESTING))
        {
            if (!getJdbcType().isNumeric())
                return null;
            String name = getName();
            var index = name.lastIndexOf("__");
            if (index < 0)
                return null;
            var unitPart = name.substring(index + 2);
            try
            {
                return Unit.valueOf(unitPart);
            }
            catch (IllegalArgumentException x)
            {
                // pass
            }
        }
        return null;
    }


    /* End properties loaded by OntologyService */


    default String getDerivationDataScope()
    {
        return null;
    }


    /**
     * This Format can be used for low-level conversion of the type represented by this column.  It does handle
     * basic numeric/date conversion including formats, and default display unit handling.  That's about it.
     * It never produces HTML
     * It does not handle compound/column formatting (missing values, oor, etc)
     * It does not handle conditional formatting
     * This method moves (most) of the work formerly done in DisplayColumn.formatValue() to a shared location (getFormat())
     * Likewise for SimpleConvertColumn.simpleConvert() (getConvert())
     */
    @Transient
    default Function<Object, String> getFormatFn()
    {
        return getDefaultFormatFn(getName(), getJavaObjectClass(), getDisplayUnit(), getFormat(), null);
    }

    @Transient
    default Function<Object, String> getTsvFormatFn()
    {
        return getDefaultFormatFn(getName(), getJavaObjectClass(), getDisplayUnit(), getTsvFormatString(), DisplayColumn.tsvFormatSymbols);
    }

    static Function<Object, String> getDefaultFormatFn(String colName, Class<?> javaObjectClass, final Unit displayUnit, String formatString, DecimalFormatSymbols dfs)
    {
        final var format = null==formatString ? null : DisplayColumn.createFormat(formatString, javaObjectClass, dfs);

        if (null == format && null == displayUnit)
        {
            return (value) -> null==value ? "" : value instanceof String ? (String)value : ConvertUtils.convert(value);
        }

        return (value) ->
        {
            if (null == value)
                return "";

            @NotNull String formattedString;
            if (null != displayUnit && value instanceof Number)
            {
                Quantity q = (value instanceof Quantity) ?
                        (Quantity) value :
                        displayUnit.getKindOfQuantity().toQuantity((Number) value);
                var doubleValue = q.doubleValue(displayUnit);
                if (null == format)
                    formattedString = ConvertUtils.convert(doubleValue);
                else
                    formattedString = format.format(doubleValue);
            }
            else if (null != format)
            {
                try
                {
                    formattedString = format.format(value);
                }
                catch (IllegalArgumentException e)
                {
                    LogHelper.getLogger(ColumnRenderProperties.class, "Column metadata").warn("Unable to apply format to {} value \"{}\" for column \"{}\", likely a SQL type mismatch between XML metadata and actual ResultSet", value.getClass().getName(), value, colName);
                    formattedString = ConvertUtils.convert(value);
                }
            }
            else if (value instanceof String)
            {
                formattedString = (String) value;
            }
            else
            {
                formattedString = ConvertUtils.convert(value);
            }

            return formattedString;
        };
    }

    /**
     * The returned Function&lt;Object,Object> should throw ConversionException (undeclared RuntimeException).
     * This method does not handle compound conversions e.g. MissingValues or Out-of-range indicators.
     */
    @Override @Transient
    SimpleConvert getConvertFn();

    /** see getConvertFn() */
    @Override
    default Object convert(Object o) throws ConversionException
    {
        return getConvertFn().convert(o);
    }

    static SimpleConvert getDefaultConvertFn(ColumnRenderProperties col)
    {
        final Class<?> javaClass = col.getJavaObjectClass();
        final var defaultUnit = col.getDisplayUnit();
        final @NotNull var jdbcType = col.getJdbcType();

        if (null != defaultUnit)
            return defaultUnit::convert;

        if (null != col.getPropertyType())
            return col.getPropertyType();

        return col.getJdbcType();
    }
}
