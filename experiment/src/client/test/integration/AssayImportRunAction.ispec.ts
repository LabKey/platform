import { hookServer, IntegrationTestServer, RequestOptions } from '@labkey/test';
import mock from 'mock-fs';

import {
    AssayDesignFieldOptions,
    createAssayDesign,
    createDomainField,
    getAuditLogsForTransaction,
    getRunQueryRow,
    ImportRunOptions,
    importRunToServer,
    initProject,
} from './utils';
import { ASSAY_DESIGNER_ROLE, caseInsensitive, EXPERIMENT_AUDIT_EVENT, RANGE_URIS, Row } from '@labkey/components';

// @ts-expect-error process is not available in a browser environment
const server = hookServer(process.env);
const PROJECT_NAME = 'ArrayImportRunActionTest Project';
const BATCH_FILE_FIELD_NAME = 'batchFileField';
const BATCH_FILE_FIELD_TWO_NAME = 'batchFile2Field';
const RUN_FILE_FIELD_NAME = 'runFileField';
const RUN_FILE_FIELD_TWO_NAME = 'runFile2Field';
const RESULT_FIELD_NAME = 'resultStringField';
const RESULT_FILE_FIELD_NAME = 'resultFileField';
let context;
let ASSAY_A_ID: number;
const ASSAY_A_NAME = 'AssayA';

beforeAll(async () => {
    context = await initProject(server, PROJECT_NAME, ASSAY_DESIGNER_ROLE, ['assay', 'experiment']);

    const assayFields: AssayDesignFieldOptions = {
        batchFields: [
            createDomainField({ name: BATCH_FILE_FIELD_NAME, rangeURI: RANGE_URIS.FILELINK }),
            createDomainField({ name: BATCH_FILE_FIELD_TWO_NAME, rangeURI: RANGE_URIS.FILELINK }),
        ],
        runFields: [
            createDomainField({ name: RUN_FILE_FIELD_NAME, rangeURI: RANGE_URIS.FILELINK }),
            createDomainField({ name: RUN_FILE_FIELD_TWO_NAME, rangeURI: RANGE_URIS.FILELINK }),
        ],
        resultFields: [
            createDomainField({ name: RESULT_FIELD_NAME }),
            createDomainField({ name: RESULT_FILE_FIELD_NAME, rangeURI: RANGE_URIS.FILELINK }),
        ],
    };
    const assayA = await createAssayDesign(server, ASSAY_A_NAME, assayFields, context.topFolderOptions);
    ASSAY_A_ID = assayA.protocolId;
});

afterAll(async () => {
    return server.teardown();
});

afterEach(() => {
    mock.restore();
});

/**
 * Verify audit event row matches expected values. Note: This function is case-insensitive for field names.
 */
function verifyFileSystemAuditEvent(events: Row[], fieldName: string, expectedValues: Record<string, any>) {
    const auditEvent = events.find(
        event => caseInsensitive(event.data, 'FieldName')?.value.toLowerCase() === fieldName.toLowerCase()
    );
    expect(auditEvent).toBeDefined();
    const eventValues = Object.entries(auditEvent.data).reduce((acc, event) => {
        const [key, value] = event;
        acc[key.toLowerCase()] = value.value;
        return acc;
    }, {});
    const lowerExpectedValues = Object.entries(expectedValues).reduce((acc, pair) => {
        const [key, value] = pair;
        acc[key.toLowerCase()] = value;
        return acc;
    }, {});
    expect(eventValues).toEqual(expect.objectContaining(lowerExpectedValues));
}

/**
 * Verify that the specified files exist or do not exist in the assay data directory on the server.
 */
async function verifyPropertiesFilesOnServer(
    server: IntegrationTestServer,
    fileNames: string[],
    exists: boolean,
    requestOptions?: RequestOptions
): Promise<void> {
    const response = await server.request(
        '_webdav',
        '%40files/assaydata',
        (agent, url) => agent.get(url.replace('.view', '') + '?method=JSON'),
        requestOptions
    );

    if (exists) {
        for (const fileName of fileNames) {
            expect(response.body.files).toContainEqual(expect.objectContaining({ text: fileName }));
        }
    } else {
        for (const fileName of fileNames) {
            expect(response.body.files).not.toContainEqual(expect.objectContaining({ text: fileName }));
        }
    }
}

describe('assay-importRun.api', () => {
    it('requires POST', () => {
        server.get('assay', 'importRun.api').expect(405);
    });
    it('errors with empty payload', async () => {
        const response = await server.post('assay', 'importRun.api');
        expect(response.body.exception).toEqual('assayId parameter required');
    });
    it('errors with invalid "assayId" parameter', async () => {
        let response = await server.post('assay', 'importRun.api', { assayId: -1 });
        expect(response.body.exception).toEqual('assayId or both protocolName and providerName required');

        const invalidAssayId = 1234567890;
        response = await server.post('assay', 'importRun.api', { assayId: invalidAssayId });
        expect(response.body.exception).toEqual(`Could not find assay id ${invalidAssayId}`);
    });
    it('errors with invalid "assayName" or "providerName" parameter', async () => {
        let response = await server.post('assay', 'importRun.api', { assayName: null });
        expect(response.body.exception).toEqual('assayId or both protocolName and providerName required');

        response = await server.post('assay', 'importRun.api', { assayName: 'some assay', providerName: null });
        expect(response.body.exception).toEqual('assayId or both protocolName and providerName required');

        const invalidProviderName = 'invalid provider name';
        response = await server.post('assay', 'importRun.api', {
            assayName: 'some assay',
            providerName: invalidProviderName,
        });
        expect(response.body.exception).toEqual(`Assay provider '${invalidProviderName}' not found`);
    });
    it('requires data to create a run', async () => {
        let response = await server.post('assay', 'importRun.api', { assayId: ASSAY_A_ID });
        expect(response.body.exception).toEqual('No data file was uploaded. Please select a file.');

        response = await server.post('assay', 'importRun.api', { assayId: ASSAY_A_ID, dataRows: [] });
        expect(response.body.exception).toEqual('No data file was uploaded. Please select a file.');

        response = await server.post('assay', 'importRun.api', { assayId: ASSAY_A_ID, dataRows: [] });
        expect(response.body.exception).toEqual('No data file was uploaded. Please select a file.');
    });
    it('successfully imports a minimally specified run', async () => {
        const payload: ImportRunOptions = { assayId: ASSAY_A_ID, dataRows: [{ [RESULT_FIELD_NAME]: 'beep' }] };
        const response = await importRunToServer(server, payload, context.topFolderOptions);
        expect(response.body.success).toEqual(true);
        expect(response.body.batchId).toBeGreaterThan(0);
        expect(response.body.runId).toBeGreaterThan(0);
        expect(response.body.assayId).toEqual(ASSAY_A_ID);
    });

    describe('file fields', () => {
        it('errors with invalid file field name', async () => {
            // Arrange
            const batchFileName = 'batchFileError.txt';
            const batchFilePath = `file://path/${batchFileName}`;
            const runFileName = 'runFileError.txt';
            const runFilePath = `file://path/${runFileName}`;
            mock({ [batchFilePath]: 'Batch McBatch', [runFilePath]: 'Run McRun' });

            const invalidBatchFieldName = 'invalid batch field name';
            const invalidRunFieldName = 'invalid run field name';
            const { editorUserOptions, topFolderOptions } = context;

            // Act
            // Fail to resolve the batch property
            {
                const payload: ImportRunOptions = {
                    assayId: ASSAY_A_ID,
                    batchProperties: { [invalidBatchFieldName]: batchFilePath },
                    dataRows: [{ [RESULT_FIELD_NAME]: 'beep' }],
                    properties: { [invalidRunFieldName]: runFilePath },
                };
                const response = await importRunToServer(server, payload, topFolderOptions, editorUserOptions);

                // Assert
                expect(response.body.success).toEqual(false);

                const { errors } = response.body;
                expect(errors).toHaveLength(1);
                expect(errors[0].message).toEqual('Failed to resolve batch property');
                expect(errors[0].field).toEqual(invalidBatchFieldName);

                await verifyPropertiesFilesOnServer(server, [batchFileName, runFileName], false, topFolderOptions);
            }

            // Act
            // Fail to resolve the run property
            {
                const payload: ImportRunOptions = {
                    assayId: ASSAY_A_ID,
                    dataRows: [{ [RESULT_FIELD_NAME]: 'beep' }],
                    properties: { [invalidRunFieldName]: runFilePath },
                };
                const response = await importRunToServer(server, payload, topFolderOptions, editorUserOptions);

                // Assert
                expect(response.body.success).toEqual(false);

                const { errors } = response.body;
                expect(errors).toHaveLength(1);
                expect(errors[0].message).toEqual('Failed to resolve run property');
                expect(errors[0].field).toEqual(invalidRunFieldName);

                await verifyPropertiesFilesOnServer(server, [runFileName], false, topFolderOptions);
            }
        });
        it('successfully imports with batch/run file fields', async () => {
            // Arrange
            const batchFileName = 'batchFileA.txt';
            const batchFilePath = `file://path/${batchFileName}`;
            const batchFileName2 = 'batchFileB.txt';
            const batchFilePath2 = `file://path/${batchFileName2}`;
            const runFileName = 'runFileA.txt';
            const runFilePath = `file://path/${runFileName}`;
            const runFileName2 = 'runFileB.txt';
            const runFilePath2 = `file://path/${runFileName2}`;
            mock({
                [batchFilePath]: 'Batch McBatch',
                [runFilePath]: 'Run McRun',
                [batchFilePath2]: 'Run McBatch',
                [runFilePath2]: 'Batch McRun',
            });

            const payload: ImportRunOptions = {
                assayId: ASSAY_A_ID,
                batchProperties: {
                    [BATCH_FILE_FIELD_NAME]: batchFilePath,
                    [BATCH_FILE_FIELD_TWO_NAME]: batchFilePath2,
                },
                dataRows: [{ [RESULT_FIELD_NAME]: 'beep' }],
                properties: { [RUN_FILE_FIELD_NAME]: runFilePath, [RUN_FILE_FIELD_TWO_NAME]: runFilePath2 },
            };

            // Act
            const { editorUserOptions, topFolderOptions } = context;
            const response = await importRunToServer(server, payload, topFolderOptions, editorUserOptions);

            // Assert
            expect(response.body.success).toEqual(true);
            const { auditTransactionId, runId } = response.body;

            // Verify files are available as expected
            const run = await getRunQueryRow(server, ASSAY_A_NAME, runId, topFolderOptions);
            const expectedUrl = `/${encodeURIComponent(PROJECT_NAME)}/core-downloadFileLink.view?propertyId=`;
            const runBatchField = `Batch/${BATCH_FILE_FIELD_NAME}`;
            expect(run[runBatchField].value).toEqual(`assaydata/${batchFileName}`);
            expect(run[runBatchField].url).toContain(expectedUrl);
            expect(run[RUN_FILE_FIELD_NAME].value).toEqual(`assaydata/${runFileName}`);
            expect(run[RUN_FILE_FIELD_NAME].url).toContain(expectedUrl);

            // Verify audit log
            expect(auditTransactionId).toBeGreaterThan(0);
            let auditLogs = await getAuditLogsForTransaction(
                server,
                auditTransactionId,
                EXPERIMENT_AUDIT_EVENT,
                topFolderOptions
            );
            expect(auditLogs.length).toBe(2);
            auditLogs = await getAuditLogsForTransaction(server, auditTransactionId, 'filesystem', topFolderOptions);
            expect(auditLogs.length).toBe(4);
            verifyFileSystemAuditEvent(auditLogs, BATCH_FILE_FIELD_NAME, {
                comment: 'Assay batch property file uploaded.',
                createdBy: context.editorUser.userId,
                file: batchFileName,
                providedFileName: batchFileName,
                transactionId: auditTransactionId,
            });
            verifyFileSystemAuditEvent(auditLogs, RUN_FILE_FIELD_NAME, {
                comment: 'Assay run property file uploaded.',
                createdBy: context.editorUser.userId,
                file: runFileName,
                providedFileName: runFileName,
                transactionId: auditTransactionId,
            });
            verifyFileSystemAuditEvent(auditLogs, BATCH_FILE_FIELD_TWO_NAME, {
                comment: 'Assay batch property file uploaded.',
                createdBy: context.editorUser.userId,
                file: batchFileName2,
                providedFileName: batchFileName2,
                transactionId: auditTransactionId,
            });
            verifyFileSystemAuditEvent(auditLogs, RUN_FILE_FIELD_TWO_NAME, {
                comment: 'Assay run property file uploaded.',
                createdBy: context.editorUser.userId,
                file: runFileName2,
                providedFileName: runFileName2,
                transactionId: auditTransactionId,
            });

            const expectedFiles = [batchFileName, batchFileName2, runFileName, runFileName2];
            verifyPropertiesFilesOnServer(server, expectedFiles, true, topFolderOptions);
        });
        it('successfully imports, fails to reimport, and retains files', async () => {
            // Arrange
            const batchFileName = 'batchFileAA.txt';
            const batchFilePath = `file://path/${batchFileName}`;
            const runFileName = 'runFileAA.txt';
            const runFilePath = `file://path/${runFileName}`;
            const { editorUserOptions, topFolderOptions } = context;
            let runId: number;

            // Successfully import a run with file properties
            {
                mock({ [batchFilePath]: 'Batch McBatch', [runFilePath]: 'Run McRun' });
                const payload: ImportRunOptions = {
                    assayId: ASSAY_A_ID,
                    batchProperties: { [BATCH_FILE_FIELD_NAME]: batchFilePath },
                    dataRows: [{ [RESULT_FIELD_NAME]: 'beep' }],
                    properties: { [RUN_FILE_FIELD_NAME]: runFilePath },
                };

                const response = await importRunToServer(server, payload, topFolderOptions, editorUserOptions);
                expect(response.body.success).toEqual(true);
                expect(response.body.runId).toBeGreaterThan(0);
                await verifyPropertiesFilesOnServer(server, [batchFileName, runFileName], true, topFolderOptions);
                runId = response.body.runId;
            }

            // Act
            // Fail to reimport the run, supplying new files
            {
                const riBatchFileName = 'reimportBatchFileAA.txt';
                const riBatchFilePath = `file://path/${riBatchFileName}`;
                const riRunFileName = 'reimportRunFileAA.txt';
                const riRunFilePath = `file://path/${riRunFileName}`;
                mock({
                    [batchFilePath]: 'Batch McBatch',
                    [runFilePath]: 'Run McRun',
                    [riBatchFilePath]: 'Reimport Batch McBatch',
                    [riRunFilePath]: 'Reimport Run McRun',
                });

                const invalidRunPropName = 'invalidRunProp';
                const payload: ImportRunOptions = {
                    assayId: ASSAY_A_ID,
                    batchProperties: {
                        [BATCH_FILE_FIELD_NAME]: riBatchFilePath,
                        [BATCH_FILE_FIELD_TWO_NAME]: batchFilePath,
                    },
                    dataRows: [{ [RESULT_FIELD_NAME]: 'beep' }],
                    properties: {
                        [RUN_FILE_FIELD_NAME]: riRunFilePath,
                        [RUN_FILE_FIELD_TWO_NAME]: runFilePath,
                        [invalidRunPropName]: 'Beep boop clang',
                    },
                    reRunId: runId,
                };
                const response = await importRunToServer(server, payload, topFolderOptions, editorUserOptions);

                // Assert
                expect(response.body.success).toEqual(false);
                await verifyPropertiesFilesOnServer(server, [batchFileName, runFileName], true, topFolderOptions);
                await verifyPropertiesFilesOnServer(server, [riBatchFileName, riRunFileName], false, topFolderOptions);
            }
        });
    });
});
