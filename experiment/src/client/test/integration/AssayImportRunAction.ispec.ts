/*
 * Copyright (c) 2025-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import { hookServer, IntegrationTestServer, RequestOptions, successfulResponse, testSeed } from '@labkey/test';
import mock from 'mock-fs';

import {
    AssayDesignFieldOptions,
    createAssayDesign,
    createDomainField,
    generateFieldNameForImport,
    getAuditLogsForTransaction,
    getRunQueryRow,
    ImportRunOptions,
    importRunToServer,
    initProject,
    MVTC_FIELD_PROP,
    options,
    TC_FIELD_PROP,
} from './utils';
import {
    ASSAY_DESIGNER_ROLE,
    caseInsensitive,
    EXPERIMENT_AUDIT_EVENT,
    IDomainField,
    RANGE_URIS,
    Row
} from '@labkey/components';

// @ts-expect-error process is not available in a browser environment
const server = hookServer(process.env);
const PROJECT_NAME = 'ArrayImportRunActionTest Project';

console.log(`[ArrayImportRunAction] Random seed: ${testSeed}  (rerun with: TEST_SEED=${testSeed})`);

const BATCH_FILE_FIELD_NAME = 'batchFileField';
const BATCH_FILE_FIELD_TWO_NAME = 'batchFile2Field';
const RUN_FILE_FIELD_NAME = 'runFileField';
const RUN_FILE_FIELD_TWO_NAME = 'runFile2Field';
const RUN_TEXT_CHOICE_FIELD_NAME = 'runTextChoiceField';
const RESULT_FIELD_NAME = 'resultStringField';
const RESULT_FILE_FIELD_NAME = 'resultFileField';
const RESULT_TC_FIELD_NAME = 'resultTextChoiceField';
const RESULT_MVTC_FIELD_NAME = 'resultMC ' + generateFieldNameForImport();

let context;
let ASSAY_A_ID: number;
let ASSAY_A_DESIGN: any;
const ASSAY_A_NAME = 'AssayA';

let supportMultiChoice = false;

beforeAll(async () => {
    context = await initProject(server, PROJECT_NAME, ASSAY_DESIGNER_ROLE, ['assay', 'experiment']);

    const createTestPayload = {
        kind: 'DataClass',
        domainDesign: { name: 'Test_mvtc_support_check', fields: [{ name: 'Prop' }] },
        options: {
            name: "Test_mvtc_support_check",
        }
    };

    await server.post('property', 'createDomain', createTestPayload).expect((result) => {
        const domain = JSON.parse(result.text);
        supportMultiChoice = domain.allowMultiChoiceProperties;
        return true;
    });

    let resultFields = [
        createDomainField({ name: RESULT_FIELD_NAME }),
        createDomainField({ name: RESULT_FILE_FIELD_NAME, rangeURI: RANGE_URIS.FILELINK }),
        createDomainField({name: RESULT_TC_FIELD_NAME, ...TC_FIELD_PROP} as Partial<IDomainField>)
    ];

    if (supportMultiChoice) {
        console.log("Assay result MVTC field name: " + RESULT_MVTC_FIELD_NAME);
        resultFields.push(createDomainField({name: RESULT_MVTC_FIELD_NAME, ...MVTC_FIELD_PROP} as Partial<IDomainField>));
    }

    let assayFields: AssayDesignFieldOptions = {
        batchFields: [
            createDomainField({ name: BATCH_FILE_FIELD_NAME, rangeURI: RANGE_URIS.FILELINK }),
            createDomainField({ name: BATCH_FILE_FIELD_TWO_NAME, rangeURI: RANGE_URIS.FILELINK }),
        ],
        runFields: [
            createDomainField({ name: RUN_FILE_FIELD_NAME, rangeURI: RANGE_URIS.FILELINK }),
            createDomainField({ name: RUN_FILE_FIELD_TWO_NAME, rangeURI: RANGE_URIS.FILELINK }),
            createDomainField({name: RUN_TEXT_CHOICE_FIELD_NAME, ...TC_FIELD_PROP} as Partial<IDomainField>)
        ],
        resultFields,
    };

    const assayA = await createAssayDesign(server, ASSAY_A_NAME, assayFields, context.topFolderOptions);
    ASSAY_A_ID = assayA.protocolId;
    ASSAY_A_DESIGN = assayA;
});

afterAll(async () => {
    return server.teardown();
});

afterEach(() => {
    mock.restore();
});

function fileNameWithIncrement(fileName: string, increment: number): string {
    const lastDotIdx = fileName.lastIndexOf('.');
    if (lastDotIdx > 0) {
        return `${fileName.substring(0, lastDotIdx)}-${increment}${fileName.substring(lastDotIdx)}`;
    }

    return `${fileName}-${increment}`;
}

function verifyAuditEvent(event: Row, expectedValues: Record<string, any>): void {
    const eventValues = Object.entries(event.data).reduce((acc, event) => {
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

function verifyExperimentAuditEvent(events: Row[], comment: string, expectedValues: Record<string, any>): void {
    const auditEvent = events.find(
        event => caseInsensitive(event.data, 'Comment')?.value.toLowerCase() === comment.toLowerCase()
    );
    if (!auditEvent) {
        throw new Error(`Experiment audit event with comment '${comment}' not found`);
    }
    verifyAuditEvent(auditEvent, expectedValues);
}

/**
 * Verify audit event row matches expected values. Note: This function is case-insensitive for field names.
 */
function verifyFileSystemAuditEvent(events: Row[], fieldName: string, expectedValues: Record<string, any>): void {
    const auditEvent = events.find(
        event => caseInsensitive(event.data, 'FieldName')?.value.toLowerCase() === fieldName.toLowerCase()
    );
    if (!auditEvent) {
        throw new Error(`File system audit event with field name '${fieldName}' not found`);
    }
    verifyAuditEvent(auditEvent, expectedValues);
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
        'fakeController',
        'fakeAction',
        (agent, url) => {
            // Note: this is a hack to allow us to make requests to the webdav controller. The IntegrationTestServer
            // request method uses ActionURL to generate URLS, but webdav URLs are not ActionURLs, so we need to
            // override the AgentProvider to generate the proper webdav URL.
            url = `${LABKEY.contextPath}/_webdav/${requestOptions.containerPath}/%40files/assaydata?method=JSON`;
            return agent.get(url);
        },
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
        expect(response.body.exception).toEqual(
            'Either "assayId" or both "protocolName" and "providerName" are required.'
        );

        const invalidAssayId = 1234567890;
        response = await server.post('assay', 'importRun.api', { assayId: invalidAssayId });
        expect(response.body.exception).toEqual(`Could not find assay id ${invalidAssayId}`);
    });
    it('errors with invalid "assayName" or "providerName" parameter', async () => {
        let response = await server.post('assay', 'importRun.api', { assayName: null });
        expect(response.body.exception).toEqual(
            'Either "assayId" or both "protocolName" and "providerName" are required.'
        );

        response = await server.post('assay', 'importRun.api', { assayName: 'some assay', providerName: null });
        expect(response.body.exception).toEqual(
            'Either "assayId" or both "protocolName" and "providerName" are required.'
        );

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
            expect(run[runBatchField].value.replaceAll('\\', '/')).toEqual(`assaydata/${batchFileName}`);
            expect(run[runBatchField].url).toContain(expectedUrl);
            expect(run[RUN_FILE_FIELD_NAME].value.replaceAll('\\', '/')).toEqual(`assaydata/${runFileName}`);
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
            // Upload files with the same name for two different fields. Expect the latter to be renamed.
            const sameFileName = 'sameFileAA.txt';
            const sameFileNameOne = fileNameWithIncrement(sameFileName, 1);
            const sameFileNameTwo = fileNameWithIncrement(sameFileName, 2);
            const sameFileNameThree = fileNameWithIncrement(sameFileName, 3);
            const batchFilePath = `file://path/batch/${sameFileName}`;
            const runFilePath = `file://path/run/${sameFileName}`;
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
                await verifyPropertiesFilesOnServer(server, [sameFileName, sameFileNameOne], true, topFolderOptions);
                runId = response.body.runId;
            }

            // Act
            // Fail to reimport the run, supplying new files
            {
                const riBatchFilePath = `file://path/reimport/batch/${sameFileName}`;
                const riRunFilePath = `file://path/reimport/run/${sameFileName}`;
                mock({
                    [batchFilePath]: 'Batch McBatch',
                    [runFilePath]: 'Run McRun',
                    [riBatchFilePath]: 'Reimport Batch McBatch',
                    [riRunFilePath]: 'Reimport Run McRun',
                });

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
                    },
                    reRunId: runId * 2, // reimport with an invalid runId
                };
                const response = await importRunToServer(server, payload, topFolderOptions, editorUserOptions);

                // Assert
                expect(response.body.success).toEqual(false);
                await verifyPropertiesFilesOnServer(server, [sameFileName, sameFileNameOne], true, topFolderOptions);
                await verifyPropertiesFilesOnServer(
                    server,
                    [sameFileNameTwo, sameFileNameThree],
                    false,
                    topFolderOptions
                );
            }
        });
        it('successfully reimports a run', async () => {
            // Arrange
            // Upload files with the same name for two different fields. Expect the latter to be renamed.
            const batchFileName = 'batchFileAARI.txt';
            const batchFileNameOne = fileNameWithIncrement(batchFileName, 1);
            const batchFileNameTwo = fileNameWithIncrement(batchFileName, 2);
            const runFileName = 'runFileBBRI.txt';
            const runFileNameOne = fileNameWithIncrement(runFileName, 1);
            const runFileNameTwo = fileNameWithIncrement(runFileName, 2);
            const batchFilePath = `file://path/batch/${batchFileName}`;
            const runFilePath = `file://path/run/${runFileName}`;
            const { editorUserOptions, topFolderOptions } = context;
            let runId: number;

            // Successfully import a run with file properties
            {
                mock({ [batchFilePath]: 'Batch McBatch', [runFilePath]: 'Run McRun' });
                const payload: ImportRunOptions = {
                    assayId: ASSAY_A_ID,
                    batchProperties: {
                        [BATCH_FILE_FIELD_NAME]: batchFilePath,
                        [BATCH_FILE_FIELD_TWO_NAME]: batchFilePath,
                    },
                    dataRows: [{ [RESULT_FIELD_NAME]: 'beep' }],
                    properties: {
                        [RUN_FILE_FIELD_NAME]: runFilePath,
                        [RUN_FILE_FIELD_TWO_NAME]: runFilePath,
                    },
                };

                const response = await importRunToServer(server, payload, topFolderOptions, editorUserOptions);
                expect(response.body.success).toEqual(true);
                expect(response.body.runId).toBeGreaterThan(0);
                await verifyPropertiesFilesOnServer(
                    server,
                    [batchFileName, batchFileNameOne, runFileName, runFileNameOne],
                    true,
                    topFolderOptions
                );
                runId = response.body.runId;
            }

            // Act
            // Successfully reimport the run, supplying some new files and some original files
            const riBatchFilePath = `file://path/reimport/batch/${batchFileName}`;
            const riRunFile2Name = 'reimportRunFileBBRI.txt';
            const riRunFile2Path = `file://path/reimport/run/${riRunFile2Name}`;
            mock({
                [riBatchFilePath]: 'Reimport Batch McBatch',
                [riRunFile2Path]: 'Reimport Run McRun 2',
                [runFilePath]: 'Run McRun',
            });

            const payload: ImportRunOptions = {
                assayId: ASSAY_A_ID,
                batchProperties: {
                    [BATCH_FILE_FIELD_TWO_NAME]: riBatchFilePath,
                },
                dataRows: [{ [RESULT_FIELD_NAME]: 'beep' }],
                properties: {
                    [RUN_FILE_FIELD_NAME]: runFilePath,
                    [RUN_FILE_FIELD_TWO_NAME]: riRunFile2Path,
                },
                reRunId: runId,
            };
            const response = await importRunToServer(server, payload, topFolderOptions, editorUserOptions);

            // Assert
            expect(response.body.success).toEqual(true);
            expect(response.body.runId).toBeGreaterThan(0);
            const auditTransactionId = response.body.auditTransactionId;
            const reRunId = response.body.runId;

            // Verify experiment audit events
            expect(auditTransactionId).toBeGreaterThan(0);
            let auditLogs = await getAuditLogsForTransaction(
                server,
                auditTransactionId,
                EXPERIMENT_AUDIT_EVENT,
                topFolderOptions
            );
            expect(auditLogs.length).toBe(3);
            verifyExperimentAuditEvent(auditLogs, `Run id ${runId} was replaced by run id ${reRunId}`, {
                createdBy: context.editorUser.userId,
                transactionId: auditTransactionId,
            });
            verifyExperimentAuditEvent(auditLogs, `${ASSAY_A_NAME} run loaded`, {
                createdBy: context.editorUser.userId,
                transactionId: auditTransactionId,
            });

            // Verify file system audit events
            auditLogs = await getAuditLogsForTransaction(server, auditTransactionId, 'filesystem', topFolderOptions);
            expect(auditLogs.length).toBe(3);
            verifyFileSystemAuditEvent(auditLogs, BATCH_FILE_FIELD_TWO_NAME, {
                comment: 'Assay batch property file uploaded.',
                createdBy: context.editorUser.userId,
                file: batchFileNameTwo,
                providedFileName: batchFileName,
                transactionId: auditTransactionId,
            });

            verifyFileSystemAuditEvent(auditLogs, RUN_FILE_FIELD_NAME, {
                comment: 'Assay run property file uploaded.',
                createdBy: context.editorUser.userId,
                file: runFileNameTwo,
                providedFileName: runFileName,
                transactionId: auditTransactionId,
            });
            verifyFileSystemAuditEvent(auditLogs, RUN_FILE_FIELD_TWO_NAME, {
                comment: 'Assay run property file uploaded.',
                createdBy: context.editorUser.userId,
                file: riRunFile2Name,
                providedFileName: riRunFile2Name,
                transactionId: auditTransactionId,
            });
        });
        it('works in conjunction with assay-assayFileUpload.api', async () => {
            // Arrange
            const batchFileName = 'uploadFileA.txt';
            const batchFilePath = `file://path/${batchFileName}`;
            const runFileName = 'uploadRunFileA.txt';
            const runFilePath = `file://path/${runFileName}`;
            mock({ [batchFilePath]: 'Batch McBatch', [runFilePath]: 'Run McRun' });
            const { editorUserOptions, topFolderOptions } = context;

            let batchFile;
            let runFile;

            // Upload files via assay-assayFileUpload.api so that we can reference those file paths when importing the run
            {
                const response = await server
                    .request(
                        'assay',
                        'assayFileUpload.api',
                        (agent, url) => agent.post(url).attach('file', batchFilePath).attach('file1', runFilePath),
                        options(topFolderOptions, editorUserOptions)
                    )
                    .expect(successfulResponse);

                const data = JSON.parse(response.text);
                expect(data.success).toEqual(true);
                expect(data.file).toBeDefined();
                expect(data.file1).toBeDefined();
                batchFile = data.file;
                runFile = data.file1;
            }

            const payload: ImportRunOptions = {
                assayId: ASSAY_A_ID,
                batchProperties: { [BATCH_FILE_FIELD_TWO_NAME]: batchFile.absolutePath },
                dataRows: [{ [RESULT_FIELD_NAME]: 'bop' }],
                properties: { [RUN_FILE_FIELD_NAME]: runFile.absolutePath },
            };

            // Act
            const response = await importRunToServer(server, payload, topFolderOptions, editorUserOptions);

            // Assert
            expect(response.body.success).toEqual(true);
            const { auditTransactionId, runId } = response.body;
            expect(runId).toBeGreaterThan(0);
            expect(auditTransactionId).toBeGreaterThan(0);

            // Verify files are available as expected
            const run = await getRunQueryRow(server, ASSAY_A_NAME, runId, topFolderOptions);
            const expectedUrl = `/${encodeURIComponent(PROJECT_NAME)}/core-downloadFileLink.view?propertyId=`;
            const runBatchField = `Batch/${BATCH_FILE_FIELD_TWO_NAME}`;
            expect(run[runBatchField].value.replaceAll('\\', '/')).toEqual(`assaydata/${batchFileName}`);
            expect(run[runBatchField].url).toContain(expectedUrl);
            expect(run[RUN_FILE_FIELD_NAME].value.replaceAll('\\', '/')).toEqual(`assaydata/${runFileName}`);
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

            // Verify file system audit events
            auditLogs = await getAuditLogsForTransaction(server, auditTransactionId, 'filesystem', topFolderOptions);
            expect(auditLogs.length).toBe(0);
        });
    });

    describe('text choice fields', () => {
        function getUpdateField(domainFieldFull: any) {
            // only keep the following properties for the update payload since the saveProtocol.api doesn't expect the other properties that come through in the assay design response
            const propertiesToKeep = [
                'name',
                'label',
                'type',
                'propertyValidators',
                'required',
                'mvEnabled',
                'multiValued',
                'propertyId',
                'container',
                'conceptURI',
                'rangeURI',
                'format',
                'propertyURI'
            ];

            return Object.fromEntries(Object.entries(domainFieldFull).filter(([key]) => propertiesToKeep.includes(key)));
        }

        // Build a saveProtocol.api payload that modifies the text choice validator expression
        // for a specific field in the given domain (0=batch, 1=run, 2=data/results).
        function buildAssayUpdatePayload(
            domainIndex: number,
            fieldName: string,
            newExpression: string
        ): any {
            const domains = ASSAY_A_DESIGN.domains.map((domain: any, i: number) => ({
                domainId: domain.domainId,
                domainURI: domain.domainURI,
                name: domain.name,
                fields: domain.fields.map((field: any) => {
                    const f = getUpdateField(field);
                    if (i === domainIndex && f.name === fieldName) {
                        const validators = f.propertyValidators[0] || {};
                        return {
                            ...f,
                            propertyValidators: [{
                                ...validators,
                                type: 'TextChoice',
                                name: 'Text Choice Validator',
                                new: true,
                                expression: newExpression,
                            }],
                        };
                    }
                    return f;
                }),
            }));

            return {
                protocolId: ASSAY_A_DESIGN.protocolId,
                name: ASSAY_A_NAME,
                providerName: 'General',
                allowEditableResults: true,
                editableResults: true,
                editableRuns: true,
                status: 'Active',
                domains,
            };
        }

        // GitHub Issue 949: Text choice value can be deleted if usage is added after loading designer
        it('blocks deleting in-use run text choice value', async () => {
            const { topFolderOptions } = context;

            // Import a run with 'Abnormal' as the run text choice value
            const importPayload: ImportRunOptions = {
                assayId: ASSAY_A_ID,
                properties: { [RUN_TEXT_CHOICE_FIELD_NAME]: 'Abnormal' },
                dataRows: [{ [RESULT_FIELD_NAME]: 'tc-run-test' }],
            };
            const importResponse = await importRunToServer(server, importPayload, topFolderOptions);
            expect(importResponse.body.success).toEqual(true);

            // Try to remove 'Abnormal' from the run TC field's valid values
            const updatePayload = buildAssayUpdatePayload(1, RUN_TEXT_CHOICE_FIELD_NAME, 'agent|cDNA|Plasma');
            const response = await server.post('assay', 'saveProtocol.api', updatePayload, topFolderOptions);
            expect(response.text).toContain('One or more values cannot be removed from the text choice list');
        });

        it('blocks deleting in-use result text choice value', async () => {
            const { topFolderOptions } = context;

            // Import a run with 'agent' as the result text choice value
            const importPayload: ImportRunOptions = {
                assayId: ASSAY_A_ID,
                dataRows: [{ [RESULT_FIELD_NAME]: 'tc-result-test', [RESULT_TC_FIELD_NAME]: 'agent' }],
            };
            const importResponse = await importRunToServer(server, importPayload, topFolderOptions);
            expect(importResponse.body.success).toEqual(true);

            // Try to remove 'agent' from the result TC field's valid values
            const updatePayload = buildAssayUpdatePayload(2, RESULT_TC_FIELD_NAME, 'Abnormal|cDNA|Plasma');
            const response = await server.post('assay', 'saveProtocol.api', updatePayload, topFolderOptions);
            expect(response.text).toContain('One or more values cannot be removed from the text choice list');
        });

        it('blocks deleting in-use multi-choice value used as single value', async () => {
            if (!supportMultiChoice) {
                console.warn('Multi-choice properties are not supported in this environment, skipping multi-choice field tests');
                return;
            }

            const { topFolderOptions } = context;

            // Import a run with 'Plasma' as a single multi-choice value
            const importPayload: ImportRunOptions = {
                assayId: ASSAY_A_ID,
                dataRows: [{ [RESULT_FIELD_NAME]: 'mc-single-test', [RESULT_MVTC_FIELD_NAME]: ['Plasma'] }],
            };
            const importResponse = await importRunToServer(server, importPayload, topFolderOptions);
            expect(importResponse.body.success).toEqual(true);

            // Try to remove 'Plasma' from the multi-choice field's valid values
            const updatePayload = buildAssayUpdatePayload(2, RESULT_MVTC_FIELD_NAME, 'Abnormal|agent|cDNA');
            const response = await server.post('assay', 'saveProtocol.api', updatePayload, topFolderOptions);
            expect(response.text).toContain('One or more values cannot be removed from the multi-choice list');
        });

        it('blocks deleting in-use multi-choice value used as part of an array value', async () => {
            if (!supportMultiChoice) {
                console.warn('Multi-choice properties are not supported in this environment, skipping multi-choice field tests');
                return;
            }

            const { topFolderOptions } = context;

            // Import a run with ['Abnormal', 'cDNA'] as multi-choice values
            const importPayload: ImportRunOptions = {
                assayId: ASSAY_A_ID,
                dataRows: [{ [RESULT_FIELD_NAME]: 'mc-array-test', [RESULT_MVTC_FIELD_NAME]: ['Abnormal', 'cDNA'] }],
            };
            const importResponse = await importRunToServer(server, importPayload, topFolderOptions);
            expect(importResponse.body.success).toEqual(true);

            // Try to remove 'cDNA' (used as part of a multi-value array) from the valid values
            const updatePayload = buildAssayUpdatePayload(2, RESULT_MVTC_FIELD_NAME, 'Abnormal|agent|Plasma');
            const response = await server.post('assay', 'saveProtocol.api', updatePayload, topFolderOptions);
            expect(response.text).toContain('One or more values cannot be removed from the multi-choice list');
        });

        // GitHub Issue 1082: Server exception "Badly formatted list of strings" on assay run import with invalid MVTC field value
        it('illegal tsv string', async () => {
            if (!supportMultiChoice) {
                return;
            }

            const { topFolderOptions } = context;

            // Import a run with ['Abnormal', 'cDNA'] as multi-choice values
            const importPayload: ImportRunOptions = {
                assayId: ASSAY_A_ID,
                dataRows: [{ [RESULT_MVTC_FIELD_NAME]: ['""x,y""'] }],
            };
            const importResponse = await importRunToServer(server, importPayload, topFolderOptions);
            expect(importResponse.body.exception).toContain('for field \'' + RESULT_MVTC_FIELD_NAME + '\' is invalid.');
        });

        // GitHub Issue 925: Not providing a MVTC value in an assay result throws error
        it('errors when required MVTC column not provided in assay import', async () => {
            if (!supportMultiChoice) {
                console.warn('Multi-choice properties are not supported in this environment, skipping multi-choice field tests');
                return;
            }

            const { topFolderOptions } = context;

            // Create a separate assay with a required MVTC result field
            const reqAssayFields: AssayDesignFieldOptions = {
                resultFields: [
                    createDomainField({ name: RESULT_FIELD_NAME }),
                    createDomainField({ name: RESULT_MVTC_FIELD_NAME, ...MVTC_FIELD_PROP, required: true } as Partial<IDomainField>),
                ],
            };
            const reqAssay = await createAssayDesign(server, 'AssayRequiredMVTC', reqAssayFields, topFolderOptions);

            // Import without providing the required MVTC column in data rows
            const importPayloadNoCol: ImportRunOptions = {
                assayId: reqAssay.protocolId,
                dataRows: [{ [RESULT_FIELD_NAME]: 'no-mvtc-column' }],
            };
            let response = await importRunToServer(server, importPayloadNoCol, topFolderOptions);
            expect(response.body.success).toBeFalsy();
            expect(response.body.exception).toContain(RESULT_MVTC_FIELD_NAME);

            // Import without blank required MVTC column in data rows
            const importPayloadNull = {
                assayId: reqAssay.protocolId,
                dataRows: [{ [RESULT_FIELD_NAME]: 'mvtc-column-null', [RESULT_MVTC_FIELD_NAME]: null }],
            };
            response = await importRunToServer(server, importPayloadNull, topFolderOptions);
            expect(response.body.success).toBeFalsy();
            expect(response.body.exception).toContain(RESULT_MVTC_FIELD_NAME);

            const importPayloadBlank = {
                assayId: reqAssay.protocolId,
                dataRows: [{ [RESULT_FIELD_NAME]: 'mvtc-column-blank', [RESULT_MVTC_FIELD_NAME]: '' }],
            };
            response = await importRunToServer(server, importPayloadBlank, topFolderOptions);
            expect(response.body.success).toBeFalsy();
            expect(response.body.exception).toContain(RESULT_MVTC_FIELD_NAME);

            const importPayloadEmpty: ImportRunOptions = {
                assayId: reqAssay.protocolId,
                dataRows: [{ [RESULT_FIELD_NAME]: 'mvtc-column-empty-array', [RESULT_MVTC_FIELD_NAME]: [] }],
            };
            response = await importRunToServer(server, importPayloadEmpty, topFolderOptions);
            expect(response.body.success).toBeFalsy();
            expect(response.body.exception).toContain(RESULT_MVTC_FIELD_NAME);

            // Import with provided required MVTC column in data rows
            const goodImportPayload = {
                assayId: reqAssay.protocolId,
                dataRows: [{ [RESULT_FIELD_NAME]: 'with-mvtc-column', [RESULT_MVTC_FIELD_NAME]: ['Abnormal', 'cDNA'] }],
            };
            response = await importRunToServer(server, goodImportPayload, topFolderOptions);
            expect(response.body.success).toBeTruthy();
        });
    });
});
