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

/** Describes the client-side feature areas that @labkey/components counts */
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
        describe("BarTender", "BarTender label template downloads (DownloadTemplateCount)");
        describe("QueryModel", "Grid loads recovered from a failure: ViewDoesNotExist (fell back to the default view), CalculatedFieldError (calculated fields omitted)");
        describe("expressionAssistant", "Calculated field expression assistant use: clickButton, submitPrompt, applyExpression, clickHelpWithValidate");
        describe("applicationNavigation", "Product menu navigation: toHomeProject, toServerContainer, toServerContainerTab, and toLimsSection, toSampleManagerSection, toBiologicsSection, toApplicationSection");
        describe("releaseNotes", "Release notes opened, by entry point: FromHelpMenu, FromBanner");
        describe("helpLink", "Help link clicks from the user menu (clickCount)");
        describe("SampleManagerSearch", "Sample Manager searches (count) and result clicks (resultClicked, plus firstResultClicked for the top result)");
        describe("BiologicsSearch", "Biologics searches (count) and result clicks (resultClicked, plus firstResultClicked for the top result)");
        describe("LIMSSearch", "LIMS searches (count) and result clicks (resultClicked, plus firstResultClicked for the top result)");
        describe("gridInsertCounts", "Rows inserted from editable grids and assay imports, by data type and row-count bucket: <type>1To50, <type>51To100, <type>101To250, <type>GT250");
        describe("gridUpdateCounts", "Rows updated from editable grids, by data type and row-count bucket: <type>1To50, <type>51To100, <type>101To250, <type>GT250");
        describe("findSamplesById", "Find Samples by ID or barcode: searches submitted (findCount), plus actions taken from its results grid (e.g. addSamplesToPicklist, sampleFinderFindDerivatives)");
        describe("sampleFinder", "Sample Finder use: headerMenuNavigation, dashboardButtonNavigation, filterModalApply, filterModalError, goToChooseValuesTab, hasWithoutAssayResultChecked, assay design counts (with <n> AssayDesgin(s)), plus actions taken from its results grid (e.g. addSamplesToPicklist)");
    }

    private static void describe(String featureArea, String description)
    {
        SimpleMetricsService.get().describeClientSideFeatureArea(featureArea, description);
    }
}
