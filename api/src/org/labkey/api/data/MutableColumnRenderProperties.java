package org.labkey.api.data;

import org.labkey.api.exp.PropertyType;
import org.labkey.api.gwt.client.DefaultScaleType;
import org.labkey.api.gwt.client.FacetingBehaviorType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.StringExpression;

import java.util.Set;

public interface MutableColumnRenderProperties extends ColumnRenderProperties, MutableColumnConceptProperties
{
    void setSortDirection(Sort.SortDirection sortDirection);

    void setInputType(String inputType);

    void setInputLength(int inputLength);

    void setInputRows(int inputRows);

    void setDisplayWidth(String displayWidth);

    void setFormat(String format);

    void setExcelFormatString(String excelFormatString);

    void setTsvFormatString(String tsvFormatString);

    void setTextExpression(StringExpression expr);

    void setLabel(String label);

    void setShortLabel(String shortLabel);

    void setDescription(String description);

    void setHidden(boolean hidden);

    void setShownInDetailsView(boolean shownInDetailsView);

    void setShownInInsertView(boolean shownInInsertView);

    void setShownInUpdateView(boolean shownInUpdateView);

    void setURL(StringExpression url);

    void setURLTargetWindow(String urlTargetWindow);

    void setURLCls(String urlCls);

    void setOnClick(String onClick);

    void setRecommendedVariable(boolean recommendedVariable);

    void setDefaultScale(DefaultScaleType defaultScale);

    void setMeasure(boolean measure);

    void setDimension(boolean dimension);

    void setNameExpression(String nameExpression);

    void setNullable(boolean nullable);

    void setRequired(boolean required);

    void setImportAliasesSet(Set<String> importAliases);

    void setPropertyType(PropertyType propertyType);

    void setFacetingBehaviorType(FacetingBehaviorType type);

    void setCrosstabColumnDimension(FieldKey crosstabColumnDimension);

    void setCrosstabColumnMember(CrosstabMember member);

    void setPHI(PHI phi);

    void setRedactedText(String redactedText);

    void setExcludeFromShifting(boolean isExcludeFromShifting);

    void setScale(int scale);

    void setPrecision(int scale);

    void setPrincipalConceptCode(String code);

    void setSourceOntology(String abbr);

    void setConceptSubtree(String path);

    void setConceptImportColumn(String name);

    void setConceptLabelColumn(String name);

    void setDerivationDataScope(String scope);
}
