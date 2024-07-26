import mock from 'mock-fs';
import { PermissionRoles } from '@labkey/api';
import { hookServer, RequestOptions, successfulResponse } from '@labkey/test';
import { caseInsensitive } from '@labkey/components';
import {
    createDerivedObjects,
    createSample,
    createSource,
    FILE_FIELD_1_NAME,
    FILE_FIELD_2_NAME,
    getExperimentRun,
    getSampleData,
    SAMPLE_TYPE_NAME_1,
    SAMPLE_TYPE_NAME_2,
    sampleExists,
    SOURCE_TYPE_NAME_1
} from './utils';

const server = hookServer(process.env);
const PROJECT_NAME = 'MoveSamplesTest Project';

let editorUser

const DEFAULT_AUDIT_LOG_COLUMNS = 'Comment,userComment,transactionId,Created';

let editorUserOptions: RequestOptions;
let subEditorUserOptions: RequestOptions;
let authorUserOptions: RequestOptions;
let noPermsUserOptions: RequestOptions;

let topFolderOptions: RequestOptions;
let subfolder1Options;
let subfolder2Options;

beforeAll(async () => {
    await server.init(PROJECT_NAME, {
        ensureModules: ['experiment'],
    });
    topFolderOptions = { containerPath: PROJECT_NAME };

    // create subfolders to use in tests
    const subfolder1 = await server.createTestContainer();
    subfolder1Options = { containerPath: subfolder1.path };
    const subfolder2 = await server.createTestContainer();
    subfolder2Options = { containerPath: subfolder2.path };

    // create users with different permissions
    editorUser = await server.createUser('test_editor@expctrltest.com');
    await server.addUserToRole(editorUser.username, PermissionRoles.Editor, PROJECT_NAME);
    await server.addUserToRole(editorUser.username, PermissionRoles.Editor, subfolder1.path);
    await server.addUserToRole(editorUser.username, PermissionRoles.Editor, subfolder2.path);
    editorUserOptions = { requestContext: await server.createRequestContext(editorUser) };

    const subEditorUser = await server.createUser('test_subeditor@expctrltest.com');
    await server.addUserToRole(subEditorUser.username, PermissionRoles.Reader, PROJECT_NAME);
    await server.addUserToRole(subEditorUser.username, PermissionRoles.Editor, subfolder1.path);
    await server.addUserToRole(subEditorUser.username, PermissionRoles.Editor, subfolder2.path);
    subEditorUserOptions = { requestContext: await server.createRequestContext(subEditorUser) };

    const authorUser = await server.createUser('test_author@expctrltest.com');
    await server.addUserToRole(authorUser.username, PermissionRoles.Author, PROJECT_NAME);
    await server.addUserToRole(authorUser.username, PermissionRoles.Author, subfolder1.path);
    await server.addUserToRole(authorUser.username, PermissionRoles.Author, subfolder2.path);
    authorUserOptions = { requestContext: await server.createRequestContext(authorUser) };

    const noPermsUser = await server.createUser('test_no_perms@expctrltest.com');
    noPermsUserOptions = { requestContext: await server.createRequestContext(noPermsUser) };

    // create a sample type at project container for use in tests
    await server.post('property', 'createDomain', {
        kind: 'SampleSet',
        domainDesign: { name: SAMPLE_TYPE_NAME_1, fields: [{ name: 'Name',  }] }
    }, topFolderOptions).expect(successfulResponse);

    // create a second sample type at project container for use in tests
    await server.post('property', 'createDomain', {
        kind: 'SampleSet',
        domainDesign: { name: SAMPLE_TYPE_NAME_2, fields: [
            { name: 'Name', },
            { name: FILE_FIELD_1_NAME, rangeURI: 'http://cpas.fhcrc.org/exp/xml#fileLink'},
            { name: FILE_FIELD_2_NAME, rangeURI: 'http://cpas.fhcrc.org/exp/xml#fileLink'}
        ] }
    }, topFolderOptions).expect(successfulResponse);
});

afterAll(async () => {
    return server.teardown();
});

afterEach(() => {
    mock.restore();
});

async function createDerivedSamples(sampleNames: string[], sampleTypeName: string, folderOptions: RequestOptions, parentSampleType: string, parentSamples: string[], sourceParents?: string[], auditBehavior?: string, ) {
    return createDerivedObjects(server, sampleNames, "samples", sampleTypeName, folderOptions, editorUserOptions, SOURCE_TYPE_NAME_1, sourceParents, parentSampleType, parentSamples, auditBehavior);
}

async function createAliquots(sampleNames: string[], parentSampleName, sampleTypeName: string, folderOptions: RequestOptions, auditBehavior?: string, ) {
    const materialResponse = await server.request('query', 'insertRows', (agent, url) => {
        let request = agent.post(url);

        const rows = [];
        sampleNames.forEach(sampleName => {
            const row = {name: sampleName};
            row['AliquotedFrom'] = parentSampleName;
            rows.push(row);
        })
        request = request.field('json', JSON.stringify({
            schemaName: 'samples',
            queryName: sampleTypeName,
            rows,
            auditBehavior,
        }));

        return request;

    }, { ...folderOptions, ...editorUserOptions }).expect(successfulResponse);
    const sampleData = [];
    materialResponse.body.rows.forEach(row => {
        sampleData.push({
            name: caseInsensitive(row, 'name'),
            rowId: caseInsensitive(row, 'rowId'),
            run: caseInsensitive(row, 'run'),
        });
    })
    return sampleData;
}

async function createSampleWithFileFields(sampleName: string, folderOptions: RequestOptions, fileData: any, auditBehavior?: string, ) {
    const materialResponse = await server.request('query', 'insertRows', (agent, url) => {
        let request = agent.post(url);

        request = request.field('json', JSON.stringify({
            schemaName: 'samples',
            queryName: SAMPLE_TYPE_NAME_2,
            rows: [{name: sampleName}],
            auditBehavior,
        }));

        Object.keys(fileData).forEach(fieldName => {
            request = request.attach(fieldName + "::0", fileData[fieldName]);
        });

        return request;

    }, { ...folderOptions, ...editorUserOptions }).expect(successfulResponse);
    return caseInsensitive(materialResponse.body.rows[0], 'rowId');
}

async function _createSource(sourceName: string, folderOptions: RequestOptions) {
    return createSource(server, sourceName, folderOptions, editorUserOptions)
}

async function _getSampleData(sampleRowId: number, folderOptions: RequestOptions, sampleType: string = SAMPLE_TYPE_NAME_1, columns: string = 'RowId') {
    return getSampleData(server, sampleRowId, folderOptions, editorUserOptions, sampleType, columns);
}

async function getSampleTypeAuditLogs(sampleType: string, folderOptions: RequestOptions, expectedNumber: number) {
    const response = await server.post('query', 'selectRows', {
        schemaName: 'auditlog',
        queryName: 'samplesetauditevent',
        'query.samplesetname~eq': sampleType,
        'query.columns': DEFAULT_AUDIT_LOG_COLUMNS,
        'query.sort': '-Created',
        'query.maxRows': expectedNumber
    }, { ...folderOptions  }).expect(successfulResponse);
    return response.body.rows;
}

async function getSampleTimelineAuditLogs(sampleRowId: number, folderOptions: RequestOptions, columns:string = DEFAULT_AUDIT_LOG_COLUMNS) {
    const response = await server.post('query', 'selectRows', {
        schemaName: 'auditlog',
        queryName: 'SampleTimelineEvent',
        'query.sampleid~eq': sampleRowId,
        'query.columns': columns,
    }, { ...folderOptions }).expect(successfulResponse);
    return response.body.rows;
}


describe('Move Samples', () => {
    // NOTE: the MoveSamplesAction is in the experiment module, but the sample status related test cases won't
    // work here because the sample status feature is only "enabled" when the sampleManagement module is available.
    // See sampleManagement/src/client/test/integration/MoveSamplesAction.ispec.ts for additional test cases.

    function getAbsoluteContainerPath(containerPath: string) :string
    {
        return containerPath.charAt(0) === '/' ? containerPath : '/' + containerPath;
    }

    function getSlashedPath(path: any): any
    {
        return path.replaceAll("\\", "/");
    }

    async function verifyRunData(runId: number, folderOptions: RequestOptions, name: string) {
        const runData = await getExperimentRun(server, runId, folderOptions);
        expect(runData).toHaveLength(1);
        expect(caseInsensitive(runData[0], 'container/Path')).toBe(getAbsoluteContainerPath(folderOptions.containerPath));
        expect(caseInsensitive(runData[0], 'name')).toBe(name);
    }

    async function verifySampleTypeAuditLogs(sourceFolderOptions: RequestOptions, targetFolderOptions: RequestOptions, sampleIds: number[], userComment: string = null, sampleType:string = SAMPLE_TYPE_NAME_1): Promise<number>
    {
        const sampleTypeEventsInSource = await getSampleTypeAuditLogs(sampleType, sourceFolderOptions, 2);
        const samplesPhrase = sampleIds.length == 1 ? "1 sample" : sampleIds.length + " samples";
        const targetPath = getAbsoluteContainerPath(targetFolderOptions.containerPath)
        const sourcePath = getAbsoluteContainerPath(sourceFolderOptions.containerPath);
        expect(caseInsensitive(sampleTypeEventsInSource[0], 'Comment')).toEqual("Moved " + samplesPhrase + " to " + targetPath);
        const transactionId = caseInsensitive(sampleTypeEventsInSource[0], 'transactionId');
        expect(caseInsensitive(sampleTypeEventsInSource[0], 'userComment')).toBe(userComment);
        expect(caseInsensitive(sampleTypeEventsInSource[1], 'Comment')).toEqual("Samples inserted in: " + sampleType);
        const sampleTypeEventsInTarget = await getSampleTypeAuditLogs(sampleType, targetFolderOptions, 1);
        expect(caseInsensitive(sampleTypeEventsInTarget[0], 'Comment')).toEqual("Moved " + samplesPhrase + " from " + sourcePath);
        expect(caseInsensitive(sampleTypeEventsInTarget[0], 'transactionId')).toBe(transactionId);
        return transactionId;
    }

    async function verifyDetailedAuditLogs(sourceFolderOptions: RequestOptions, targetFolderOptions: RequestOptions, sampleIds: number[], transactionId: number = undefined, userComment: string = null, valueChanges: any[] = undefined) {
        for (const sampleRowId of sampleIds) {
            const sampleEventsInSource = await getSampleTimelineAuditLogs(sampleRowId, sourceFolderOptions);
            expect(sampleEventsInSource).toHaveLength(0);
        }

        for (const sampleRowId of sampleIds) {

            const sampleEventsInTarget = await getSampleTimelineAuditLogs(sampleRowId, targetFolderOptions, valueChanges ? DEFAULT_AUDIT_LOG_COLUMNS + ",OldValues,NewValues": DEFAULT_AUDIT_LOG_COLUMNS);
            expect(sampleEventsInTarget).toHaveLength(2);
            expect(caseInsensitive(sampleEventsInTarget[0], 'Comment')).toEqual("Sample project was updated.");
            expect(caseInsensitive(sampleEventsInTarget[0], 'userComment')).toBe(userComment);

            if (valueChanges) {
                valueChanges.forEach(valueChange => {
                    const oldValues =  caseInsensitive(sampleEventsInTarget[0], 'OldValues');
                    const newValues = caseInsensitive(sampleEventsInTarget[0], 'NewValues');
                    expect(getSlashedPath(decodeURIComponent(oldValues)).indexOf(getSlashedPath(valueChange.oldValue))).toBeGreaterThan(-1);
                    expect(getSlashedPath(decodeURIComponent(newValues)).indexOf(getSlashedPath(valueChange.newValue))).toBeGreaterThan(-1);
                })
            }
            if (transactionId)
                expect(caseInsensitive(sampleEventsInTarget[0], 'transactionId')).toBe(transactionId);
            expect(caseInsensitive(sampleEventsInTarget[1], 'Comment')).toEqual("Sample was registered.");
        }
    }

    describe("move samples via moveRows.api", () => {
        it('requires POST', () => {
            server.get('query', 'moveRows.api').expect(405);
        });

        it('error, no permissions', async () => {
            // Act
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: subfolder1Options.containerPath,
                schemaName: 'samples',
                queryName: SAMPLE_TYPE_NAME_1,
                rows: [{}],
            }, {...topFolderOptions, ...noPermsUserOptions}).expect(403);

            // Assert
            const {exception, success} = response.body;
            expect(success).toBe(false);
            expect(exception).toEqual('User does not have permission to perform this operation.');
        });

        it('error, requires update permissions in current', async () => {
            // Act
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: subfolder1Options.containerPath,
                schemaName: 'samples',
                queryName: SAMPLE_TYPE_NAME_1,
                rows: [{}],
            }, {...topFolderOptions, ...authorUserOptions}).expect(403);

            // Assert
            const {exception, success} = response.body;
            expect(success).toBe(false);
            expect(exception).toEqual('User does not have permission to perform this operation.');
        });

        it('error, missing required targetContainer param', async () => {
            // Act
            const response = await server.post('query', 'moveRows.api', {
                schemaName: 'samples',
                queryName: SAMPLE_TYPE_NAME_1,
                rows: [{}],
            }, {...topFolderOptions, ...editorUserOptions}).expect(400);

            // Assert
            const {exception, success} = response.body;
            expect(success).toBe(false);
            expect(exception).toEqual('A target container must be specified for the move operation.');
        });

        it('error, non-existent targetContainer param', async () => {
            // Act
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: 'BOGUS',
                schemaName: 'samples',
                queryName: SAMPLE_TYPE_NAME_1,
                rows: [{}],
            }, {...topFolderOptions, ...editorUserOptions}).expect(400);

            // Assert
            const {exception, success} = response.body;
            expect(success).toBe(false);
            expect(exception).toEqual('The target container was not found: BOGUS.');
        });

        it('error, missing required rows param', async () => {
            // Act
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: subfolder1Options.containerPath,
                schemaName: 'samples',
                queryName: SAMPLE_TYPE_NAME_1,
            }, {...topFolderOptions, ...editorUserOptions}).expect(400);

            // Assert
            const {exception, success} = response.body;
            expect(success).toBe(false);
            expect(exception).toEqual("No 'rows' array supplied.");
        });

        it('error, empty rows param', async () => {
            // Act
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: subfolder1Options.containerPath,
                schemaName: 'samples',
                queryName: SAMPLE_TYPE_NAME_1,
                rows: []
            }, {...topFolderOptions, ...editorUserOptions}).expect(400);

            // Assert
            const {exception, success} = response.body;
            expect(success).toBe(false);
            expect(exception).toEqual("No 'rows' array supplied.");
        });

        it('error, invalid sample rowId', async () => {
            // Act
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: subfolder1Options.containerPath,
                schemaName: 'samples',
                queryName: SAMPLE_TYPE_NAME_1,
                rows: [{ RowId: -1 }]
            }, {...topFolderOptions, ...editorUserOptions}).expect(400);

            // Assert
            const {exception, success} = response.body;
            expect(success).toBe(false);
            expect(exception).toEqual('Unable to find all samples for the move operation.');
        });

        it('error, requires insert perm in targetContainer', async () => {
            // Arrange
            const sampleRowId = await createSample(server, 'sub1-notmoved-3', subfolder1Options, editorUserOptions);

            // Act
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: topFolderOptions.containerPath,
                schemaName: 'samples',
                queryName: SAMPLE_TYPE_NAME_1,
                rows: [{ rowId: sampleRowId }],
            }, {...subfolder1Options, ...subEditorUserOptions}).expect(400);

            // Assert
            const {exception, success} = response.body;
            expect(success).toBe(false);
            expect(exception).toEqual("You do not have permission to move rows from '" + SAMPLE_TYPE_NAME_1 + "' to the target container: " + PROJECT_NAME + ".");

            const sampleExistsInTop = await sampleExists(server, sampleRowId, topFolderOptions, editorUserOptions, SAMPLE_TYPE_NAME_1);
            expect(sampleExistsInTop).toBe(false);
            const sampleExistsInSub1 = await sampleExists(server, sampleRowId, subfolder1Options, editorUserOptions, SAMPLE_TYPE_NAME_1);
            expect(sampleExistsInSub1).toBe(true);
        });

        it('success, sample ID not in current parent project', async () => {
            // Arrange
            const sampleRowId = await createSample(server, 'sub1-movedTo2-1', subfolder1Options, editorUserOptions);

            // Act
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: subfolder2Options.containerPath,
                schemaName: 'samples',
                queryName: SAMPLE_TYPE_NAME_1,
                rows: [{ rowId: sampleRowId }],
            }, {...topFolderOptions, ...editorUserOptions}).expect(200);

            // Assert
            const {success, updateCounts} = response.body;
            expect(success).toBe(true);
            expect(updateCounts.samples).toBe(1);

            const sampleExistsInSub1 = await sampleExists(server, sampleRowId, subfolder1Options, editorUserOptions, SAMPLE_TYPE_NAME_1);
            expect(sampleExistsInSub1).toBe(false);
            const sampleExistsInSub2 = await sampleExists(server, sampleRowId, subfolder2Options, editorUserOptions, SAMPLE_TYPE_NAME_1);
            expect(sampleExistsInSub2).toBe(true);
        });

        it('success, sample ID not in current subfolder', async () => {
            // Arrange
            const sampleRowId = await createSample(server, 'top-movedTo2-1', topFolderOptions, editorUserOptions);

            // Act
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: subfolder2Options.containerPath,
                schemaName: 'samples',
                queryName: SAMPLE_TYPE_NAME_1,
                rows: [{ rowId: sampleRowId }],
            }, {...subfolder1Options, ...editorUserOptions}).expect(200);

            // Assert
            const {success} = response.body;
            expect(success).toBe(true);

            const sampleExistsInTop = await sampleExists(server, sampleRowId, topFolderOptions, editorUserOptions, SAMPLE_TYPE_NAME_1);
            expect(sampleExistsInTop).toBe(false);
            const sampleExistsInSub1 = await sampleExists(server, sampleRowId, subfolder2Options, editorUserOptions, SAMPLE_TYPE_NAME_1);
            expect(sampleExistsInSub1).toBe(true);
        });

        it('success, some sample IDs not in current subfolder', async () => {
            // Arrange
            const sampleRowId1 = await createSample(server, 'top-movedTo1-1', topFolderOptions, editorUserOptions);
            const sampleRowId2 = await createSample(server, 'sub1-notMovedTo1-1', subfolder1Options, editorUserOptions);

            // Act
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: subfolder2Options.containerPath,
                schemaName: 'samples',
                queryName: SAMPLE_TYPE_NAME_1,
                rows: [{ rowId: sampleRowId1}, { rowId: sampleRowId2 }],
            }, {...subfolder1Options, ...editorUserOptions}).expect(200);

            // Assert
            const {success} = response.body;
            expect(success).toBe(true);

            const sample1ExistsInTop = await sampleExists(server, sampleRowId1, topFolderOptions, editorUserOptions, SAMPLE_TYPE_NAME_1);
            expect(sample1ExistsInTop).toBe(false);
            const sample1ExistsInSub1 = await sampleExists(server, sampleRowId1, subfolder2Options, editorUserOptions, SAMPLE_TYPE_NAME_1);
            expect(sample1ExistsInSub1).toBe(true);
            const sample2ExistsInSub1 = await sampleExists(server, sampleRowId2, subfolder2Options, editorUserOptions, SAMPLE_TYPE_NAME_1);
            expect(sample2ExistsInSub1).toBe(true);
        });

        it('success, all sample IDs in target subfolder', async () => {
            // Arrange
            const sampleRowId1 = await createSample(server, 'sub1-notMoved-1', subfolder1Options, editorUserOptions);
            const sampleRowId2 = await createSample(server, 'sub1-notMoved-2', subfolder1Options, editorUserOptions);

            // Act
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: subfolder1Options.containerPath,
                schemaName: 'samples',
                queryName: SAMPLE_TYPE_NAME_1,
                rows: [{ rowId: sampleRowId1}, { rowId: sampleRowId2 }],
            }, {...topFolderOptions, ...editorUserOptions}).expect(200);

            // Assert
            const {success, updateCounts} = response.body;
            expect(success).toBe(true);
            expect(updateCounts.samples).toBeUndefined();

            const sample1ExistsSub1 = await sampleExists(server, sampleRowId1, subfolder1Options, editorUserOptions, SAMPLE_TYPE_NAME_1);
            expect(sample1ExistsSub1).toBe(true);
            const sample2ExistsInSub1 = await sampleExists(server, sampleRowId2, subfolder1Options, editorUserOptions, SAMPLE_TYPE_NAME_1);
            expect(sample2ExistsInSub1).toBe(true);
        });

        it('success, move sample from parent project to subfolder, no audit logging', async () => {
            // Arrange
            const sampleRowId = await createSample(server, 'top-movetosub1-1', topFolderOptions, editorUserOptions, 'NONE');

            // Act
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: subfolder1Options.containerPath,
                schemaName: 'samples',
                queryName: SAMPLE_TYPE_NAME_1,
                rows: [{ rowId: sampleRowId }],
            }, {...topFolderOptions, ...editorUserOptions}).expect(200);

            // Assert
            const {updateCounts} = response.body;
            expect(updateCounts.samples).toBe(1);
            expect(updateCounts.sampleAliases).toBe(0);
            expect(updateCounts.sampleAuditEvents).toBe(0);

            const sampleExistsInTop = await sampleExists(server, sampleRowId, topFolderOptions, editorUserOptions, SAMPLE_TYPE_NAME_1);
            expect(sampleExistsInTop).toBe(false);
            const sampleExistsInSub1 = await sampleExists(server, sampleRowId, subfolder1Options, editorUserOptions, SAMPLE_TYPE_NAME_1);
            expect(sampleExistsInSub1).toBe(true);

            await verifySampleTypeAuditLogs(topFolderOptions, subfolder1Options, [sampleRowId]);

            const sampleEventsInTop = await getSampleTimelineAuditLogs(sampleRowId, topFolderOptions);
            expect(sampleEventsInTop).toHaveLength(0);
            const sampleEventsInSub1 = await getSampleTimelineAuditLogs(sampleRowId, subfolder1Options);
            expect(sampleEventsInSub1).toHaveLength(0);
        });

        it('success, move sample from parent project to subfolder, detailed audit logging', async () => {
            // Arrange
            const sampleRowId = await createSample(server, 'top-movetosub1-2', topFolderOptions, editorUserOptions, "DETAILED");

            // Act
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: subfolder1Options.containerPath,
                schemaName: 'samples',
                queryName: SAMPLE_TYPE_NAME_1,
                rows: [{ rowId: sampleRowId }],
                auditBehavior: "DETAILED",
            }, {...topFolderOptions, ...editorUserOptions}).expect(200);

            // Assert
            const {updateCounts} = response.body;
            expect(updateCounts.samples).toBe(1);
            expect(updateCounts.sampleAliases).toBe(0);
            expect(updateCounts.sampleAuditEvents).toBe(1);

            const sampleExistsInTop = await sampleExists(server, sampleRowId, topFolderOptions, editorUserOptions, SAMPLE_TYPE_NAME_1);
            expect(sampleExistsInTop).toBe(false);
            const sampleExistsInSub1 = await sampleExists(server, sampleRowId, subfolder1Options, editorUserOptions, SAMPLE_TYPE_NAME_1);
            expect(sampleExistsInSub1).toBe(true);

            const auditTransactionId = await verifySampleTypeAuditLogs(topFolderOptions, subfolder1Options, [sampleRowId]);
            await verifyDetailedAuditLogs(topFolderOptions, subfolder1Options, [sampleRowId], auditTransactionId);
        });

        it('success, move sample from parent project to subfolder, detailed audit logging with comment', async () => {
            // Arrange
            const sampleRowId = await createSample(server, 'top-movetosub1-3', topFolderOptions, editorUserOptions, "DETAILED");
            const userComment = "Oops! Wrong project.";
            // Act
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: subfolder1Options.containerPath,
                schemaName: 'samples',
                queryName: SAMPLE_TYPE_NAME_1,
                rows: [{ rowId: sampleRowId }],
                auditBehavior: "DETAILED",
                auditUserComment: userComment,
            }, {...topFolderOptions, ...editorUserOptions}).expect(200);

            // Assert
            const {updateCounts} = response.body;
            expect(updateCounts.samples).toBe(1);
            expect(updateCounts.sampleAliases).toBe(0);
            expect(updateCounts.sampleAuditEvents).toBe(1);

            const sampleExistsInTop = await sampleExists(server, sampleRowId, topFolderOptions, editorUserOptions, SAMPLE_TYPE_NAME_1);
            expect(sampleExistsInTop).toBe(false);
            const sampleExistsInSub1 = await sampleExists(server, sampleRowId, subfolder1Options, editorUserOptions, SAMPLE_TYPE_NAME_1);
            expect(sampleExistsInSub1).toBe(true);

            const auditTransactionId = await verifySampleTypeAuditLogs(topFolderOptions, subfolder1Options, [sampleRowId], userComment);
            await verifyDetailedAuditLogs(topFolderOptions, subfolder1Options, [sampleRowId], auditTransactionId, userComment);
        });

        it('success, move sample from parent project to subfolder with summary logging', async () => {
            // Arrange
            const sampleRowId = await createSample(server, 'top-movetosub1-4', topFolderOptions, editorUserOptions, "DETAILED");
            const userComment = "4 is in the wrong place."

            // Act
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: subfolder1Options.containerPath,
                schemaName: 'samples',
                queryName: SAMPLE_TYPE_NAME_1,
                rows: [{ rowId: sampleRowId }],
                auditBehavior: 'SUMMARY',
                auditUserComment: userComment,
            }, {...topFolderOptions, ...editorUserOptions}).expect(200);

            // Assert
            const {updateCounts} = response.body;
            expect(updateCounts.samples).toBe(1);
            expect(updateCounts.sampleAliases).toBe(0);
            expect(updateCounts.sampleAuditEvents).toBe(1);

            const sampleExistsInTop = await sampleExists(server, sampleRowId, topFolderOptions, editorUserOptions, SAMPLE_TYPE_NAME_1);
            expect(sampleExistsInTop).toBe(false);
            const sampleExistsInSub1 = await sampleExists(server, sampleRowId, subfolder1Options, editorUserOptions, SAMPLE_TYPE_NAME_1);
            expect(sampleExistsInSub1).toBe(true);

            await verifySampleTypeAuditLogs(topFolderOptions, subfolder1Options, [sampleRowId], userComment);

            const sampleEventsInTop = await getSampleTimelineAuditLogs(sampleRowId, topFolderOptions);
            expect(sampleEventsInTop).toHaveLength(0);
            const sampleEventsInSub1 = await getSampleTimelineAuditLogs(sampleRowId, subfolder1Options);
            expect(sampleEventsInSub1).toHaveLength(1);
            expect(caseInsensitive(sampleEventsInSub1[0], 'Comment')).toEqual("Sample was registered.");
        });

        it('success, move sample from subfolder to parent project', async () => {
            // Arrange
            const sampleRowId = await createSample(server, 'sub1-movetotop-1', subfolder1Options, editorUserOptions, "DETAILED");

            // Act
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: topFolderOptions.containerPath,
                schemaName: 'samples',
                queryName: SAMPLE_TYPE_NAME_1,
                rows: [{ rowId: sampleRowId }],
                auditBehavior: "DETAILED",
            }, {...subfolder1Options, ...editorUserOptions}).expect(200);

            // Assert
            const {updateCounts} = response.body;
            expect(updateCounts.samples).toBe(1);
            expect(updateCounts.sampleAliases).toBe(0);
            expect(updateCounts.sampleAuditEvents).toBe(1);

            const sampleExistsInTop = await sampleExists(server, sampleRowId, topFolderOptions, editorUserOptions, SAMPLE_TYPE_NAME_1);
            expect(sampleExistsInTop).toBe(true);
            const sampleExistsInSub1 = await sampleExists(server, sampleRowId, subfolder1Options, editorUserOptions, SAMPLE_TYPE_NAME_1);
            expect(sampleExistsInSub1).toBe(false);

            const auditTransactionId = await verifySampleTypeAuditLogs(subfolder1Options, topFolderOptions, [sampleRowId]);
            await verifyDetailedAuditLogs(subfolder1Options, topFolderOptions, [sampleRowId], auditTransactionId);

        });

        it('success, move sample from subfolder to sibling', async () => {
            // Arrange
            const sampleRowId = await createSample(server, 'sub1-movetosub2-1', subfolder1Options, editorUserOptions, "DETAILED");

            // Act
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: subfolder2Options.containerPath,
                schemaName: 'samples',
                queryName: SAMPLE_TYPE_NAME_1,
                rows: [{ rowId: sampleRowId }],
                auditBehavior: "DETAILED",
            }, {...subfolder1Options, ...editorUserOptions}).expect(200);

            // Assert
            const {updateCounts} = response.body;
            expect(updateCounts.samples).toBe(1);
            expect(updateCounts.sampleAliases).toBe(0);
            expect(updateCounts.sampleAuditEvents).toBe(1);

            const sampleExistsInSub1 = await sampleExists(server, sampleRowId, subfolder1Options, editorUserOptions, SAMPLE_TYPE_NAME_1);
            expect(sampleExistsInSub1).toBe(false);
            const sampleExistsInSub2 = await sampleExists(server, sampleRowId, subfolder2Options, editorUserOptions, SAMPLE_TYPE_NAME_1);
            expect(sampleExistsInSub2).toBe(true);

            const auditTransactionId = await verifySampleTypeAuditLogs(subfolder1Options, subfolder2Options, [sampleRowId]);
            await verifyDetailedAuditLogs(subfolder1Options, subfolder2Options, [sampleRowId], auditTransactionId);

        });

        it('success, move samples from multiple types', async () => {
            // Arrange
            const sampleRowId1 = await createSample(server, 'sub1-movetosub2-5', subfolder1Options, editorUserOptions, "DETAILED");
            const sampleRowId2 = await createSample(server, 'sub1-movetosub2-6', subfolder1Options, editorUserOptions, "DETAILED");
            const sampleRowId3 = await createSample(server, 'type2-sub1-movetosub2-1', subfolder1Options, editorUserOptions, "DETAILED", SAMPLE_TYPE_NAME_2);

            // Act
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: subfolder2Options.containerPath,
                schemaName: 'samples',
                queryName: SAMPLE_TYPE_NAME_2,
                rows: [{ rowId: sampleRowId1 }, { rowId: sampleRowId2 }, { rowId: sampleRowId3 }],
                auditBehavior: "DETAILED",
            }, {...subfolder1Options, ...editorUserOptions}).expect(200);

            // Assert
            const {updateCounts} = response.body;
            expect(updateCounts.samples).toBe(3);
            expect(updateCounts.sampleAliases).toBe(0);
            expect(updateCounts.sampleAuditEvents).toBe(3);

            let sampleExistsInSub1 = await sampleExists(server, sampleRowId1, subfolder1Options, editorUserOptions, SAMPLE_TYPE_NAME_1);
            expect(sampleExistsInSub1).toBe(false);
            sampleExistsInSub1 = await sampleExists(server, sampleRowId2, subfolder1Options, editorUserOptions, SAMPLE_TYPE_NAME_1);
            expect(sampleExistsInSub1).toBe(false);
            sampleExistsInSub1 = await sampleExists(server, sampleRowId3, subfolder1Options, editorUserOptions, SAMPLE_TYPE_NAME_2);
            expect(sampleExistsInSub1).toBe(false);

            let sampleExistsInSub2 = await sampleExists(server, sampleRowId1, subfolder2Options, editorUserOptions, SAMPLE_TYPE_NAME_1);
            expect(sampleExistsInSub2).toBe(true);
            sampleExistsInSub2 = await sampleExists(server, sampleRowId2, subfolder2Options, editorUserOptions, SAMPLE_TYPE_NAME_1);
            expect(sampleExistsInSub2).toBe(true);
            sampleExistsInSub2 = await sampleExists(server, sampleRowId3, subfolder2Options, editorUserOptions, SAMPLE_TYPE_NAME_2);
            expect(sampleExistsInSub2).toBe(true);

            const auditTransactionId = await verifySampleTypeAuditLogs(subfolder1Options, subfolder2Options, [sampleRowId1, sampleRowId2]);
            const auditTransactionId2 = await verifySampleTypeAuditLogs(subfolder1Options, subfolder2Options, [sampleRowId3], undefined, SAMPLE_TYPE_NAME_2);
            expect(auditTransactionId2).toBe(auditTransactionId);
            await verifyDetailedAuditLogs(subfolder1Options, subfolder2Options, [sampleRowId1, sampleRowId2, sampleRowId3], auditTransactionId);
        });

        it('success, move one sample from parent with file field', async () => {
            mock({
                'fileA.txt': 'fileA contents',
            });
            const sampleRowId1 = await createSampleWithFileFields('top2-movetosub1-1', topFolderOptions, {[FILE_FIELD_1_NAME]: 'fileA.txt'}, "DETAILED");
            const userComment = "Moving files too";
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: subfolder1Options.containerPath,
                schemaName: 'samples',
                queryName: SAMPLE_TYPE_NAME_1,
                rows: [{ rowId: sampleRowId1 }],
                auditBehavior: "DETAILED",
                auditUserComment: userComment,
            }, {...topFolderOptions, ...editorUserOptions}).expect(200);

            // Assert
            const {updateCounts} = response.body;
            expect(updateCounts.samples).toBe(1);
            expect(updateCounts.sampleFiles).toBe(1);
            const sampleData = await _getSampleData(sampleRowId1, subfolder1Options, SAMPLE_TYPE_NAME_2, "RowId," + FILE_FIELD_1_NAME);

            expect(sampleData.length).toBe(1);
            expect(getSlashedPath(sampleData[0][FILE_FIELD_1_NAME]).endsWith(subfolder1Options.containerPath + "/@files/sampletype/fileA.txt")).toBe(true);

            await verifyDetailedAuditLogs(topFolderOptions, subfolder1Options, [sampleRowId1], undefined, userComment,
                [{
                    oldValue: getSlashedPath(topFolderOptions.containerPath + "/@files/sampletype/fileA.txt"),
                    newValue: getSlashedPath(subfolder1Options.containerPath + "/@files/sampletype/fileA.txt")
                }]);
        });

        it('success, move sample from subfolder with multiple file fields', async () => {
            mock({
                'fileB.txt': 'fileB contents',
                'fileC.txt': 'fileC contents',
            });
            const sampleRowId1 = await createSampleWithFileFields('sub12-movetotop-1', subfolder1Options, {
                [FILE_FIELD_1_NAME]: 'fileB.txt',
                [FILE_FIELD_2_NAME]: 'fileC.txt'
            }, "DETAILED");

            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: topFolderOptions.containerPath,
                schemaName: 'samples',
                queryName: SAMPLE_TYPE_NAME_1,
                rows: [{ rowId: sampleRowId1 }],
                auditBehavior: "DETAILED",
            }, {...subfolder1Options, ...editorUserOptions}).expect(200);

            // Assert
            const {updateCounts} = response.body;
            expect(updateCounts.samples).toBe(1);
            expect(updateCounts.sampleFiles).toBe(2);
            const sampleData = await _getSampleData(sampleRowId1, topFolderOptions, SAMPLE_TYPE_NAME_2, "RowId," + FILE_FIELD_1_NAME + "," + FILE_FIELD_2_NAME);
            expect(sampleData.length).toBe(1);
            expect(getSlashedPath(sampleData[0][FILE_FIELD_1_NAME]).endsWith(topFolderOptions.containerPath + "/@files/sampletype/fileB.txt")).toBe(true);
            expect(getSlashedPath(sampleData[0][FILE_FIELD_2_NAME]).endsWith(topFolderOptions.containerPath + "/@files/sampletype/fileC.txt")).toBe(true);

            await verifyDetailedAuditLogs(subfolder1Options, topFolderOptions, [sampleRowId1], undefined, undefined, [{
                oldValue: getSlashedPath(subfolder1Options.containerPath + "/@files/sampletype/fileB.txt"),
                newValue: getSlashedPath(topFolderOptions.containerPath + "/@files/sampletype/fileB.txt")
            }, {
                oldValue: getSlashedPath(subfolder1Options.containerPath + "/@files/sampletype/fileC.txt"),
                newValue: getSlashedPath(topFolderOptions.containerPath + "/@files/sampletype/fileC.txt")
            }]);
        });

        it('success, move sample from subfolder with overlapping file name', async () => {
            mock({
                'fileD.txt': 'fileD contents',
                'fileE.txt': 'fileE contents',
            });
            const sampleRowId1 = await createSampleWithFileFields('sub12-movetotop-2', subfolder1Options, {
                [FILE_FIELD_1_NAME]: 'fileD.txt',
                [FILE_FIELD_2_NAME]: 'fileE.txt'
            }, "DETAILED");
            // create sample in target folder with file names the same as source folder samples.
            await createSampleWithFileFields('top2-withfile-1', topFolderOptions, {[FILE_FIELD_2_NAME]: 'fileD.txt'}, "DETAILED");

            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: topFolderOptions.containerPath,
                schemaName: 'samples',
                queryName: SAMPLE_TYPE_NAME_1,
                rows: [{ rowId: sampleRowId1 }],
                auditBehavior: "DETAILED",
            }, {...subfolder1Options, ...editorUserOptions}).expect(200);

            // Assert
            const {updateCounts} = response.body;
            expect(updateCounts.samples).toBe(1);
            expect(updateCounts.sampleFiles).toBe(2);
            const sampleData = await _getSampleData(sampleRowId1, topFolderOptions, SAMPLE_TYPE_NAME_2, "RowId," + FILE_FIELD_1_NAME + "," + FILE_FIELD_2_NAME);
            expect(sampleData.length).toBe(1);
            expect(getSlashedPath(sampleData[0][FILE_FIELD_1_NAME]).endsWith(topFolderOptions.containerPath + "/@files/sampletype/fileD-1.txt")).toBe(true);
            expect(getSlashedPath(sampleData[0][FILE_FIELD_2_NAME]).endsWith(topFolderOptions.containerPath + "/@files/sampletype/fileE.txt")).toBe(true);

            await verifyDetailedAuditLogs(subfolder1Options, topFolderOptions, [sampleRowId1], undefined, undefined, [{
                oldValue: getSlashedPath(subfolder1Options.containerPath + "/@files/sampletype/fileD.txt"),
                newValue: getSlashedPath(topFolderOptions.containerPath + "/@files/sampletype/fileD-1.txt")
            }, {
                oldValue: getSlashedPath(subfolder1Options.containerPath + "/@files/sampletype/fileE.txt"),
                newValue: getSlashedPath(topFolderOptions.containerPath + "/@files/sampletype/fileE.txt")
            }]);
        });

        it('success, move to sibling subfolder without files', async () => {
            mock({
                'fileF.txt': 'fileF contents',
            });
            const sampleRowId1 = await createSampleWithFileFields('sub12-movetosub2-1', subfolder1Options, {[FILE_FIELD_1_NAME]: 'fileF.txt'}, "DETAILED");

            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: subfolder2Options.containerPath,
                schemaName: 'samples',
                queryName: SAMPLE_TYPE_NAME_1,
                rows: [{ rowId: sampleRowId1 }],
                auditBehavior: "DETAILED",
            }, {...subfolder1Options, ...editorUserOptions}).expect(200);

            // Assert
            const {updateCounts} = response.body;
            expect(updateCounts.samples).toBe(1);
            expect(updateCounts.sampleFiles).toBe(1);
            const sampleData = await _getSampleData(sampleRowId1, subfolder2Options, SAMPLE_TYPE_NAME_2, "RowId," + FILE_FIELD_1_NAME);
            expect(sampleData.length).toBe(1);
            expect(getSlashedPath(sampleData[0][FILE_FIELD_1_NAME]).endsWith(subfolder2Options.containerPath + "/@files/sampletype/fileF.txt")).toBe(true);

            await verifyDetailedAuditLogs(subfolder1Options, subfolder2Options, [sampleRowId1], undefined, undefined, [{
                oldValue: getSlashedPath(subfolder1Options.containerPath + "/@files/sampletype/fileF.txt"),
                newValue: getSlashedPath(subfolder2Options.containerPath + "/@files/sampletype/fileF.txt")
            }]);
        });

        it('success, move multiple samples', async () => {
            // Arrange
            const sampleRowId1 = await createSample(server, 'sub1-movetosub2-2', subfolder1Options, editorUserOptions, "DETAILED");
            const sampleRowId2 = await createSample(server, 'sub1-movetosub2-3', subfolder1Options, editorUserOptions, "DETAILED");
            const sampleRowId3 = await createSample(server, 'sub1-movetosub2-4', subfolder1Options, editorUserOptions, "DETAILED");

            // Act
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: subfolder2Options.containerPath,
                schemaName: 'samples',
                queryName: SAMPLE_TYPE_NAME_1,
                rows: [{ rowId: sampleRowId1 }, { rowId: sampleRowId2 }, { rowId: sampleRowId3 }],
                auditBehavior: "DETAILED",
            }, {...subfolder1Options, ...editorUserOptions}).expect(200);

            // Assert
            const {updateCounts} = response.body;
            expect(updateCounts.samples).toBe(3);
            expect(updateCounts.sampleAliases).toBe(0);
            expect(updateCounts.sampleAuditEvents).toBe(3);
            expect(updateCounts.sampleFiles).toBe(0);

            let sampleExistsInSub1 = await sampleExists(server, sampleRowId1, subfolder1Options, editorUserOptions, SAMPLE_TYPE_NAME_1);
            expect(sampleExistsInSub1).toBe(false);
            sampleExistsInSub1 = await sampleExists(server, sampleRowId2, subfolder1Options, editorUserOptions, SAMPLE_TYPE_NAME_1);
            expect(sampleExistsInSub1).toBe(false);
            sampleExistsInSub1 = await sampleExists(server, sampleRowId3, subfolder1Options, editorUserOptions, SAMPLE_TYPE_NAME_1);
            expect(sampleExistsInSub1).toBe(false);

            let sampleExistsInSub2 = await sampleExists(server, sampleRowId1, subfolder2Options, editorUserOptions, SAMPLE_TYPE_NAME_1);
            expect(sampleExistsInSub2).toBe(true);
            sampleExistsInSub2 = await sampleExists(server, sampleRowId2, subfolder2Options, editorUserOptions, SAMPLE_TYPE_NAME_1);
            expect(sampleExistsInSub2).toBe(true);
            sampleExistsInSub2 = await sampleExists(server, sampleRowId3, subfolder2Options, editorUserOptions, SAMPLE_TYPE_NAME_1);
            expect(sampleExistsInSub2).toBe(true);

            const auditTransactionId = await verifySampleTypeAuditLogs(subfolder1Options, subfolder2Options, [sampleRowId1, sampleRowId2, sampleRowId3]);
            await verifyDetailedAuditLogs(subfolder1Options, subfolder2Options, [sampleRowId1, sampleRowId2, sampleRowId3], auditTransactionId);

            // verify that we are able to delete the original sample container after things are moved
            await server.post('core', 'deleteContainer', undefined, {...subfolder1Options}).expect(successfulResponse);
        });

        it('success, move all child derived samples to sibling', async () => {
            const subfolder3 = await server.createTestContainer();

            const subfolder3Options = {containerPath: subfolder3.path};
            await server.addUserToRole(editorUser.username, PermissionRoles.Editor, subfolder3.path);

            // create a sample in the top folder
            const sampleRowId1 = await createSample(server, 'top-parent-1', topFolderOptions, editorUserOptions, "DETAILED");

            // derive samples into new subfolder
            const derivedSamples = await createDerivedSamples(
                ['sub3-derived-1', 'sub3-derived-2'],
                SAMPLE_TYPE_NAME_2,
                subfolder3Options,
                SAMPLE_TYPE_NAME_1,
                [sampleRowId1]
            );

            const rowIdsToMove = derivedSamples.map(data => data.rowId);
            // the two samples should have the same runId
            const runId = caseInsensitive(derivedSamples[0], 'run');
            expect(runId).toBe(caseInsensitive(derivedSamples[1], 'run'));

            // move samples to sibling folder
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: subfolder2Options.containerPath,
                schemaName: 'samples',
                queryName: SAMPLE_TYPE_NAME_1,
                rows: derivedSamples,
                auditBehavior: "DETAILED",
            }, {...subfolder3Options, ...editorUserOptions}).expect(200);

            const {updateCounts} = response.body;
            expect(updateCounts.samples).toBe(2);
            expect(updateCounts.sampleDerivationRunsUpdated).toBe(1);
            expect(updateCounts.sampleDerivationRunsSplit).toBe(0);

            // verify run container is updated
            const sample1Data = await _getSampleData(rowIdsToMove[0], subfolder2Options, SAMPLE_TYPE_NAME_2, "RowId,Run");
            // runId should not change
            expect(sample1Data.length).toBe(1);
            expect(caseInsensitive(sample1Data[0], 'run')).toBe(runId);

            const sample2Data = await _getSampleData(rowIdsToMove[0], subfolder2Options, SAMPLE_TYPE_NAME_2, "RowId,Run");
            // runId should not change
            expect(sample2Data.length).toBe(1);
            expect(caseInsensitive(sample2Data[0], 'run')).toBe(runId);

            verifyRunData(runId, subfolder2Options, 'Derive 2 samples from top-parent-1');

            // delete original subfolder
            await server.post('core', 'deleteContainer', undefined, {...subfolder3Options}).expect(successfulResponse);
            verifyRunData(runId, subfolder2Options, 'Derive 2 samples from top-parent-1');
        });

        it('success, move some child aliquots to parent', async () => {
            const subfolder3 = await server.createTestContainer();

            const subfolder3Options = {containerPath: subfolder3.path};
            await server.addUserToRole(editorUser.username, PermissionRoles.Editor, subfolder3.path);

            // create a sample in the top folder
            await createSample(server, 'top-parent-2', topFolderOptions, editorUserOptions, "DETAILED");

            // derive samples into new subfolder
            const aliquots = await createAliquots(
                ['sub3-aliquot-1', 'sub3-aliquot-2', 'sub3-aliquot-3'],
                'top-parent-2',
                SAMPLE_TYPE_NAME_1,
                subfolder3Options
            );

            const aliquotRowIds = aliquots.map(data => caseInsensitive(data, 'rowId'));
            // the samples should have the same runId
            const runId = caseInsensitive(aliquots[0], 'run');
            expect(runId).toBe(caseInsensitive(aliquots[1], 'run'));

            // move samples to top folder
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: topFolderOptions.containerPath,
                schemaName: 'samples',
                queryName: SAMPLE_TYPE_NAME_1,
                rows: aliquots.slice(1),
                auditBehavior: "DETAILED",
            }, {...subfolder3Options, ...editorUserOptions}).expect(200);

            const {updateCounts} = response.body;
            expect(updateCounts.samples).toBe(2);
            expect(updateCounts.sampleDerivationRunsUpdated).toBe(0);
            expect(updateCounts.sampleDerivationRunsSplit).toBe(1);

            // verify run is not updated for aliquot that did not move
            const sample1Data = await _getSampleData(aliquotRowIds[0], subfolder3Options, SAMPLE_TYPE_NAME_1, "RowId,Run");
            // runId should not change
            expect(sample1Data.length).toBe(1);
            expect(caseInsensitive(sample1Data[0], 'run')).toBe(runId);
            verifyRunData(runId, subfolder3Options, 'Create aliquot from top-parent-2');

            const sample2Data = await _getSampleData(aliquotRowIds[1], topFolderOptions, SAMPLE_TYPE_NAME_1, "RowId,Run");
            // runId should change
            expect(sample2Data.length).toBe(1);
            expect(caseInsensitive(sample2Data[0], 'run') !== runId).toBe(true);
            verifyRunData(caseInsensitive(sample2Data[0], 'run'), topFolderOptions, 'Create 2 aliquots from top-parent-2');

            // delete original subfolder
            await server.post('core', 'deleteContainer', undefined, {...subfolder3Options}).expect(successfulResponse);

            // verify run in top container still exists
            verifyRunData(caseInsensitive(sample2Data[0], 'run'), topFolderOptions, 'Create 2 aliquots from top-parent-2');
        });


        it('success, move samples with source and sample parents', async () => {
            // create data class
            await server.post('property', 'createDomain', {
                kind: 'DataClass',
                domainDesign: {name: SOURCE_TYPE_NAME_1, fields: [{name: 'Data'}]}
            }, topFolderOptions).expect(successfulResponse);

            const subfolder3 = await server.createTestContainer();

            const subfolder3Options = {containerPath: subfolder3.path};
            await server.addUserToRole(editorUser.username, PermissionRoles.Editor, subfolder3.path);

            // create a sample in the top folder
            await createSample(server, 'top-parent-3', editorUserOptions, topFolderOptions);

            // create a source in the top folder
            await _createSource('top-source-1', topFolderOptions);

            // create samples with only source parent in subfolder
            const sourceParentSamples = await createDerivedSamples(['sub3-src1-sample1', 'sub3-src1-sample2'], SAMPLE_TYPE_NAME_1, subfolder3Options, undefined, undefined, ['top-source-1']);
            // create samples with source and sample parent in subfolder
            const samSourceParentSamples = await createDerivedSamples(['sub3-p3-src1-sample1', 'sub3-p3-src1-sample2'], SAMPLE_TYPE_NAME_2, subfolder3Options, SAMPLE_TYPE_NAME_1, ['top-parent-3'], ['top-source-1']);

            // create parent sample in subfolder
            await createSample(server, 'sub3-parent-1', editorUserOptions, subfolder3Options);
            // create samples derived from parent in subfolder
            const sub3ParentSamples = await createDerivedSamples(['sub3-parent-sample1', 'sub3-parent-sample2', 'sub3-parent-sample3'], SAMPLE_TYPE_NAME_2, subfolder3Options, SAMPLE_TYPE_NAME_1, ['sub3-parent-1']);

            // move all source-parent-only samples, a subset of sample-and-source-parent samples, and one sample from subfolder parent to sibling
            const movingRows = [].concat(sourceParentSamples);
            movingRows.push(samSourceParentSamples[1]);
            movingRows.push(sub3ParentSamples[0]);

            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: subfolder2Options.containerPath,
                schemaName: 'samples',
                queryName: SAMPLE_TYPE_NAME_1,
                rows: movingRows,
                auditBehavior: "DETAILED",
            }, {...subfolder3Options, ...editorUserOptions}).expect(200);

            const {updateCounts} = response.body;
            expect(updateCounts.samples).toBe(4);
            expect(updateCounts.sampleDerivationRunsUpdated).toBe(1);
            expect(updateCounts.sampleDerivationRunsSplit).toBe(2);

            // verify runs
            let sampleData = await _getSampleData(caseInsensitive(sourceParentSamples[0], 'rowId'), subfolder2Options, SAMPLE_TYPE_NAME_1, "RowId,Run");
            // runId should not change when all derived samples have moved
            expect(sampleData.length).toBe(1);
            let runId = caseInsensitive(sampleData[0], 'run');
            expect(runId).toBe(caseInsensitive(sourceParentSamples[0], 'run'));
            verifyRunData(runId, subfolder2Options, 'Derive 2 samples from top-source-1');

            // runId should stay the same for subset sample that did not move
            sampleData = await _getSampleData(caseInsensitive(samSourceParentSamples[0], 'rowId'), subfolder3Options, SAMPLE_TYPE_NAME_2, 'RowId,Run');
            runId = caseInsensitive(sampleData[0], 'run')
            expect(caseInsensitive(sampleData[0], 'run')).toBe(caseInsensitive(samSourceParentSamples[0], 'run'));
            verifyRunData(caseInsensitive(sampleData[0], 'run'), subfolder3Options, 'Derive sample from top-source-1, top-parent-3');

            // new runId for subset sample that moved
            sampleData = await _getSampleData(caseInsensitive(samSourceParentSamples[1], 'rowid'), subfolder2Options, SAMPLE_TYPE_NAME_2, 'RowId,Run');
            expect(caseInsensitive(sampleData[0], 'run') !== caseInsensitive(samSourceParentSamples[1], 'run')).toBe(true);
            verifyRunData(caseInsensitive(sampleData[0], 'run'), subfolder2Options, 'Derive sample from top-source-1, top-parent-3');

            // runId should stay the same for subset sample that did not move
            sampleData = await _getSampleData(caseInsensitive(sub3ParentSamples[1], 'rowid'), subfolder3Options, SAMPLE_TYPE_NAME_2, 'RowId,Run');
            expect(caseInsensitive(sampleData[0], 'run')).toBe(caseInsensitive(sub3ParentSamples[1], 'run'));
            verifyRunData(caseInsensitive(sampleData[0], 'run'), subfolder3Options, 'Derive 2 samples from sub3-parent-1');

            // new runId for subset sample that moved
            sampleData = await _getSampleData(caseInsensitive(sub3ParentSamples[0], 'rowid'), subfolder2Options, SAMPLE_TYPE_NAME_2, 'RowId,Run');
            expect(caseInsensitive(sampleData[0], 'run') !== caseInsensitive(sub3ParentSamples[0], 'run')).toBe(true);
            verifyRunData(caseInsensitive(sampleData[0], 'run'), subfolder2Options, 'Derive sample from sub3-parent-1');

            // delete original subfolder
            await server.post('core', 'deleteContainer', undefined, {...subfolder3Options}).expect(successfulResponse);

        });
    });
});

