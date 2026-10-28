/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.core.metrics;

import org.labkey.api.usageMetrics.SimpleMetricsService;

/** Describes the generic client-side feature areas that @labkey/components counts; modules describe their own product-specific areas */
public final class ClientSideMetricDescriptions
{
    private ClientSideMetricDescriptions()
    {
    }

    public static void register()
    {
        describe("pagination", "Grid paging clicks: loadFirstPage, loadPreviousPage, loadNextPage, loadLastPage, setPageSize<size>, showTotalRowCount");
        describe("grid", "Grid row selection using shift-click (shiftSelect)");
        describe("customViews", "Grid columns reordered by drag and drop (columnReorderDragNDrop)");
        describe("export", "Grid exports by format and header type: csv-caption, excel-caption, tsv-caption, csv-importfield, excel-importfield, tsv-importfield");
        describe("QueryModel", "Grid loads recovered from a failure: ViewDoesNotExist (fell back to the default view), CalculatedFieldError (calculated fields omitted)");
        describe("applicationNavigation", "Product menu navigation: toHomeProject, toServerContainer, toServerContainerTab, and toLimsSection, toSampleManagerSection, toBiologicsSection, toApplicationSection");
        describe("gridSearch", "Grid search box use: engaged (first typing), suggestions chosen by kind (filterChosen, composeChosen, searchChosen), filter suggestions by source (exactValue, identity, identityContains, integerValue, key, partialMatch, partialValue, shape, shapeContains), and Name filters by data type (sampleNameChosen, dataClassNameChosen)");
        describe("gridInsertCounts", "Rows inserted from editable grids and assay imports, by data type and row-count bucket: <type>1To50, <type>51To100, <type>101To250, <type>GT250");
        describe("gridUpdateCounts", "Rows updated from editable grids, by data type and row-count bucket: <type>1To50, <type>51To100, <type>101To250, <type>GT250");
    }

    private static void describe(String featureArea, String description)
    {
        SimpleMetricsService.get().describeClientSideFeatureArea(featureArea, description);
    }
}
