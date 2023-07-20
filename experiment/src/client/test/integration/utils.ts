import { IntegrationTestServer, RequestOptions, successfulResponse } from '@labkey/test';
import { caseInsensitive } from '@labkey/components';

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