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
import {List, Record} from "immutable";
import {DomainDesign} from "@glass/domainproperties";

// TODO remove as this is now in @glass/base
export class AssayProtocolModel extends Record({
    allowTransformationScript: false,
    autoCopyTargetContainer: undefined,
    availableDetectionMethods: undefined,
    availableMetadataInputFormats: undefined,
    availablePlateTemplates: undefined,
    backgroundUpload: false,
    description: undefined,
    domains: undefined,
    editableResults: false,
    editableRuns: false,
    metadataInputFormatHelp: undefined,
    moduleTransformScripts: undefined,
    name: undefined,
    protocolId: undefined,
    protocolParameters: undefined,
    protocolTransformScripts: undefined,
    providerName: undefined,
    saveScriptFiles: false,
    selectedDetectionMethod: undefined,
    selectedMetadataInputFormat: undefined,
    selectedPlateTemplate: undefined
}) {
    allowTransformationScript: boolean;
    autoCopyTargetContainer: string;
    availableDetectionMethods: any;
    availableMetadataInputFormats: any;
    availablePlateTemplates: any;
    backgroundUpload: boolean;
    description: string;
    domains: List<DomainDesign>;
    editableResults: boolean;
    editableRuns: boolean;
    metadataInputFormatHelp: any;
    moduleTransformScripts: Array<any>;
    name: string;
    protocolId: number;
    protocolParameters: any;
    protocolTransformScripts: any;
    providerName: string;
    saveScriptFiles: boolean;
    selectedDetectionMethod: any;
    selectedMetadataInputFormat: any;
    selectedPlateTemplate: any;

    constructor(values?: {[key:string]: any}) {
        super(values);
    }
}