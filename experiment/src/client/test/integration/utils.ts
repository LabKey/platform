import { ExperimentCRUDUtils, IntegrationTestServer, RequestOptions, successfulResponse } from '@labkey/test';
import { caseInsensitive, SAMPLE_TYPE_DESIGNER_ROLE } from '@labkey/components';
import { PermissionRoles } from '@labkey/api';

export const SAMPLE_TYPE_NAME_1 = 'TestMoveSampleType1';
export const SAMPLE_TYPE_NAME_2 = 'TestMoveSampleType2';
export const FILE_FIELD_1_NAME = 'SampleFile1';
export const FILE_FIELD_2_NAME = 'SampleFile2';
export const SOURCE_TYPE_NAME_1 = 'SourceType1';
export const SOURCE_TYPE_NAME_2 = 'SourceType2';
export const ATTACHMENT_FIELD_1_NAME = 'SourceFile1';
export const ATTACHMENT_FIELD_2_NAME = 'SourceFile2';

export async function getSampleData(server: IntegrationTestServer, sampleRowId: number, folderOptions: RequestOptions, userOptions: RequestOptions, sampleType: string = SAMPLE_TYPE_NAME_1, columns: string = 'RowId') {
    return await ExperimentCRUDUtils.getSamplesData(server, [sampleRowId], sampleType, columns, folderOptions, userOptions);
}

export async function sampleExists(server: IntegrationTestServer, sampleRowId: number, folderOptions: RequestOptions, userOptions: RequestOptions, sampleType: string = SAMPLE_TYPE_NAME_1) {
    return await ExperimentCRUDUtils.sampleExists(server, sampleRowId, sampleType, folderOptions, userOptions);
}

export async function createSource(server: IntegrationTestServer, sourceType: string = SOURCE_TYPE_NAME_1, sourceName: string, folderOptions: RequestOptions, userOptions: RequestOptions, auditBehavior?) {
    const rows = await ExperimentCRUDUtils.createSource(server, sourceName, sourceType, folderOptions, userOptions, auditBehavior);
    return caseInsensitive(rows[0], 'rowId');
}

export async function getSourceData(server: IntegrationTestServer, rowId: number, folderOptions: RequestOptions, userOptions: RequestOptions, sourceType: string = SOURCE_TYPE_NAME_1, columns: string = 'RowId') {
    return await ExperimentCRUDUtils.getSourcesData(server, [rowId], sourceType, columns, folderOptions, userOptions);
}

export async function sourceExists(server: IntegrationTestServer, rowId: number, folderOptions: RequestOptions, userOptions: RequestOptions, sourceType: string = SOURCE_TYPE_NAME_1) {
    return await ExperimentCRUDUtils.sourceExists(server, rowId, sourceType, folderOptions, userOptions);
}


export async function createSample(server: IntegrationTestServer, sampleType: string = SAMPLE_TYPE_NAME_1, sampleName: string, folderOptions: RequestOptions, userOptions: RequestOptions, auditBehavior?) {
    const rows = await ExperimentCRUDUtils.createSample(server, sampleName, sampleType, folderOptions, userOptions, auditBehavior);
    return caseInsensitive(rows[0], 'rowId');
}

export async function createDerivedObjects(
    server: IntegrationTestServer,
    names: string[],
    targetSchema: string,
    targetTypeName: string,
    folderOptions: RequestOptions,
    userOptions: RequestOptions,
    parentSourceType: string,
    parentSources: string[],
    parentSampleType?: string,
    sampleParents?: string[],
    auditBehavior?: string,
) {
    const response = await server.request('query', 'insertRows', (agent, url) => {
        let request = agent.post(url);
        const rows = [];
        names.forEach(sampleName => {
            const row = {name: sampleName};
            if (parentSources)
                row['DataInputs/' + parentSourceType] = parentSources.join(',');
            if (sampleParents)
                row['MaterialInputs/' + parentSampleType] = sampleParents.join(',');
            rows.push(row);
        })
        request = request.field('json', JSON.stringify({
            schemaName: targetSchema,
            queryName: targetTypeName,
            rows,
            auditBehavior,
        }));

        return request;

    }, { ...userOptions, ...folderOptions }).expect(200);
    const data = [];
    response.body.rows.forEach(row => {
        data.push({
            name: caseInsensitive(row, 'name'),
            rowId: caseInsensitive(row, 'rowId'),
            run: caseInsensitive(row, 'run')
        });
    })
    return data;
}

export async function getExperimentRun(server: IntegrationTestServer, runId: number, folderOptions: RequestOptions) {
    const response = await server.post('query', 'selectRows', {
        schemaName: 'exp',
        queryName: 'runs',
        'query.rowid~eq': runId,
        'query.columns': 'rowId,name,container/Path',
    }, { ...folderOptions }).expect(successfulResponse);
    return response.body.rows;
}

export async function importRun(server: IntegrationTestServer, assayId: number, runName: string, dataRows: any[], folderOptions: RequestOptions, userOptions: RequestOptions, reRunId?: number, batchId?: number) {
    const runResponse = await server.post('assay', 'importRun', {
        assayId: assayId,
        name: runName,
        saveDataAsFile: true,
        jobDescription: "desc - " + runName,
        dataRows,
        reRunId,
        batchId,
    }, { ...folderOptions, ...userOptions }).expect(successfulResponse);
    return {
        runId: caseInsensitive(runResponse.body, 'runId'),
        batchId: caseInsensitive(runResponse.body, 'batchId'),
    }
}

export async function uploadAssayFile(server: IntegrationTestServer, assayDesignName: string, rowId: number, isRun: boolean, fieldName: string, fileName: string, folderOptions: RequestOptions, userOptions: RequestOptions) {

    const uploadResponse = await server.request('query', 'updateRows', (agent, url) => {
        let request = agent.post(url);

        request = request.field('json', JSON.stringify({
            schemaName:"assay.General." + assayDesignName,
            queryName: isRun ? "Runs" : "Data",
            rows:[{RowId:rowId}],
            skipReselectRows:true,
        }));

        request = request.attach(fieldName + "::0", fileName);

        return request;

    }, { ...folderOptions, ...userOptions }).expect(successfulResponse);

    return caseInsensitive(uploadResponse.body, 'rowsAffected')
}


export async function runExists(server: IntegrationTestServer, runId: number, folderOptions: RequestOptions) {
    const response = await getExperimentRun(server, runId, folderOptions);
    return response.length === 1;
}

export async function getAssayRunMovedAuditLogs(server: IntegrationTestServer, assayDesignName: string, runName: string, userComment: string, folderOptions: RequestOptions) {
    const payload = {
        schemaName: 'auditlog',
        queryName: 'ExperimentAuditEvent',
        'query.protocolrun~eq': assayDesignName + '~~KEYSEP~~' + runName,
        'query.comment~eq': 'Assay run was moved.'
    }
    if (userComment)
        payload['query.usercomment~eq'] = userComment;
    const response = await server.post('query', 'selectRows', payload, { ...folderOptions  }).expect(successfulResponse);
    return response.body.rows;
}

export async function getAssayResults(server: IntegrationTestServer, assayDesignName: string, fileField: string, runId: number, folderOptions: RequestOptions) {
    const response = await server.post('query', 'selectRows', {
        schemaName: "assay.General." + assayDesignName,
        queryName: 'Data',
        'query.run~eq': runId,
        'query.columns': 'rowId,' + fileField,
    }, { ...folderOptions }).expect(successfulResponse);
    return response.body.rows;
}

async function getRowIdByName(server: IntegrationTestServer, queryName: string, dataTypeName: string, folderOptions: RequestOptions) : Promise<number> {
    const response = await server.post('query', 'selectRows', {
        schemaName: 'exp',
        queryName: queryName,
        'query.name~eq': dataTypeName,
        'query.columns': "RowId",
    }, { ...folderOptions  }).expect(successfulResponse);
    if (response.body.rows?.length > 0)
        return caseInsensitive(response.body.rows[0], 'rowId');
    return 0;
}

export async function getDataClassRowIdByName(server: IntegrationTestServer, dataClassName: string, folderOptions: RequestOptions) {
    return getRowIdByName(server, 'dataclasses', dataClassName, folderOptions);
}

export async function getSampleTypeRowIdByName(server: IntegrationTestServer, sampleType: string, folderOptions: RequestOptions) {
    return getRowIdByName(server, 'samplesets', sampleType, folderOptions);
}

export async function getAssayDesignRowIdByName(server: IntegrationTestServer, assayName: string, folderOptions: RequestOptions) {
    return getRowIdByName(server, 'protocols', assayName, folderOptions);
}

async function addRecord(server: IntegrationTestServer, schema: string, queryName: string, recordName: string, folderOptions: RequestOptions, editorUserOptions: RequestOptions) {
    const materialResponse = await server.request('query', 'insertRows', (agent, url) => {
        let request = agent.post(url);

        request = request.field('json', JSON.stringify({
            schemaName: schema,
            queryName: queryName,
            rows: [{name: recordName}]
        }));

        return request;

    }, { ...folderOptions, ...editorUserOptions }).expect(successfulResponse);
    return caseInsensitive(materialResponse.body.rows[0], 'rowId');
}

async function deleteDataType(server: IntegrationTestServer, action: string, dataTypeRowId: number, folderOptions: RequestOptions, userOptions: RequestOptions) {
    const resp = await server.request('experiment', action, (agent, url) => {
        return agent
            .post(url)
            .type('form')
            .send({
                singleObjectRowId: dataTypeRowId,
                forceDelete: true
            });
    }, { ...folderOptions, ...userOptions });
    return resp;
}

export async function deleteSourceType(server: IntegrationTestServer, dataTypeRowId: number, folderOptions: RequestOptions, userOptions: RequestOptions) {
    return deleteDataType(server, 'deleteDataClass', dataTypeRowId, folderOptions, userOptions);
}

export async function deleteSampleType(server: IntegrationTestServer, sampleTypeRowId: number, folderOptions: RequestOptions, userOptions: RequestOptions) {
    return deleteDataType(server, 'deleteSampleTypes', sampleTypeRowId, folderOptions, userOptions);
}

export async function deleteAssayDesign(server: IntegrationTestServer, protocolRowId: number, folderOptions: RequestOptions, userOptions: RequestOptions) {
    return deleteDataType(server, 'deleteProtocolByRowIdsAPI', protocolRowId, folderOptions, userOptions);
}

export async function initProject(server: IntegrationTestServer, projectName: string, designerRole: string, ensureModules = ['experiment']) {
    await server.init(projectName, {
        ensureModules,
    });
    const topFolderOptions = { containerPath: projectName };

    const emailSuffix = '@' + projectName.replace(' ', '').toLowerCase() + '.com';
    // create subfolders to use in tests
    const subfolder1 = await server.createTestContainer();
    const subfolder1Options = { containerPath: subfolder1.path };
    const subfolder2 = await server.createTestContainer();
    const subfolder2Options = { containerPath: subfolder2.path };

    // create users with different permissions
    const readerUser = await server.createUser('reader' + emailSuffix);
    await server.addUserToRole(readerUser.username, PermissionRoles.Reader, projectName);
    const readerUserOptions = { requestContext: await server.createRequestContext(readerUser) };

    const editorUser = await server.createUser('editor' + emailSuffix);
    await server.addUserToRole(editorUser.username, PermissionRoles.Editor, projectName);
    await server.addUserToRole(editorUser.username, PermissionRoles.Editor, subfolder1.path);
    const editorUserOptions = { requestContext: await server.createRequestContext(editorUser) };

    const designer = await server.createUser('designer' + emailSuffix);
    await server.addUserToRole(designer.username, designerRole, projectName);
    const designerOptions = { requestContext: await server.createRequestContext(designer) };

    const designerReader = await server.createUser('readerdesigner' + emailSuffix);
    await server.addUserToRole(designerReader.username, designerRole, projectName);
    await server.addUserToRole(designerReader.username, PermissionRoles.Reader, projectName);
    const designerReaderOptions = { requestContext: await server.createRequestContext(designerReader) };

    const designerEditor = await server.createUser('designereditor' + emailSuffix);
    await server.addUserToRole(designerEditor.username, PermissionRoles.Editor, projectName);
    await server.addUserToRole(designerEditor.username, designerRole, projectName);
    const designerEditorOptions = { requestContext: await server.createRequestContext(designerEditor) };

    const admin = await server.createUser('admin' + emailSuffix);
    await server.addUserToRole(admin.username, PermissionRoles.ProjectAdmin, projectName);
    await server.addUserToRole(admin.username, PermissionRoles.FolderAdmin, subfolder1.path);
    await server.addUserToRole(admin.username, PermissionRoles.FolderAdmin, subfolder2.path);
    const adminOptions = { requestContext: await server.createRequestContext(admin) };

    return {
        topFolderOptions,
        subfolder1Options,
        subfolder2Options,
        readerUser,
        readerUserOptions,
        editorUser,
        editorUserOptions,
        designer,
        designerOptions,
        designerReader,
        designerReaderOptions,
        designerEditor,
        designerEditorOptions,
        admin,
        adminOptions
    }
}

export async function checkLackDesignerOrReaderPerm(server: IntegrationTestServer, domainType: string, topFolderOptions: RequestOptions, readerUserOptions: RequestOptions, editorUserOptions: RequestOptions, designerOptions: RequestOptions) {
    await server.post('property', 'createDomain', {
        kind: domainType,
        domainDesign: { name: "Failed", fields: [{ name: 'Prop' }] },
        options: {
            name: "Failed",
        }
    }, {...topFolderOptions, ...readerUserOptions}).expect(403);

    await server.post('property', 'createDomain', {
        kind: domainType,
        domainDesign: { name: "Failed", fields: [{ name: 'Prop' }] },
        options: {
            name: "Failed",
        }
    }, {...topFolderOptions, ...editorUserOptions}).expect(403);

    await server.post('property', 'createDomain', {
        kind: domainType,
        domainDesign: { name: "Failed", fields: [{ name: 'Prop' }] },
        options: {
            name: "Failed",
        }
    }, {...topFolderOptions, ...designerOptions}).expect(403);
}

export async function verifyRequiredLineageInsertUpdate(server: IntegrationTestServer, isParentSample: boolean, isChildSample: boolean, topFolderOptions: RequestOptions, subfolder1Options: RequestOptions, designerReaderOptions: RequestOptions, readerUserOptions: RequestOptions, editorUserOptions: RequestOptions) {
    const parentDataType = isParentSample ? "ParentSampleType" : "ParentDataType";
    await server.post('property', 'createDomain', {
        kind: isParentSample ? 'SampleSet' : 'DataClass',
        domainDesign: { name: parentDataType, fields: [{ name: isParentSample ? 'Name' : 'Prop' }] },
        options: {
            name: parentDataType,
        }
    }, {...topFolderOptions, ...designerReaderOptions}).expect(successfulResponse);
    const parentDtaTypeRowId : number = await (isParentSample ? getSampleTypeRowIdByName(server, parentDataType, topFolderOptions) : getDataClassRowIdByName(server, parentDataType, topFolderOptions));

    // create some parent data
    const createParentDataFn = isParentSample ? createSample : createSource;
    await createParentDataFn(server, parentDataType, 'PDataHome', topFolderOptions, editorUserOptions);
    await createParentDataFn(server, parentDataType, 'PDataC1', subfolder1Options, editorUserOptions);
    await createParentDataFn(server, parentDataType, 'PDataHome2', topFolderOptions, editorUserOptions);
    await createParentDataFn(server, parentDataType, 'PDataC2', subfolder1Options, editorUserOptions);

    const dataType = "withRequired" + (isParentSample ? 'SampleParent' : 'DataParent');
    let childDomainId = -1, childDomainURI = '';
    await server.post('property', 'createDomain', {
        kind: isChildSample ? 'SampleSet' : 'DataClass',
        domainDesign: { name: dataType, fields: [{ name: isChildSample ? 'Name' : 'Prop' }]},
        options: {
            name: dataType,
            importAliases: {
                'pAlias': {
                    inputType: (isParentSample ? 'materialInputs/' : 'dataInputs/') + parentDataType,
                    required: false,
                }
            }
        }
    }, {...topFolderOptions, ...designerReaderOptions}).expect((result) => {
        const domain = JSON.parse(result.text);
        childDomainId = domain.domainId;
        childDomainURI = domain.domainURI;
        return true;
    });

    const dataTypeRowId = await (isChildSample ? getSampleTypeRowIdByName(server, dataType, topFolderOptions) : getDataClassRowIdByName(server, dataType, topFolderOptions));

    const createChildDataFn = isChildSample ? createSample : createSource;
    const homeDataRowId = await createChildDataFn(server, dataType, 'CData1', topFolderOptions, editorUserOptions);
    const sub1DataRowId = await createChildDataFn(server, dataType, 'CData2', subfolder1Options, editorUserOptions);

    // verify cannot add required parent alias with missing lineage
    const updateDomainPayload = {
        domainId: childDomainId,
        domainDesign: {name: dataType, domainId: childDomainId, domainURI: childDomainURI, fields: [{ name: 'Prop' }]},
        options: {
            rowId: isChildSample ? undefined : dataTypeRowId /*dataclass update domain needs rowid passed in*/,
            name: dataType,
            nameExpression: 'S-${genId}',
            importAliases: {
                'pAlias': {
                    inputType: (isParentSample ? 'materialInputs/' : 'dataInputs/') + parentDataType,
                    required: true,
                }
            }
        }
    };
    let requiredNotAllowedResp = await server.post('property', 'saveDomain', updateDomainPayload, {...topFolderOptions, ...designerReaderOptions});
    expect(requiredNotAllowedResp['body']['success']).toBeFalsy();
    expect(requiredNotAllowedResp['body']['exception']).toBe("'" + parentDataType + "' cannot be required as a parent type when there are existing " + (isChildSample ? 'samples' : 'data') + " without a parent of this type.");
    await verifyRequiredLineageReference(server, parentDtaTypeRowId, isParentSample, topFolderOptions, readerUserOptions);

    const dataSchema = isChildSample ? 'samples' : 'exp.data';
    const parentInput = (isParentSample ? 'materialInputs/' : "dataInputs/") + parentDataType;
    // update existing Home data to add missing lineage
    await ExperimentCRUDUtils.updateRows(server, [{
        'rowId': homeDataRowId,
        [parentInput]: 'PDataHome'}], dataSchema, dataType, topFolderOptions, editorUserOptions);

    // required lineage still cannot be added due to missing lineage in child folder
    requiredNotAllowedResp = await server.post('property', 'saveDomain', updateDomainPayload, {...topFolderOptions, ...designerReaderOptions});
    expect(requiredNotAllowedResp['body']['success']).toBeFalsy();
    expect(requiredNotAllowedResp['body']['exception']).toBe("'" + parentDataType + "' cannot be required as a parent type when there are existing " + (isChildSample ? 'samples' : 'data') + " without a parent of this type.");

    // update existing Child data to add missing lineage
    await ExperimentCRUDUtils.updateRows(server, [{
        'rowId': sub1DataRowId,
        [parentInput]: 'PDataC1'}], dataSchema, dataType, subfolder1Options, editorUserOptions);

    // verify required lineage can now be added with all existing data have lineage
    await server.post('property', 'saveDomain', updateDomainPayload, {...topFolderOptions, ...designerReaderOptions}).expect(successfulResponse);
    const reference = [dataType];
    await verifyRequiredLineageReference(server, parentDtaTypeRowId, isParentSample, topFolderOptions, readerUserOptions, isChildSample ? reference : [], isChildSample ? [] : reference);

    // verify creating new data using insert now requires parent lineage
    await insertRowsExpectError(server, [{'name': 'CData3'}], dataSchema, dataType, 'Data does not contain required field: ' + parentInput, topFolderOptions, editorUserOptions);
    await insertRowsExpectError(server, [{'name': 'CData3', [parentInput]: ''}], dataSchema, dataType, 'Missing value for required property: ' + parentInput, topFolderOptions, editorUserOptions);
    await insertRowsExpectError(server, [{'name': 'CData3', ['pAlias']: ''}], dataSchema, dataType, 'Missing value for required property: pAlias', topFolderOptions, editorUserOptions);

    // verify creating new data using import/merge now requires parent lineage
    let failedImportResp = await ExperimentCRUDUtils.importData(server, 'name\nCData3', dataType, 'IMPORT', topFolderOptions, editorUserOptions, false, false, isChildSample, true);
    let failedImport = JSON.parse(failedImportResp.text);
    expect(failedImport.exception).toBe('Data does not contain required field: ' + parentInput);
    failedImportResp = await ExperimentCRUDUtils.importData(server, 'name\t' + parentInput + '\nCData3\t', dataType, 'IMPORT', topFolderOptions, editorUserOptions, false, false, isChildSample, true);
    failedImport = JSON.parse(failedImportResp.text);
    expect(failedImport.exception).toBe('Missing value for required property: ' + parentInput);
    failedImportResp = await ExperimentCRUDUtils.importData(server, 'name\t' + parentInput + '\nCData3\tbadparentname', dataType, 'IMPORT', topFolderOptions, editorUserOptions, false, false, isChildSample, true);
    failedImport = JSON.parse(failedImportResp.text);
    expect(failedImport.exception).toContain("'badparentname' not found in");
    failedImportResp = await ExperimentCRUDUtils.importData(server, 'name\tpAlias\nCData3\t', dataType, 'IMPORT', topFolderOptions, editorUserOptions, false, false, isChildSample, true);
    failedImport = JSON.parse(failedImportResp.text);
    expect(failedImport.exception).toBe('Missing value for required property: pAlias');
    failedImportResp = await ExperimentCRUDUtils.importData(server, 'name\nCData3', dataType, 'MERGE', topFolderOptions, editorUserOptions, false, false, isChildSample, true);
    failedImport = JSON.parse(failedImportResp.text);
    expect(failedImport.exception).toBe('Data does not contain required field: ' + parentInput);

    // verify cannot remove existing data's required lineage using update
    await updateRowsExpectError(server, [{'rowId': homeDataRowId, [parentInput]: ''}], dataSchema, dataType, 'Missing value for required property: ' + parentInput, topFolderOptions, editorUserOptions);
    // TODO: parent alias doesn't work for query.update api when used with rowId
    await updateRowsExpectError(server, [{'rowId': sub1DataRowId, [parentInput]: ''}], dataSchema, dataType, 'Missing value for required property: ' + parentInput, subfolder1Options, editorUserOptions);
    failedImportResp = await ExperimentCRUDUtils.importData(server, 'name\t' + parentInput + '\nCData1\t', dataType, 'UPDATE', topFolderOptions, editorUserOptions, false, false, isChildSample, true);
    failedImport = JSON.parse(failedImportResp.text);
    expect(failedImport.exception).toBe('Missing value for required property: ' + parentInput);
    failedImportResp = await ExperimentCRUDUtils.importData(server, 'name\tpAlias\nCData1\t', dataType, 'UPDATE', topFolderOptions, editorUserOptions, false, false, isChildSample, true);
    failedImport = JSON.parse(failedImportResp.text);
    expect(failedImport.exception).toBe('Missing value for required property: pAlias');
    failedImportResp = await ExperimentCRUDUtils.importData(server, 'name\nCData1', dataType, 'MERGE', topFolderOptions, editorUserOptions, false, false, isChildSample, true);
    failedImport = JSON.parse(failedImportResp.text);
    expect(failedImport.exception).toBe('Data does not contain required field: ' + parentInput);
    failedImportResp = await ExperimentCRUDUtils.importData(server, 'name\tpAlias\nCData1\t', dataType, 'MERGE', topFolderOptions, editorUserOptions, false, false, isChildSample, true);
    failedImport = JSON.parse(failedImportResp.text);
    expect(failedImport.exception).toBe('Missing value for required property: pAlias');

    // verify update (api, from file) is successful when required parent column is not included
    const selfInput = (isChildSample ? 'materialInputs/' : 'dataInputs/') + dataType;
    await ExperimentCRUDUtils.updateRows(server, [{'rowId': homeDataRowId, [selfInput]: ''}], dataSchema, dataType, topFolderOptions, editorUserOptions);
    await ExperimentCRUDUtils.updateRows(server, [{'rowId': sub1DataRowId, 'description': 'updated!'}], dataSchema, dataType, subfolder1Options, editorUserOptions);
    let successResp = await ExperimentCRUDUtils.importData(server, 'name\t' + selfInput + '\nCData1\t', dataType, 'UPDATE', topFolderOptions, editorUserOptions, false, false, isChildSample);
    let successImport = JSON.parse(successResp.text);
    expect(successImport.success).toBeTruthy();
    successResp = await ExperimentCRUDUtils.importData(server, 'name\tdescription\nCData1\tupdated', dataType, 'UPDATE', topFolderOptions, editorUserOptions, false, false, isChildSample);
    expect(JSON.parse(successResp.text).success).toBeTruthy();

    // verify updating (api, update using import, merge) required parent to not empty values is successful
    await ExperimentCRUDUtils.updateRows(server, [{'rowId': homeDataRowId, [parentInput]: 'PDataHome2'}], dataSchema, dataType, topFolderOptions, editorUserOptions);
    await ExperimentCRUDUtils.updateRows(server, [{'rowId': sub1DataRowId, [parentInput]: 'PDataC2'}], dataSchema, dataType, subfolder1Options, editorUserOptions);
    successResp = await ExperimentCRUDUtils.importData(server, 'name\tdescription\nCData1\tupdated', dataType, 'UPDATE', topFolderOptions, editorUserOptions, false, false, isChildSample);
    expect(JSON.parse(successResp.text).success).toBeTruthy();
    successResp = await ExperimentCRUDUtils.importData(server, 'name\t' + parentInput + '\nCData1\tPDataHome', dataType, 'UPDATE', topFolderOptions, editorUserOptions, false, false, isChildSample);
    expect(JSON.parse(successResp.text).success).toBeTruthy();
    successResp = await ExperimentCRUDUtils.importData(server, 'name\tpAlias\nCData1\tPDataHome,PDataHome2', dataType, 'UPDATE', topFolderOptions, editorUserOptions, false, false, isChildSample);
    expect(JSON.parse(successResp.text).success).toBeTruthy();
    successResp = await ExperimentCRUDUtils.importData(server, 'name\t' + parentInput + '\nCData1\tPDataHome', dataType, 'MERGE', topFolderOptions, editorUserOptions, false, false, isChildSample);
    expect(JSON.parse(successResp.text).success).toBeTruthy();
    successResp = await ExperimentCRUDUtils.importData(server, 'name\tpAlias\nCData1\tPDataHome,PDataHome2', dataType, 'MERGE', topFolderOptions, editorUserOptions, false, false, isChildSample);
    expect(JSON.parse(successResp.text).success).toBeTruthy();

    // verify creating new data with required parent is successful
    await ExperimentCRUDUtils.insertRows(server, [{'name': 'CData4', [parentInput]: 'PDataHome'}], dataSchema, dataType, topFolderOptions, editorUserOptions);
    await ExperimentCRUDUtils.insertRows(server, [{['pAlias']: 'PDataC1'}], dataSchema, dataType, subfolder1Options, editorUserOptions);

    // verify creating new data using import/merge now requires parent lineage
    successResp = await ExperimentCRUDUtils.importData(server, parentInput + '\nPDataHome', dataType, 'IMPORT', topFolderOptions, editorUserOptions, false, false, isChildSample);
    expect(JSON.parse(successResp.text).success).toBeTruthy();
    successResp = await ExperimentCRUDUtils.importData(server, 'pAlias\nPDataHome', dataType, 'IMPORT', topFolderOptions, editorUserOptions, false, false, isChildSample);
    expect(JSON.parse(successResp.text).success).toBeTruthy();
    successResp = await ExperimentCRUDUtils.importData(server, 'name\tpAlias\nCData5\tPDataHome', dataType, 'MERGE', topFolderOptions, editorUserOptions, false, false, isChildSample);
    expect(JSON.parse(successResp.text).success).toBeTruthy();

}

export const getAssayDesignPayload = (name: string, runFields: any[], resultFields: any[]) => {
    return {
        "allowEditableResults": true,
        "editableResults": true,
        "editableRuns": true,
        "domains": [
            {
                "name": "Batch Fields",
                "domainURI": "urn:lsid:${LSIDAuthority}:AssayDomain-Batch.Folder-${Container.RowId}:${AssayName}",
                "domainId": 0,
                "fields": [],
                "indices": [],
                "mandatoryFieldNames": [],
                "domainKindName": "Assay",
            },
            {
                "name": "Run Fields",
                "domainURI": "urn:lsid:${LSIDAuthority}:AssayDomain-Run.Folder-${Container.RowId}:${AssayName}",
                "domainId": 0,
                "fields": runFields,
                "indices": [],
                "mandatoryFieldNames": [],
                "domainKindName": "Assay",
            },
            {
                "name": "Data Fields",
                "domainURI": "urn:lsid:${LSIDAuthority}:AssayDomain-Data.Folder-${Container.RowId}:${AssayName}",
                "domainId": 0,
                "fields": resultFields,
                "indices": [],
                "domainKindName": "Assay",
            }
        ],
        "name": name,
        "protocolId": null,
        "providerName": "General",
        "status": "Active",
    }
}

export async function verifyRequiredLineageReference(server: IntegrationTestServer, parentTypeRowId: number, isSampleParent: boolean, folderOptions: RequestOptions, userOptions: RequestOptions, sampleTypeRefs: string[] = [], dataTypeRefs : string[] = []){
    await server.post('experiment', 'getDataTypesWithRequiredLineage', {
        parentDataTypeRowId: parentTypeRowId,
        sampleParent: isSampleParent,
    }, {...folderOptions, ...userOptions}).expect((result) => {
        const resp = JSON.parse(result.text);
        expect(resp['dataClasses']).toEqual(dataTypeRefs);
        expect(resp['sampleTypes']).toEqual(sampleTypeRefs);
    });
}

export async function insertRowsExpectError(server: IntegrationTestServer, rows: any[], schemaName: string, queryName: string, error: string, folderOptions: RequestOptions, userOptions: RequestOptions, isUpdate?: boolean) {
    await server.post('query', isUpdate? 'updateRows' : 'insertRows', {
        schemaName,
        queryName,
        rows,
    }, { ...folderOptions, ...userOptions }).expect((result) => {
        const resp = JSON.parse(result.text);
        expect(resp.success).toBeFalsy();
        expect(resp.exception).toBe(error);
    });
}

export async function updateRowsExpectError(server: IntegrationTestServer, rows: any[], schemaName: string, queryName: string, error: string, folderOptions: RequestOptions, userOptions: RequestOptions) {
    return insertRowsExpectError(server, rows, schemaName, queryName, error, folderOptions, userOptions, true);
}