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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.gwt.client.DefaultScaleType;
import org.labkey.api.gwt.client.FacetingBehaviorType;
import org.labkey.api.ontology.OntologyService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.StringExpression;

import java.io.File;
import java.math.BigDecimal;
import java.util.Date;
import java.util.Set;

import static org.labkey.api.ontology.OntologyService.conceptCodeConceptURI;

public interface ColumnRenderProperties extends ImportAliasable
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

    StringExpression getURL();

    String getURLTargetWindow();

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

    static String getFriendlyTypeName(@NotNull Class javaClass)
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
        else
            return "Other";
    }

    /** Don't return TYPEs just real java objects */
    Class getJavaObjectClass();

    /** Return Class or TYPE, based on isNullable */
    Class getJavaClass();

    Class getJavaClass(boolean isNullable);

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
     *
     * This should be set to an unambiguous path of conceptid (e.g. /NCI:concept1/NCI:concept2).  However, we will
     * accept a simple conceptid and map to a path and hope the hierarchy tree is consistent e.g. {set of concepts
     * under (path1)/CONCEPT/} == {set of concepts under (alternate path2)/CONCEPT/}
     *
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
    /* End properties loaded by OntologyService */


    default String getDerivationDataScope()
    {
        return null;
    }
}
