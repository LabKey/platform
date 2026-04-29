import {
    ExperimentCRUDUtils,
    generateFieldName,
    IntegrationTestServer,
    RequestOptions,
    selectRandomN,
    successfulResponse,
} from '@labkey/test';
import { caseInsensitive, IDomainField, Row, RowValue, SCHEMAS } from '@labkey/components';
import { AssayDOM, PermissionRoles } from '@labkey/api';

export const SAMPLE_TYPE_NAME_1 = 'TestMoveSampleType1';
export const SAMPLE_TYPE_NAME_2 = 'TestMoveSampleType2';
export const FILE_FIELD_1_NAME = 'SampleFile1';
export const FILE_FIELD_2_NAME = 'SampleFile2';
export const SOURCE_TYPE_NAME_1 = 'SourceType1';
export const SOURCE_TYPE_NAME_2 = 'SourceType2';
export const ATTACHMENT_FIELD_1_NAME = 'SourceFile1';
export const ATTACHMENT_FIELD_2_NAME = 'SourceFile2';
const SAMPLE_TYPE_DOMAIN_KIND = 'SampleSet';
const DATA_CLASS_DOMAIN_KIND = 'DataClass';

export const MVTC_FIELD_PROP = {
    "propertyId": -1,
    "propertyValidators": [
        {
            "type": "TextChoice",
            "name": "Text Choice Validator",
            "new": true,
            "expression": "Abnormal|agent|cDNA|Plasma"
        }
    ],
    "rangeURI": "http://cpas.fhcrc.org/exp/xml#multiChoice",
};

export const TC_FIELD_PROP = {
    ...MVTC_FIELD_PROP,
    rangeURI: 'http://www.w3.org/2001/XMLSchema#string',
    conceptURI: 'http://www.labkey.org/types#textChoice',
};


export function options(folderOptions?: RequestOptions, userOptions?: RequestOptions): RequestOptions {
    return folderOptions || userOptions ? { ...folderOptions, ...userOptions } : undefined;
}

export interface AssayDesignFieldOptions {
    batchFields?: Partial<IDomainField>[];
    resultFields?: Partial<IDomainField>[];
    runFields?: Partial<IDomainField>[];
}

export async function createAssayDesign(
    server: IntegrationTestServer,
    assayName: string,
    fields?: AssayDesignFieldOptions,
    folderOptions?: RequestOptions,
    userOptions?: RequestOptions
) {
    const { batchFields, resultFields, runFields } = fields || {};
    const payload = getAssayDesignPayload(assayName, batchFields, runFields, resultFields);
    const response = await server
        .post('assay', 'saveProtocol.api', payload, options(folderOptions, userOptions))
        .expect(successfulResponse);
    return response.body.data;
}

export function createDomainField(field: Partial<IDomainField>): Partial<IDomainField> {
    return {
        scale: 4000,
        shownInDetailsView: true,
        shownInInsertView: true,
        shownInUpdateView: true,
        ...field,
    };
}

// TODO move getSourceDataByName to ExperimentCrudUtils
export async function getSourceDataByName(server: IntegrationTestServer, sourceName: string, queryName: string, columns: string = 'Name, RowId', folderOptions: RequestOptions , userOptions: RequestOptions) : Promise<any> {
    const response = await server.post('query', 'selectRows', {
        schemaName: 'exp.data',
        queryName,
        'query.Name~eq': sourceName,
        'query.columns': columns,
    }, options(folderOptions, userOptions)).expect(successfulResponse);
    return response.body.rows[0];
}

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

    }, options(folderOptions, userOptions)).expect(200);
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

export async function getRunQueryRow(
    server: IntegrationTestServer,
    assayName: string,
    runId: number,
    folderOptions?: RequestOptions,
    userOptions?: RequestOptions
): Promise<Record<string, RowValue>> {
    const response = await server
        .post(
            'query',
            'selectRows.api',
            {
                schemaName: `assay.General.${assayName}`,
                'query.queryName': 'Runs',
                apiVersion: 17.1,
                'query.RowId~eq': runId,
            },
            options(folderOptions, userOptions)
        )
        .expect(successfulResponse);
    return response.body.rows[0].data;
}

export async function getAuditLogsForTransaction(
    server: IntegrationTestServer,
    transactionId: number,
    auditQuery: string,
    folderOptions?: RequestOptions,
    userOptions?: RequestOptions
): Promise<Row[]> {
    const response = await server
        .post(
            'query',
            'selectRows.api',
            {
                schemaName: SCHEMAS.AUDIT_TABLES.SCHEMA,
                'query.queryName': auditQuery,
                apiVersion: 17.1,
                'query.transactionId~eq': transactionId,
                'query.columns': '*',
            },
            options(folderOptions, userOptions)
        )
        .expect(successfulResponse);
    return response.body.rows;
}

/**
 * @deprecated Use {@link importRunToServer} instead.
 */
export async function importRun(server: IntegrationTestServer, assayId: number, runName: string, dataRows: any[], folderOptions: RequestOptions, userOptions: RequestOptions, reRunId?: number, batchId?: number) {
    const runResponse = await server.post('assay', 'importRun', {
        assayId: assayId,
        name: runName,
        saveDataAsFile: true,
        jobDescription: "desc - " + runName,
        dataRows,
        reRunId,
        batchId,
    }, options(folderOptions, userOptions)).expect(successfulResponse);
    return {
        runId: caseInsensitive(runResponse.body, 'runId'),
        batchId: caseInsensitive(runResponse.body, 'batchId'),
    }
}

function serializeRunProperties(
    prop: string,
    properties: any,
    attachments: Record<string, any>,
    fields: Record<string, any>
): void {
    if (!properties) return;

    for (const [key, value] of Object.entries(properties)) {
        const propKey = `${prop}['${key}']`;
        if (typeof value === 'string') {
            if (value.startsWith('file://')) {
                attachments[propKey] = value;
            } else {
                fields[propKey] = value;
            }
        } else {
            fields[propKey] = JSON.stringify(value);
        }
    }
}

export type ImportRunOptions = Omit<AssayDOM.ImportRunOptions, 'failure' | 'scope' | 'success'>;

export async function importRunToServer(
    server: IntegrationTestServer,
    importRunOptions: ImportRunOptions,
    folderOptions?: RequestOptions,
    userOptions?: RequestOptions
) {
    const { batchProperties, properties, ...props } = importRunOptions;

    const fields = {};
    const attachments = {};

    for (const [key, value] of Object.entries(props)) {
        fields[key] = JSON.stringify(value);
    }

    serializeRunProperties('batchProperties', batchProperties, attachments, fields);
    serializeRunProperties('properties', properties, attachments, fields);

    return server.request(
        'assay',
        'importRun.api',
        (agent, url) => {
            let req = agent.post(url);
            for (const [key, value] of Object.entries(fields)) {
                req = req.field(key, value);
            }
            for (const [key, value] of Object.entries(attachments)) {
                req = req.attach(key, value);
            }
            return req;
        },
        options(folderOptions, userOptions)
    );
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

async function verifyDomainCreateFailure(server: IntegrationTestServer, domainType: string, badDomainName: string, error: string, folderOptions: RequestOptions, userOptions: RequestOptions, domainFields?: any[]) {
    const field : Record<string, string> = domainType === SAMPLE_TYPE_DOMAIN_KIND ? { name: 'Name' } : { name: 'Prop' };
    const fields = [field];
    if (domainFields)
        fields.push(...domainFields);
    const badDomainNameResp = await server.post('property', 'createDomain', {
        kind: domainType,
        domainDesign: { name: badDomainName, fields },
        options: {
            name: badDomainName,
        }
    }, {...folderOptions, ...userOptions});

    expect(badDomainNameResp['body']['success']).toBeFalsy();
    expect(badDomainNameResp['body']['exception']).toBe(error.replace('REPLACE', () => badDomainName));
}

async function verifyDomainUpdateFailure(server: IntegrationTestServer, domainId: number, domainURI: string, dataTypeRowId/*needed for updating dataclass*/: number, badDomainName: string, error: string, folderOptions: RequestOptions, userOptions: RequestOptions, domainFields?: any[]) {
    const options = {
        name: badDomainName
    }
    if (dataTypeRowId)
        options['rowId'] = dataTypeRowId;
    const domainDesign = {
        name: badDomainName,
        domainId,
        domainURI
    }
    if (domainFields)
        domainDesign['fields'] = domainFields;
    const updatedDomainPayload = {
        domainId,
        domainDesign,
        options
    };

    const badDomainNameResp = await server.post('property', 'saveDomain', updatedDomainPayload, {...folderOptions, ...userOptions});

    expect(badDomainNameResp['body']['success']).toBeFalsy();
    expect(badDomainNameResp['body']['exception']).toBe(error.replace('REPLACE', () => badDomainName));
}

async function verifyDomainCreateSuccess(server: IntegrationTestServer, domainType: string, domainName: string, folderOptions: RequestOptions, userOptions: RequestOptions) {
    let domainId, domainURI;
    const field = domainType === SAMPLE_TYPE_DOMAIN_KIND ? { name: 'Name' } : { name: 'Prop' };
    await server.post('property', 'createDomain', {
        kind: domainType,
        domainDesign: { name: domainName, fields: [field] },
        options: {
            name: domainName,
        }
    }, {...folderOptions, ...userOptions}).expect((result) => {
        const domain = JSON.parse(result.text);
        expect(domain).toHaveProperty('domainId');
        expect(domain).toHaveProperty('domainURI');
        domainId = domain.domainId;
        domainURI = domain.domainURI;
        return true;
    });
    return {domainId, domainURI};
}

export const ILLEGAL_DOMAIN_CHARSET = "<>[]{};,`\"~!@#$%^*=|?\\";
const LEGAL_CHARSET = [' ', '+', '-', '_', '.', ':', '', '&', '(', ')', '/'];
const alphaNumeric = ['a', 'A', '1', '0'];
export async function checkDomainName(server: IntegrationTestServer, domainType: string, supportNameExpression: boolean, folderOptions: RequestOptions, userOptions: RequestOptions) {
    const badNames = {
        '': domainType === SAMPLE_TYPE_DOMAIN_KIND ? 'Sample Type name is required.' : `${domainType} name must not be blank.`,
        ' ': domainType === SAMPLE_TYPE_DOMAIN_KIND ? 'Sample Type name is required.' : `${domainType} name must not be blank.`,
        'with\0nullCharacter': `Invalid ${domainType} name 'REPLACE'. ${domainType} name must contain only valid unicode characters.`,
        'with\tnewLines': `Invalid ${domainType} name 'REPLACE'. ${domainType} name may not contain 'tab', 'new line', or 'return' characters.`,
        '.startWithDot': `Invalid ${domainType} name 'REPLACE'. ${domainType} name must start with a letter or a number.`,
        ['c' + selectRandomN(ILLEGAL_DOMAIN_CHARSET.split(''), 2).join('')]: `Invalid ${domainType} name 'REPLACE'. ${domainType} name may not contain any of these characters: ` + ILLEGAL_DOMAIN_CHARSET,
        'a -b': `Invalid ${domainType} name 'REPLACE'. ${domainType} name may not contain space followed by dash.`
    };
    if (supportNameExpression) {
        badNames['withCounter'] = `Invalid ${domainType} name 'REPLACE'. 'withCounter' is a reserved name.`;
        badNames['int:withCounter'] = `Invalid ${domainType} name 'REPLACE'. ':withCounter' is a reserved pattern.`;
        badNames['drawdate:first'] = `Invalid ${domainType} name 'REPLACE'. ':first' is a reserved pattern.`;
    }

    let badNameKeys = Object.keys(badNames);
    for (let i = 0; i < badNameKeys.length; i++)
        await verifyDomainCreateFailure(server, domainType, badNameKeys[i], badNames[badNameKeys[i]], folderOptions, userOptions);

    const domainNameWithBadField = 'FieldNameNewLineInvalid';
    const fieldNameError = " -- Field name may not contain 'tab', 'new line', or 'return' characters.";
    await verifyDomainCreateFailure(server, domainType, domainNameWithBadField, domainNameWithBadField + fieldNameError, folderOptions, userOptions, [{'Name': 'a\nb'}]);
    await verifyDomainCreateFailure(server, domainType, domainNameWithBadField, domainNameWithBadField + fieldNameError, folderOptions, userOptions, [{'Name': 'a\rb'}]);
    await verifyDomainCreateFailure(server, domainType, domainNameWithBadField, domainNameWithBadField + fieldNameError, folderOptions, userOptions, [{'Name': 'a\tb'}]);

    if (!supportNameExpression)
    {
        await verifyDomainCreateSuccess(server, domainType, 'withCounter', folderOptions, userOptions);
    }

    // spaces should be trimmed before validation
    await verifyDomainCreateSuccess(server, domainType, ' startWithSpace', folderOptions, userOptions);

    const domainName = selectRandomN(alphaNumeric, 2).join('') + selectRandomN(LEGAL_CHARSET, 5).join('').replaceAll(' -', ' _-'); // name may not contain space followed by dash
    const { domainId, domainURI } = await verifyDomainCreateSuccess(server, domainType, domainName, folderOptions, userOptions);

    let dataTypeRowId = 0;
    if (domainType !== SAMPLE_TYPE_DOMAIN_KIND)
        dataTypeRowId = await getDataClassRowIdByName(server, domainName, folderOptions);
    const requireMsg = domainType == SAMPLE_TYPE_DOMAIN_KIND ? 'Sample Type name is required.' : `${domainType} name must not be blank.`;
    badNames[''] = requireMsg;
    badNames[' '] = requireMsg;
    for (let i = 0; i < badNameKeys.length; i++){
        await verifyDomainUpdateFailure(server, domainId, domainURI, dataTypeRowId, badNameKeys[i], badNames[badNameKeys[i]], folderOptions, userOptions);
    }

    await verifyDomainUpdateFailure(server, domainId, domainURI, dataTypeRowId, domainName, domainName + fieldNameError, folderOptions, userOptions, [{'Name': 'a\nb'}]);
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

export async function verifyRequiredLineageInsertUpdate(server: IntegrationTestServer, isParentSample: boolean, isChildSample: boolean, topFolderOptions: RequestOptions, subfolder1Options: RequestOptions, designerReaderOptions: RequestOptions, readerUserOptions: RequestOptions, editorUserOptions: RequestOptions, adminUserOptions: RequestOptions) {
    const parentDataType = isParentSample ? "ParentSampleType" : "ParentDataType";
    await server.post('property', 'createDomain', {
        kind: isParentSample ? SAMPLE_TYPE_DOMAIN_KIND : DATA_CLASS_DOMAIN_KIND,
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

    const useLowerCase = Math.random() < 0.5;
    // test both lower case and upper case prefix
    const parentInput = (isParentSample ? (useLowerCase ? 'materialInputs/' : 'MaterialInputs/') : (useLowerCase ? 'dataInputs/' : 'DataInputs/')) + parentDataType;
    await server.post('property', 'createDomain', {
        kind: isChildSample ? SAMPLE_TYPE_DOMAIN_KIND : DATA_CLASS_DOMAIN_KIND,
        domainDesign: { name: dataType, fields: [{ name: isChildSample ? 'Name' : 'Prop' }]},
        options: {
            name: dataType,
            importAliases: {
                'pAlias': {
                    inputType: parentInput,
                    required: false,
                }
            }
        }
    }, {...topFolderOptions, ...designerReaderOptions}).expect((result) => {
        const domain = JSON.parse(result.text);
        expect(domain).toHaveProperty('domainId');
        expect(domain).toHaveProperty('domainURI');
        childDomainId = domain.domainId;
        childDomainURI = domain.domainURI;
        return true;
    });

    const dataTypeRowId = await (isChildSample ? getSampleTypeRowIdByName(server, dataType, topFolderOptions) : getDataClassRowIdByName(server, dataType, topFolderOptions));

    const createChildDataFn = isChildSample ? createSample : createSource;
    const homeDataRowId = await createChildDataFn(server, dataType, 'CData1', topFolderOptions, editorUserOptions);
    const sub1DataRowId = await createChildDataFn(server, dataType, 'CData2', subfolder1Options, editorUserOptions);

    const dataSchema = isChildSample ? 'samples' : 'exp.data';

    let aliquotRowId = -1;
    if (isChildSample) {
        // create an aliquot, check aliquots are excluded from missing required lineage data check
        const results = await ExperimentCRUDUtils.insertRows(server, [{'AliquotedFrom': 'CData1', [parentInput]: ''}], dataSchema, dataType, topFolderOptions, editorUserOptions);
        aliquotRowId = caseInsensitive(results[0], 'rowId');
        await ExperimentCRUDUtils.importSample(server, "AliquotedFrom\nCData1", dataType, 'IMPORT', topFolderOptions, editorUserOptions);
    }

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
                    inputType: parentInput,
                    required: true,
                }
            }
        }
    };
    let requiredNotAllowedResp = await server.post('property', 'saveDomain', updateDomainPayload, {...topFolderOptions, ...designerReaderOptions});
    expect(requiredNotAllowedResp['body']['success']).toBeFalsy();
    expect(requiredNotAllowedResp['body']['exception']).toBe("'" + parentDataType + "' cannot be required as a parent type when there are existing " + (isChildSample ? 'samples' : 'data') + " without a parent of this type.");
    await verifyRequiredLineageReference(server, parentDtaTypeRowId, isParentSample, topFolderOptions, readerUserOptions);

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

    // verify required lineage can now be added with all existing data have lineage (excluding aliquots)
    await server.post('property', 'saveDomain', updateDomainPayload, {...topFolderOptions, ...designerReaderOptions}).expect(successfulResponse);
    const reference = [dataType];
    await verifyRequiredLineageReference(server, parentDtaTypeRowId, isParentSample, topFolderOptions, readerUserOptions, isChildSample ? reference : [], isChildSample ? [] : reference);

    // verify creating new data using insert now requires parent lineage
    await insertRowsExpectError(server, [{'name': 'CData3'}], dataSchema, dataType, 'Data does not contain required field: ' + parentInput, topFolderOptions, editorUserOptions);
    await insertRowsExpectError(server, [{'name': 'CData3', [parentInput]: ''}], dataSchema, dataType, 'Missing value for required property: ' + parentInput, topFolderOptions, editorUserOptions);
    await insertRowsExpectError(server, [{'name': 'CData3', ['pAlias']: ''}], dataSchema, dataType, 'Missing value for required property: pAlias', topFolderOptions, editorUserOptions);

    // verify creating new data using import/merge now requires parent lineage
    let failedImportResp = await ExperimentCRUDUtils.importData(server, 'name\nCData3', dataType, 'IMPORT', topFolderOptions, editorUserOptions, false, false, isChildSample, true);
    expect(JSON.parse(failedImportResp.text).exception).toBe('Data does not contain required field: ' + parentInput);
    failedImportResp = await ExperimentCRUDUtils.importData(server, 'AliquotedFrom\nCData3', dataType, 'IMPORT', topFolderOptions, editorUserOptions, false, false, isChildSample, true);
    expect(JSON.parse(failedImportResp.text).exception).toBe('Data does not contain required field: ' + parentInput);
    failedImportResp = await ExperimentCRUDUtils.importData(server, 'name\t' + parentInput + '\nCData3\t', dataType, 'IMPORT', topFolderOptions, editorUserOptions, false, false, isChildSample, true);
    expect(JSON.parse(failedImportResp.text).exception).toBe('Missing value for required property: ' + parentInput);
    failedImportResp = await ExperimentCRUDUtils.importData(server, 'name\t' + parentInput + '\nCData3\tbadparentname', dataType, 'IMPORT', topFolderOptions, editorUserOptions, false, false, isChildSample, true);
    expect(JSON.parse(failedImportResp.text).exception).toContain("'badparentname' not found in");
    failedImportResp = await ExperimentCRUDUtils.importData(server, 'name\tpAlias\nCData3\t', dataType, 'IMPORT', topFolderOptions, editorUserOptions, false, false, isChildSample, true);
    expect(JSON.parse(failedImportResp.text).exception).toBe('Missing value for required property: pAlias');
    failedImportResp = await ExperimentCRUDUtils.importData(server, 'name\nCData3', dataType, 'MERGE', topFolderOptions, editorUserOptions, false, false, isChildSample, true);
    expect(JSON.parse(failedImportResp.text).exception).toBe('Data does not contain required field: ' + parentInput);

    // verify cannot remove existing data's required lineage using update
    await updateRowsExpectError(server, [{'rowId': homeDataRowId, [parentInput]: ''}], dataSchema, dataType, 'Missing value for required property: ' + parentInput, topFolderOptions, editorUserOptions);
    // TODO: parent alias doesn't work for query.update api when used with rowId
    await updateRowsExpectError(server, [{'rowId': sub1DataRowId, [parentInput]: ''}], dataSchema, dataType, 'Missing value for required property: ' + parentInput, subfolder1Options, editorUserOptions);
    failedImportResp = await ExperimentCRUDUtils.importData(server, 'name\t' + parentInput + '\nCData1\t', dataType, 'UPDATE', topFolderOptions, editorUserOptions, false, false, isChildSample, true);
    expect(JSON.parse(failedImportResp.text).exception).toBe('Missing value for required property: ' + parentInput);
    failedImportResp = await ExperimentCRUDUtils.importData(server, 'name\tpAlias\nCData1\t', dataType, 'UPDATE', topFolderOptions, editorUserOptions, false, false, isChildSample, true);
    expect(JSON.parse(failedImportResp.text).exception).toBe('Missing value for required property: pAlias');
    failedImportResp = await ExperimentCRUDUtils.importData(server, 'name\nCData1', dataType, 'MERGE', topFolderOptions, editorUserOptions, false, false, isChildSample, true);
    expect(JSON.parse(failedImportResp.text).exception).toBe('Data does not contain required field: ' + parentInput);
    failedImportResp = await ExperimentCRUDUtils.importData(server, 'name\tpAlias\nCData1\t', dataType, 'MERGE', topFolderOptions, editorUserOptions, false, false, isChildSample, true);
    expect(JSON.parse(failedImportResp.text).exception).toBe('Missing value for required property: pAlias');

    // verify update (api, from file) is successful when required parent column is not included
    const selfInput = (isChildSample ? 'materialInputs/' : 'dataInputs/') + dataType;
    await ExperimentCRUDUtils.updateRows(server, [{'rowId': homeDataRowId, [selfInput]: ''}], dataSchema, dataType, topFolderOptions, editorUserOptions);
    await ExperimentCRUDUtils.updateRows(server, [{'rowId': sub1DataRowId, 'description': 'updated!'}], dataSchema, dataType, subfolder1Options, editorUserOptions);
    let successResp = await ExperimentCRUDUtils.importData(server, 'name\t' + selfInput + '\nCData1\t', dataType, 'UPDATE', topFolderOptions, editorUserOptions, false, false, isChildSample);
    let successImport = JSON.parse(successResp.text);
    expect(successImport.success).toBeTruthy();
    successResp = await ExperimentCRUDUtils.importData(server, 'name\tdescription\nCData1\tupdated', dataType, 'UPDATE', topFolderOptions, editorUserOptions, false, false, isChildSample);
    expect(JSON.parse(successResp.text).success).toBeTruthy();
    if (isChildSample) {
        // verify aliquot update
        await ExperimentCRUDUtils.updateRows(server, [{'rowId': aliquotRowId, 'description': 'aliquotupdated!'}], dataSchema, dataType, topFolderOptions, editorUserOptions);
        successResp = await ExperimentCRUDUtils.importData(server, 'name\tdescription\nCData1-1\taliquotupdatedimport', dataType, 'UPDATE', topFolderOptions, editorUserOptions, false, false, isChildSample);
        expect(JSON.parse(successResp.text).success).toBeTruthy();
    }

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

    // verify can create aliquot if parent column is present but blank
    if (isChildSample)
    {
        // verify aliquot creation
        await ExperimentCRUDUtils.insertRows(server, [{'AliquotedFrom': 'CData1', [parentInput]: ''}], dataSchema, dataType, topFolderOptions, editorUserOptions);
        successResp = await ExperimentCRUDUtils.importData(server, parentInput + '\tAliquotedFrom\n\tCData1', dataType, 'IMPORT', topFolderOptions, editorUserOptions, false, false, isChildSample);
        expect(JSON.parse(successResp.text).success).toBeTruthy();
        successResp = await ExperimentCRUDUtils.importData(server, 'AliquotedFrom\tpAlias\nCData1\t', dataType, 'MERGE', topFolderOptions, editorUserOptions, false, false, isChildSample);
        expect(JSON.parse(successResp.text).success).toBeTruthy();
    }

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

    // Issue 51717: When viewing the exp.Data table, both Inputs/Data/First and Outputs/Data/First show the same values
    if (!isParentSample && !isChildSample) {
        const columns = 'Name, Inputs/Data/First/Name, Outputs/Data/First/Name';
        let inputOutputs = await getSourceDataByName(server, 'PDataHome', parentDataType, columns, topFolderOptions, editorUserOptions);
        expect(caseInsensitive(inputOutputs, 'Inputs/Data/First/Name')).toHaveLength(0);
        expect(caseInsensitive(inputOutputs, 'Outputs/Data/First/Name')).toHaveLength(5);
        inputOutputs = await getSourceDataByName(server, 'PDataC1', parentDataType, columns, subfolder1Options, editorUserOptions);
        expect(caseInsensitive(inputOutputs, 'Inputs/Data/First/Name')).toHaveLength(0);
        expect(caseInsensitive(inputOutputs, 'Outputs/Data/First/Name')).toHaveLength(1);
        inputOutputs = await getSourceDataByName(server, 'CData1', dataType, columns, topFolderOptions, editorUserOptions);
        expect(caseInsensitive(inputOutputs, 'Inputs/Data/First/Name')).toHaveLength(2);
        expect(caseInsensitive(inputOutputs, 'Outputs/Data/First/Name')).toHaveLength(0);
        inputOutputs = await getSourceDataByName(server, 'CData2', dataType, columns, subfolder1Options, editorUserOptions);
        expect(caseInsensitive(inputOutputs, 'Inputs/Data/First/Name')).toEqual(['PDataC2']);
        expect(caseInsensitive(inputOutputs, 'Outputs/Data/First/Name')).toHaveLength(0);
    }
    if (!isParentSample && isChildSample) {
        const columns = 'Name, Inputs/Data/First/Name, Outputs/Data/First/Name, Inputs/Materials/First/Name, Outputs/Materials/First/Name';
        let inputOutputs = await getSourceDataByName(server, 'PDataHome', parentDataType, columns, topFolderOptions, editorUserOptions);
        expect(caseInsensitive(inputOutputs, 'Inputs/Data/First/Name')).toHaveLength(0);
        expect(caseInsensitive(inputOutputs, 'Outputs/Data/First/Name')).toHaveLength(0);
        expect(caseInsensitive(inputOutputs, 'Inputs/Materials/First/Name')).toHaveLength(0);
        expect(caseInsensitive(inputOutputs, 'Outputs/Materials/First/Name')).toHaveLength(5);
        inputOutputs = await getSourceDataByName(server, 'PDataC1', parentDataType, columns, subfolder1Options, editorUserOptions);
        expect(caseInsensitive(inputOutputs, 'Inputs/Data/First/Name')).toHaveLength(0);
        expect(caseInsensitive(inputOutputs, 'Outputs/Data/First/Name')).toHaveLength(0);
        expect(caseInsensitive(inputOutputs, 'Inputs/Materials/First/Name')).toHaveLength(0);
        expect(caseInsensitive(inputOutputs, 'Outputs/Materials/First/Name')).toHaveLength(1);
        inputOutputs = await ExperimentCRUDUtils.getSampleDataByName(server, 'CData1', dataType, columns, topFolderOptions, editorUserOptions);
        expect(caseInsensitive(inputOutputs, 'Inputs/Data/First/Name')).toHaveLength(2);
        expect(caseInsensitive(inputOutputs, 'Outputs/Data/First/Name')).toHaveLength(0);
        expect(caseInsensitive(inputOutputs, 'Inputs/Materials/First/Name')).toHaveLength(0);
        expect(caseInsensitive(inputOutputs, 'Outputs/Materials/First/Name')).toHaveLength(5);
        inputOutputs = await ExperimentCRUDUtils.getSampleDataByName(server, 'CData2', dataType, columns, subfolder1Options, editorUserOptions);
        expect(caseInsensitive(inputOutputs, 'Inputs/Data/First/Name')).toEqual(['PDataC2']);
        expect(caseInsensitive(inputOutputs, 'Outputs/Data/First/Name')).toHaveLength(0);
    }
    if (isParentSample && isChildSample) {
        const columns = 'Name, Inputs/Data/First/Name, Outputs/Data/First/Name, Inputs/Materials/First/Name, Outputs/Materials/First/Name';
        let inputOutputs = await ExperimentCRUDUtils.getSampleDataByName(server, 'PDataHome', parentDataType, columns, topFolderOptions, editorUserOptions);
        expect(caseInsensitive(inputOutputs, 'Inputs/Data/First/Name')).toHaveLength(0);
        expect(caseInsensitive(inputOutputs, 'Outputs/Data/First/Name')).toHaveLength(0);
        expect(caseInsensitive(inputOutputs, 'Inputs/Materials/First/Name')).toHaveLength(0);
        expect(caseInsensitive(inputOutputs, 'Outputs/Materials/First/Name')).toHaveLength(5);
        inputOutputs = await ExperimentCRUDUtils.getSampleDataByName(server, 'PDataC1', parentDataType, columns, subfolder1Options, editorUserOptions);
        expect(caseInsensitive(inputOutputs, 'Inputs/Data/First/Name')).toHaveLength(0);
        expect(caseInsensitive(inputOutputs, 'Outputs/Data/First/Name')).toHaveLength(0);
        expect(caseInsensitive(inputOutputs, 'Inputs/Materials/First/Name')).toHaveLength(0);
        expect(caseInsensitive(inputOutputs, 'Outputs/Materials/First/Name')).toHaveLength(1);
        inputOutputs = await ExperimentCRUDUtils.getSampleDataByName(server, 'CData1', dataType, columns, topFolderOptions, editorUserOptions);
        expect(caseInsensitive(inputOutputs, 'Inputs/Data/First/Name')).toHaveLength(0);
        expect(caseInsensitive(inputOutputs, 'Outputs/Data/First/Name')).toHaveLength(0);
        expect(caseInsensitive(inputOutputs, 'Inputs/Materials/First/Name')).toHaveLength(2);
        expect(caseInsensitive(inputOutputs, 'Outputs/Materials/First/Name')).toHaveLength(5);
        inputOutputs = await ExperimentCRUDUtils.getSampleDataByName(server, 'CData2', dataType, columns, subfolder1Options, editorUserOptions);
        expect(caseInsensitive(inputOutputs, 'Inputs/Materials/First/Name')).toEqual(['PDataC2']);
        expect(caseInsensitive(inputOutputs, 'Outputs/Materials/First/Name')).toHaveLength(0);
    }
    // Issue 51410: LKSM: A deleted required source type still shows up in the sample type grids
    await deleteDataType(server, isParentSample ? 'deleteSampleTypes' : 'deleteDataClass', parentDtaTypeRowId, topFolderOptions, adminUserOptions);
    await ExperimentCRUDUtils.insertRows(server, [{'name': 'CNoParent'}], dataSchema, dataType, topFolderOptions, editorUserOptions);
}

export const getAssayDesignPayload = (
    name: string,
    batchFields: Partial<IDomainField>[] = [],
    runFields: Partial<IDomainField>[] = [],
    resultFields: Partial<IDomainField>[] = []
) => {
    return {
        allowEditableResults: true,
        editableResults: true,
        editableRuns: true,
        domains: [
            {
                domainId: undefined,
                domainURI: 'urn:lsid:${LSIDAuthority}:AssayDomain-Batch.Folder-${Container.RowId}:${GpatAssayDBSeq}',
                fields: batchFields,
                name: 'Batch Fields',
            },
            {
                domainId: undefined,
                domainURI: 'urn:lsid:${LSIDAuthority}:AssayDomain-Run.Folder-${Container.RowId}:${GpatAssayDBSeq}',
                fields: runFields,
                name: 'Run Fields',
            },
            {
                domainId: undefined,
                domainURI: 'urn:lsid:${LSIDAuthority}:AssayDomain-Data.Folder-${Container.RowId}:${GpatAssayDBSeq}',
                fields: resultFields,
                name: 'Data Fields',
            },
        ],
        name: name,
        protocolId: null,
        providerName: 'General',
        status: 'Active',
    };
};

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

export function canNameBeUsedInImport(name: string) {
    return name.indexOf(',') === -1 && name.indexOf('\t') === -1 && name.indexOf('"') === -1 && name.indexOf('\n') === -1;
}

export function generateFieldNameForImport(length: number = 10, charset?: string) {
    let fieldName = generateFieldName(length, charset);
    while (!canNameBeUsedInImport(fieldName))
    {
        fieldName = generateFieldName(length, charset);
    }
    return fieldName;
}