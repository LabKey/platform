import { IntegrationTestServer, RequestOptions, successfulResponse } from '@labkey/test';
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
    const response = await server.post('query', 'selectRows', {
        schemaName: 'samples',
        queryName: sampleType,
        'query.RowId~eq': sampleRowId,
        'query.columns': columns,
    }, { ...folderOptions, ...userOptions }).expect(successfulResponse);
    return response.body.rows
}

export async function sampleExists(server: IntegrationTestServer, sampleRowId: number, folderOptions: RequestOptions, userOptions: RequestOptions, sampleType: string = SAMPLE_TYPE_NAME_1) {
    const response = await getSampleData(server, sampleRowId, folderOptions, userOptions, sampleType);
    return response.length === 1;
}

export async function createSource(server: IntegrationTestServer, sourceName: string, folderOptions: RequestOptions, userOptions: RequestOptions, auditBehavior?: string, sourceType: string = SOURCE_TYPE_NAME_1) {
    const dataResponse = await server.post('query', 'insertRows', {
        schemaName: 'exp.data',
        queryName: sourceType,
        rows: [{ name: sourceName }],
        auditBehavior,
    }, { ...folderOptions, ...userOptions }).expect(successfulResponse);
    return caseInsensitive(dataResponse.body.rows[0], 'rowId');
}

export async function getSourceData(server: IntegrationTestServer, rowId: number, folderOptions: RequestOptions, userOptions: RequestOptions, sourceType: string = SOURCE_TYPE_NAME_1, columns: string = 'RowId') {
    const response = await server.post('query', 'selectRows', {
        schemaName: 'exp.data',
        queryName: sourceType,
        'query.RowId~eq': rowId,
        'query.columns': columns,
    }, { ...folderOptions, ...userOptions }).expect(successfulResponse);
    return response.body.rows
}

export async function sourceExists(server: IntegrationTestServer, rowId: number, folderOptions: RequestOptions, userOptions: RequestOptions, sourceType: string = SOURCE_TYPE_NAME_1) {
    const response = await getSourceData(server, rowId, folderOptions, userOptions, sourceType);
    return response.length === 1;
}


export async function createSample(server: IntegrationTestServer, sampleName: string, folderOptions: RequestOptions, userOptions: RequestOptions, auditBehavior?: string, sampleType: string = SAMPLE_TYPE_NAME_1) {
    const materialResponse = await server.post('query', 'insertRows', {
        schemaName: 'samples',
        queryName: sampleType,
        rows: [{ name: sampleName }],
        auditBehavior,
    }, { ...folderOptions, ...userOptions }).expect(successfulResponse);
    return caseInsensitive(materialResponse.body.rows[0], 'rowId');
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

async function getRowIdByName(server: IntegrationTestServer, queryName: string, dataTypeName: string, folderOptions: RequestOptions) {
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