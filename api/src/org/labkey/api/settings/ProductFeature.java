/*
 * Copyright (c) 2022-2026 LabKey Corporation
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
package org.labkey.api.settings;

/**
 * A listing of functionality that is available in the non-starter tiers of our applications
 */
public enum ProductFeature
{
    AdvancedWorkflow("sampleManagement"),
    ApiKeys("core"),
    Assay("assay"),
    AssayQC("premium"),
    BiologicsRegistry("biologics"),
    CalculatedFields("core"),
    ChartBuilding("core"),
    ConditionalFormatting("core"),
    CustomImportTemplates("core"),
    DataChangeCommentRequirement("core"),
    ELN("labbook"),
    FreezerManagement("inventory"),
    Media("recipe"),
    NonstandardAssay("nonstandardAssay"),
    Folders("sampleManagement"),
    SampleManagement("sampleManagement"),
    SchemaBrowser("sampleManagement"),
    TransformScripts("core"),
    Workflow("sampleManagement");

    private final String _module;

    ProductFeature(String module)
    {
        _module = module;
    }

    public String getModule()
    {
        return _module;
    }
}
