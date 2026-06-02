/*
 * Copyright (c) 2020-2026 LabKey Corporation
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

    void setShownInLookupView(boolean shownInLookupView);

    void setURL(StringExpression url);

    void setURLTarget(String urlTarget);
    void setURLTargetWindow(String urlTarget);

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

    void setScannable(boolean scannable);

    void setPrecision(int scale);

    @Override
    void setPrincipalConceptCode(String code);

    @Override
    void setSourceOntology(String abbr);

    @Override
    void setConceptSubtree(String path);

    @Override
    void setConceptImportColumn(String name);

    @Override
    void setConceptLabelColumn(String name);

    void setDerivationDataScope(String scope);
}
