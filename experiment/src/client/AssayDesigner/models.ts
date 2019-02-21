/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import {List, Record} from "immutable";
import {DomainDesign} from "@glass/domainproperties";

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