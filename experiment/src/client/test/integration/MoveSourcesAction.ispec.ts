import mock from 'mock-fs';
import { PermissionRoles } from '@labkey/api';
import { hookServer, RequestOptions, successfulResponse } from '@labkey/test';
import { caseInsensitive } from '@labkey/components';
import {
    ATTACHMENT_FIELD_1_NAME,
    ATTACHMENT_FIELD_2_NAME,
    createDerivedObjects,
    createSample,
    createSource,
    getExperimentRun,
    getSourceData,
    SAMPLE_TYPE_NAME_1,
    SOURCE_TYPE_NAME_1,
    SOURCE_TYPE_NAME_2,
    sourceExists
} from './utils';


const server = hookServer(process.env);
const PROJECT_NAME = 'MoveSourcesTest Project';


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

    // create a source types for use in tests
    await server.post('property', 'createDomain', {
        kind: 'DataClass',
        domainDesign: { name: SOURCE_TYPE_NAME_1, fields: [
            { name: 'Data' },
            { name: ATTACHMENT_FIELD_1_NAME, rangeURI: 'http://www.labkey.org/exp/xml#attachment'},
            { name: ATTACHMENT_FIELD_2_NAME, rangeURI: 'http://www.labkey.org/exp/xml#attachment'}
        ] }
    }, topFolderOptions).expect(successfulResponse);

    await server.post('property', 'createDomain', {
        kind: 'DataClass',
        domainDesign: { name: SOURCE_TYPE_NAME_2, fields: [
                { name: 'Location' }] }
    }, topFolderOptions).expect(successfulResponse);

});

afterAll(async () => {
    return server.teardown();
});

afterEach(() => {
    mock.restore();
});

async function createDerivedSources(names: string[], sourceTypeName: string, folderOptions: RequestOptions, parentSourceType: string, parentSources: string[], parentSampleType?: string, sampleParents?: string[], auditBehavior?: string, ) {
    return createDerivedObjects(server, names, 'exp.data', sourceTypeName, folderOptions, editorUserOptions, parentSourceType, parentSources, parentSampleType, sampleParents, auditBehavior);
}

async function createSourceWithAttachments(sourceName: string, folderOptions: RequestOptions, fileData: any, auditBehavior?: string, ) {
    const materialResponse = await server.request('query', 'insertRows', (agent, url) => {
        let request = agent.post(url);

        request = request.field('json', JSON.stringify({
            schemaName: 'exp.data',
            queryName: SOURCE_TYPE_NAME_1,
            rows: [{name: sourceName}],
            auditBehavior,
        }));

        Object.keys(fileData).forEach(fieldName => {
            request = request.attach(fieldName + "::0", fileData[fieldName]);
        });

        return request;

    }, { ...folderOptions, ...editorUserOptions }).expect(successfulResponse);
    return caseInsensitive(materialResponse.body.rows[0], 'rowId');
}

async function _createSource(sourceName: string, folderOptions: RequestOptions, auditBehavior?: string, sourceType: string = SOURCE_TYPE_NAME_1) {
    return createSource(server, sourceName, folderOptions, editorUserOptions, auditBehavior, sourceType)
}

async function _getSourceData(rowId: number, folderOptions: RequestOptions, sourceType: string = SOURCE_TYPE_NAME_1, columns: string = 'RowId') {
    return getSourceData(server, rowId, folderOptions, editorUserOptions, sourceType, columns);
}

async function getSourceRunId(rowId: number, folderOptions: RequestOptions) {
    const response = await server.post('query', 'selectRows', {
        schemaName: 'exp',
        queryName: 'data',
        'query.RowId~eq': rowId,
        // 'query.columns': 'Run/RowId',
    }, { ...folderOptions, ...editorUserOptions }).expect(successfulResponse);
    return response.body.rows[0].Run;
}

async function _sourceExists(rowId: number, folderOptions: RequestOptions, sampleType: string = SOURCE_TYPE_NAME_1) {
    return sourceExists(server, rowId, folderOptions, editorUserOptions, sampleType);
}

async function getQueryUpdateAuditLogs(sourceType: string, folderOptions: RequestOptions, expectedNumber: number) {
    const response = await server.post('query', 'selectRows', {
        schemaName: 'auditlog',
        queryName: 'queryupdateauditevent',
        'query.schemaname~eq': 'exp.data',
        'query.queryname~eq': sourceType,
        'query.columns': DEFAULT_AUDIT_LOG_COLUMNS,
        'query.sort': '-Created',
        'query.maxRows': expectedNumber
    }, { ...folderOptions  }).expect(successfulResponse);
    return response.body.rows;
}


async function getDetailedQueryUpdateAuditLogs(rowId: number, folderOptions: RequestOptions, columns: string = DEFAULT_AUDIT_LOG_COLUMNS, sourceType: string = SOURCE_TYPE_NAME_1) {
    const response = await server.post('query', 'selectRows', {
        schemaName: 'auditlog',
        queryName: 'queryupdateauditevent',
        'query.schemaname~eq': 'exp.data',
        'query.queryname~eq': sourceType,
        'query.rowPk~eq': rowId + '',
        'query.columns': columns,
        'query.sort': '-Created',
    }, { ...folderOptions  }).expect(successfulResponse);
    return response.body.rows;
}

async function getAttachmentAuditLogs(folderOptions: RequestOptions, name: string, columns: string = DEFAULT_AUDIT_LOG_COLUMNS) {
    const response = await server.post('query', 'selectRows', {
        schemaName: 'auditlog',
        queryName: 'attachmentauditevent',
        'query.attachment~eq': name,
        'query.columns': columns,
        'query.sort': '-Created',
    }, { ...folderOptions  }).expect(successfulResponse);
    return response.body.rows;
}

describe('Move Sources', () => {

    function getAbsoluteContainerPath(containerPath: string)
    {
        return containerPath.charAt(0) === '/' ? containerPath : '/' + containerPath;
    }

    async function verifyRunData(runId: number, folderOptions: RequestOptions, name: string) {
        const runData = await getExperimentRun(server, runId, folderOptions);
        expect(runData).toHaveLength(1);
        expect(caseInsensitive(runData[0], 'container/Path')).toBe(getAbsoluteContainerPath(folderOptions.containerPath));
        expect(caseInsensitive(runData[0], 'name')).toBe(name);
    }

    async function verifySummaryAuditLogs(sourceFolderOptions: RequestOptions, count: number, userComment: string = null, sourceType: string = SOURCE_TYPE_NAME_1): Promise<number>
    {
        const eventsInSource = await getQueryUpdateAuditLogs(sourceType, sourceFolderOptions, 1);
        expect(caseInsensitive(eventsInSource[0], 'Comment')).toEqual(count + " row(s) were updated.");
        const transactionId = caseInsensitive(eventsInSource[0], 'transactionId');
        expect(caseInsensitive(eventsInSource[0], 'userComment')).toBe(userComment);
        return transactionId;
    }

    async function verifyAttachmentAuditLogs(sourceFolderOptions: RequestOptions, name: string)
    {
        const attachmentEvents = await getAttachmentAuditLogs(sourceFolderOptions, name);
        expect(attachmentEvents.length).toBeGreaterThan(0);
    }

    async function verifyDetailedAuditLogs(sourceFolderOptions: RequestOptions, targetFolderOptions: RequestOptions, sourceIds: number[], transactionId: number = undefined, userComment: string = null, sourceType: string = SOURCE_TYPE_NAME_1, valueChanges: any[] = undefined)
    {
        for (const sourceRowId of sourceIds) {
            const sampleEventsInSource = await getDetailedQueryUpdateAuditLogs(sourceRowId, sourceFolderOptions, sourceType);
            expect(sampleEventsInSource).toHaveLength(0);
        }

        for (const sourceRowId of sourceIds) {
            const eventsInTarget = await getDetailedQueryUpdateAuditLogs(sourceRowId, targetFolderOptions, valueChanges ? DEFAULT_AUDIT_LOG_COLUMNS + ',OldValues,NewValues' : DEFAULT_AUDIT_LOG_COLUMNS, sourceType);
            expect(eventsInTarget).toHaveLength(2);
            expect(caseInsensitive(eventsInTarget[0], 'Comment')).toEqual("A row was updated.");
            expect(caseInsensitive(eventsInTarget[0], 'userComment')).toBe(userComment);

            if (valueChanges) {
                valueChanges.forEach(valueChange => {
                    const oldValues =  caseInsensitive(eventsInTarget[0], 'OldValues');
                    const newValues = caseInsensitive(eventsInTarget[0], 'NewValues');
                    expect(oldValues.indexOf(encodeURIComponent(valueChange.oldValue))).toBeGreaterThan(-1);
                    expect(newValues.indexOf(encodeURIComponent(valueChange.newValue))).toBeGreaterThan(-1);
                })
            }
            if (transactionId)
                expect(caseInsensitive(eventsInTarget[0], 'transactionId')).toBe(transactionId);
            expect(caseInsensitive(eventsInTarget[1], 'Comment')).toEqual("A row was inserted.");
        }
    }

    describe('Move sources via moveRows', () => {
        it('requires POST', () => {
            server.get('query', 'moveRows.api').expect(405);
        });

        it('error, no permissions', async () => {
            // Act
            const response = await server.post('query', 'moveRows.api', {}, { ...topFolderOptions, ...noPermsUserOptions }).expect(403);

            // Assert
            const { exception, success } = response.body;
            expect(success).toBe(false);
            expect(exception).toEqual('User does not have permission to perform this operation.');
        });

        it('error, requires update permissions in current', async () => {
            // Act
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: subfolder1Options.containerPath,
                schemaName: 'exp.data',
                queryName: SOURCE_TYPE_NAME_1,
                rows: [{}],
            }, { ...topFolderOptions, ...authorUserOptions }).expect(403);

            // Assert
            const { exception, success } = response.body;
            expect(success).toBe(false);
            expect(exception).toEqual('User does not have permission to perform this operation.');
        });

        it('error, missing required targetContainer param', async () => {
            // Act
            const response = await server.post('query', 'moveRows.api', {}, { ...topFolderOptions, ...editorUserOptions }).expect(400);

            // Assert
            const { exception, success } = response.body;
            expect(success).toBe(false);
            expect(exception).toEqual('A target container must be specified for the move operation.');
        });

        it('error, non-existent targetContainer param', async () => {
            // Act
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: 'BOGUS',
                schemaName: 'exp.data',
                queryName: SOURCE_TYPE_NAME_1,
            }, { ...topFolderOptions, ...editorUserOptions }).expect(400);

            // Assert
            const { exception, success } = response.body;
            expect(success).toBe(false);
            expect(exception).toEqual('The target container was not found: BOGUS.');
        });

        it('error, missing required rows param', async () => {
            // Act
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: subfolder1Options.containerPath,
                schemaName: 'exp.data',
                queryName: SOURCE_TYPE_NAME_1,
            }, { ...topFolderOptions, ...editorUserOptions }).expect(400);

            // Assert
            const { exception, success } = response.body;
            expect(success).toBe(false);
            expect(exception).toEqual("No 'rows' array supplied.");
        });

        it('error, empty rows param', async () => {
            // Act
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: subfolder1Options.containerPath,
                schemaName: 'exp.data',
                queryName: SOURCE_TYPE_NAME_1,
                rows: []
            }, { ...topFolderOptions, ...editorUserOptions }).expect(400);

            // Assert
            const { exception, success } = response.body;
            expect(success).toBe(false);
            expect(exception).toEqual("No 'rows' array supplied.");
        });

        it('error, invalid source ID', async () => {
            // Act
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: subfolder1Options.containerPath,
                schemaName: 'exp.data',
                queryName: SOURCE_TYPE_NAME_1,
                rows: [{ RowId: -1 }]
            }, { ...topFolderOptions, ...editorUserOptions }).expect(400);

            // Assert
            const { exception, success } = response.body;
            expect(success).toBe(false);
            expect(exception).toEqual('Unable to find all sources for the move operation.');
        });

        it('error, requires insert perm in targetContainer', async () => {
            // Arrange
            const sourceRowId = await _createSource('sub1-notMoved-3', subfolder1Options);

            // Act
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: topFolderOptions.containerPath,
                schemaName: 'exp.data',
                queryName: SOURCE_TYPE_NAME_1,
                rows: [{ RowId: sourceRowId }],
            }, { ...subfolder1Options, ...subEditorUserOptions }).expect(400);

            // Assert
            const { exception, success } = response.body;
            expect(success).toBe(false);
            expect(exception).toEqual("You do not have permission to move rows from '" + SOURCE_TYPE_NAME_1 + "' to the target container: " + PROJECT_NAME + ".");

            const existsInTop = await _sourceExists(sourceRowId, topFolderOptions);
            expect(existsInTop).toBe(false);
            const existsInSub1 = await _sourceExists(sourceRowId, subfolder1Options);
            expect(existsInSub1).toBe(true);
        });


        it('success, source ID not in current parent project', async () => {
            // Arrange
            const sourceRowId = await _createSource('sub1-moved-1', subfolder1Options);

            // Act
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: subfolder2Options.containerPath,
                schemaName: 'exp.data',
                queryName: SOURCE_TYPE_NAME_1,
                rows: [{ RowId: sourceRowId }],
            }, { ...topFolderOptions, ...editorUserOptions }).expect(200);

            // Assert
            const { success } = response.body;
            expect(success).toBe(true);

            const existsInTarget = await _sourceExists(sourceRowId, subfolder2Options);
            expect(existsInTarget).toBe(true);
            const existsInSub1 = await _sourceExists(sourceRowId, subfolder1Options);
            expect(existsInSub1).toBe(false);
        });

        it('success, source ID not in current subfolder', async () => {
            // Arrange
            const sourceRowId = await _createSource('top-moved-1', topFolderOptions);

            // Act
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: subfolder2Options.containerPath,
                schemaName: 'exp.data',
                queryName: SOURCE_TYPE_NAME_1,
                rows: [{ RowId: sourceRowId }],
            }, { ...subfolder1Options, ...editorUserOptions }).expect(200);

            // Assert
            const { success } = response.body;
            expect(success).toBe(true);

            const existsInTarget = await _sourceExists(sourceRowId, subfolder2Options);
            expect(existsInTarget).toBe(true);
            const existsInSub1 = await _sourceExists(sourceRowId, subfolder1Options);
            expect(existsInSub1).toBe(false);
        });


        it('success, some source IDs not in current subfolder', async () => {
            // Arrange
            const sourceRowId1 = await _createSource('top-movedTo1-1', topFolderOptions);
            const sourceRowId2 = await _createSource('sub1-notMovedTo1-1', subfolder1Options);

            // Act
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: subfolder1Options.containerPath,
                schemaName: 'exp.data',
                queryName: SOURCE_TYPE_NAME_1,
                rows: [{ rowId: sourceRowId1}, { rowId: sourceRowId2 }],
            }, {...subfolder1Options, ...editorUserOptions}).expect(200);

            // Assert
            const {success, updateCounts} = response.body;
            expect(success).toBe(true);
            expect(updateCounts.sources).toBe(1);

            const source1ExistsInTop = await _sourceExists(sourceRowId1, topFolderOptions);
            expect(source1ExistsInTop).toBe(false);
            const source1ExistsInSub1 = await _sourceExists(sourceRowId1, subfolder1Options);
            expect(source1ExistsInSub1).toBe(true);
            const source2ExistsInSub1 = await _sourceExists(sourceRowId2, subfolder1Options);
            expect(source2ExistsInSub1).toBe(true);
        });

        it('success, all source IDs in target subfolder', async () => {
            // Arrange
            const sourceRowId1 = await _createSource('sub1-notMoved-1', subfolder1Options);
            const sourceRowId2 = await _createSource('sub1-notMoved-2', subfolder1Options);

            // Act
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: subfolder1Options.containerPath,
                schemaName: 'exp.data',
                queryName: SOURCE_TYPE_NAME_1,
                rows: [{ rowId: sourceRowId1}, { rowId: sourceRowId2 }],
            }, {...topFolderOptions, ...editorUserOptions}).expect(200);

            // Assert
            const {success, updateCounts} = response.body;
            expect(success).toBe(true);
            expect(updateCounts.sources).toBeUndefined();

            const source1ExistsSub1 = await _sourceExists(sourceRowId1, subfolder1Options);
            expect(source1ExistsSub1).toBe(true);
            const source2ExistsInSub1 = await _sourceExists(sourceRowId2, subfolder1Options);
            expect(source2ExistsInSub1).toBe(true);
        });

        it('success, move from parent project to subfolder, no audit logging', async () => {
            // Arrange
            const sourceRowId = await _createSource('top-movetosub1-1', topFolderOptions);

            // Act
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: subfolder1Options.containerPath,
                schemaName: 'exp.data',
                queryName: SOURCE_TYPE_NAME_1,
                rows: [{ RowId: sourceRowId }],
            }, { ...topFolderOptions, ...editorUserOptions }).expect(200);

            // Assert
            const { updateCounts, success } = response.body;
            expect(success).toBe(true);
            expect(updateCounts.sources).toBe(1);
            expect(updateCounts.sourceAliases).toBe(0);
            expect(updateCounts.sourceAuditEvents).toBe(0);

            const existsInTop = await _sourceExists(sourceRowId, topFolderOptions);
            expect(existsInTop).toBe(false);
            const existsInSub1 = await _sourceExists(sourceRowId, subfolder1Options);
            expect(existsInSub1).toBe(true);

            await verifySummaryAuditLogs(topFolderOptions, 1);

            const eventsInTop = await getDetailedQueryUpdateAuditLogs(sourceRowId, topFolderOptions);
            expect(eventsInTop).toHaveLength(0);
            const eventsInSub1 = await getDetailedQueryUpdateAuditLogs(sourceRowId, subfolder1Options);
            expect(eventsInSub1).toHaveLength(0);
        });

        it('success, move from parent project to subfolder, detailed audit logging', async () => {
            // Arrange
            const sourceRowId = await _createSource('top-movetosub1-2', topFolderOptions, "DETAILED");

            // Act
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: subfolder1Options.containerPath,
                schemaName: 'exp.data',
                queryName: SOURCE_TYPE_NAME_1,
                rows: [{ RowId: sourceRowId }],
                auditBehavior: "DETAILED",
            }, { ...topFolderOptions, ...editorUserOptions }).expect(200);

            // Assert
            const { updateCounts, success } = response.body;
            expect(success).toBe(true);
            expect(updateCounts.sources).toBe(1);
            expect(updateCounts.sourceAliases).toBe(0);
            expect(updateCounts.sourceAuditEvents).toBe(1);

            const existsInTop = await _sourceExists(sourceRowId, topFolderOptions);
            expect(existsInTop).toBe(false);
            const existsInSub1 = await _sourceExists(sourceRowId, subfolder1Options);
            expect(existsInSub1).toBe(true);

            const auditTransactionId = await verifySummaryAuditLogs(topFolderOptions, 1);
            await verifyDetailedAuditLogs(topFolderOptions, subfolder1Options, [sourceRowId], auditTransactionId);
        });

        it('success, move from parent project to subfolder, detailed audit logging with comment', async () => {
            // Arrange
            const sourceRowId = await _createSource('top-movetosub1-3', topFolderOptions, "DETAILED");
            const userComment =  "Oops! Wrong project.";
            // Act
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: subfolder1Options.containerPath,
                schemaName: 'exp.data',
                queryName: SOURCE_TYPE_NAME_1,
                rows: [{ RowId: sourceRowId }],
                auditBehavior: "DETAILED",
                auditUserComment: userComment,
            }, { ...topFolderOptions, ...editorUserOptions }).expect(200);

            // Assert
            const { updateCounts, success } = response.body;
            expect(success).toBe(true);
            expect(updateCounts.sources).toBe(1);
            expect(updateCounts.sourceAliases).toBe(0);
            expect(updateCounts.sourceAuditEvents).toBe(1);

            const existsInTop = await _sourceExists(sourceRowId, topFolderOptions);
            expect(existsInTop).toBe(false);
            const existsInSub1 = await _sourceExists(sourceRowId, subfolder1Options);
            expect(existsInSub1).toBe(true);

            const auditTransactionId = await verifySummaryAuditLogs(topFolderOptions,  1, userComment);
            await verifyDetailedAuditLogs(topFolderOptions, subfolder1Options, [sourceRowId], auditTransactionId, userComment);
        });

        it('success, move from parent project to subfolder with summary logging', async () => {
            // Arrange
            const sourceRowId = await _createSource('top-movetosub1-4', topFolderOptions, "DETAILED");
            const userComment = "4 is in the wrong place."

            // Act
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: subfolder1Options.containerPath,
                schemaName: 'exp.data',
                queryName: SOURCE_TYPE_NAME_1,
                rows: [{ RowId: sourceRowId }],
                auditBehavior: 'SUMMARY',
                auditUserComment: userComment,
            }, { ...topFolderOptions, ...editorUserOptions }).expect(200);

            // Assert
            const { updateCounts, success } = response.body;
            expect(success).toBe(true);
            expect(updateCounts.sources).toBe(1);
            expect(updateCounts.sourceAliases).toBe(0);
            expect(updateCounts.sourceAuditEvents).toBe(1);

            const existsInTop = await _sourceExists(sourceRowId, topFolderOptions);
            expect(existsInTop).toBe(false);
            const existsInSub1 = await _sourceExists(sourceRowId, subfolder1Options);
            expect(existsInSub1).toBe(true);

            await verifySummaryAuditLogs(topFolderOptions, 1, userComment);

            const eventsInTop = await getDetailedQueryUpdateAuditLogs(sourceRowId, topFolderOptions);
            expect(eventsInTop).toHaveLength(0);
            const eventsInSub1 = await getDetailedQueryUpdateAuditLogs(sourceRowId, subfolder1Options);
            expect(eventsInSub1).toHaveLength(1);
            expect(caseInsensitive(eventsInSub1[0], 'Comment')).toEqual("A row was inserted.");
        });

        it('success, move from subfolder to parent project', async () => {
            // Arrange
            const sourceRowId = await _createSource('sub1-movetotop-1', subfolder1Options, "DETAILED");

            // Act
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: topFolderOptions.containerPath,
                schemaName: 'exp.data',
                queryName: SOURCE_TYPE_NAME_1,
                rows: [{ RowId: sourceRowId }],
                auditBehavior: "DETAILED",
            }, { ...subfolder1Options, ...editorUserOptions }).expect(200);

            // Assert
            const { updateCounts, success } = response.body;
            expect(success).toBe(true);
            expect(updateCounts.sources).toBe(1);
            expect(updateCounts.sourceAliases).toBe(0);
            expect(updateCounts.sourceAuditEvents).toBe(1);

            const existsInTop = await _sourceExists(sourceRowId, topFolderOptions);
            expect(existsInTop).toBe(true);
            const existsInSub1 = await _sourceExists(sourceRowId, subfolder1Options);
            expect(existsInSub1).toBe(false);

            const auditTransactionId = await verifySummaryAuditLogs(subfolder1Options, 1  );
            await verifyDetailedAuditLogs(subfolder1Options, topFolderOptions, [sourceRowId], auditTransactionId);

        });

        it('success, move from subfolder to sibling', async () => {
            // Arrange
            const sourceRowId = await _createSource('sub1-movetosub2-1', subfolder1Options, "DETAILED");

            // Act
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: subfolder2Options.containerPath,
                schemaName: 'exp.data',
                queryName: SOURCE_TYPE_NAME_1,
                rows: [{ RowId: sourceRowId }],
                auditBehavior: "DETAILED",
            }, { ...subfolder1Options, ...editorUserOptions }).expect(200);

            // Assert
            const { updateCounts, success } = response.body;
            expect(success).toBe(true);
            expect(updateCounts.sources).toBe(1);
            expect(updateCounts.sourceAliases).toBe(0);
            expect(updateCounts.sourceAuditEvents).toBe(1);

            const existsInSub1 = await _sourceExists(sourceRowId, subfolder1Options);
            expect(existsInSub1).toBe(false);
            const existsInSub2 = await _sourceExists(sourceRowId, subfolder2Options);
            expect(existsInSub2).toBe(true);

            const auditTransactionId = await verifySummaryAuditLogs(subfolder1Options, 1);
            await verifyDetailedAuditLogs(subfolder1Options, subfolder2Options, [sourceRowId], auditTransactionId);

        });

        it('success, move from multiple types', async () => {
            // Arrange
            const sourceRowId1 = await _createSource('sub1-movetosub2-5', subfolder1Options, "DETAILED");
            const sourceRowId2 = await _createSource('sub1-movetosub2-6', subfolder1Options, "DETAILED");
            const sourceRowId3 = await _createSource('type2-sub1-movetosub2-1', subfolder1Options, "DETAILED", SOURCE_TYPE_NAME_2);

            // Act
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: subfolder2Options.containerPath,
                schemaName: 'exp.data',
                queryName: SOURCE_TYPE_NAME_1,
                rows: [{ RowId: sourceRowId1 }, { RowId: sourceRowId2 }, { RowId: sourceRowId3 }],
                auditBehavior: "DETAILED",
            }, { ...subfolder1Options, ...editorUserOptions }).expect(200);

            // Assert
            const { updateCounts, success } = response.body;
            expect(success).toBe(true);
            expect(updateCounts.sources).toBe(3);
            expect(updateCounts.sourceAliases).toBe(0);
            expect(updateCounts.sourceAuditEvents).toBe(3);

            let existsInSub1 = await _sourceExists(sourceRowId1, subfolder1Options);
            expect(existsInSub1).toBe(false);
            existsInSub1 = await _sourceExists(sourceRowId2, subfolder1Options);
            expect(existsInSub1).toBe(false);
            existsInSub1 = await _sourceExists(sourceRowId3, subfolder1Options, SOURCE_TYPE_NAME_2);
            expect(existsInSub1).toBe(false);

            let existsInSub2 = await _sourceExists(sourceRowId1, subfolder2Options);
            expect(existsInSub2).toBe(true);
            existsInSub2 = await _sourceExists(sourceRowId2, subfolder2Options);
            expect(existsInSub2).toBe(true);
            existsInSub2 = await _sourceExists(sourceRowId3, subfolder2Options, SOURCE_TYPE_NAME_2);
            expect(existsInSub2).toBe(true);

            const auditTransactionId = await verifySummaryAuditLogs(subfolder1Options,  2);
            const auditTransactionId2 = await verifySummaryAuditLogs(subfolder1Options,  1, undefined, SOURCE_TYPE_NAME_2);
            expect(auditTransactionId2).toBe(auditTransactionId);
            await verifyDetailedAuditLogs(subfolder1Options, subfolder2Options, [sourceRowId1, sourceRowId2], auditTransactionId);
            await verifyDetailedAuditLogs(subfolder1Options, subfolder2Options, [sourceRowId3], auditTransactionId, undefined, SOURCE_TYPE_NAME_2);

        });

        it('success, move one from parent with attachment field', async () => {
            mock({
                'fileA.txt': 'fileA contents',
            });
            const sourceRowId1 = await createSourceWithAttachments('top2-movetosub1-1', topFolderOptions, {[ATTACHMENT_FIELD_1_NAME]: 'fileA.txt'}, "DETAILED");
            const userComment = "Moving files too";
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: subfolder1Options.containerPath,
                schemaName: 'exp.data',
                queryName: SOURCE_TYPE_NAME_1,
                rows: [{ RowId: sourceRowId1 }],
                auditBehavior: "DETAILED",
                auditUserComment: userComment,
            }, { ...topFolderOptions, ...editorUserOptions }).expect(200);

            // Assert
            const { updateCounts, success } = response.body;
            expect(success).toBe(true);
            expect(updateCounts.sources).toBe(1);
            const data = await _getSourceData(sourceRowId1, subfolder1Options, SOURCE_TYPE_NAME_1, "RowId," + ATTACHMENT_FIELD_1_NAME);

            expect(data.length).toBe(1);
            expect(data[0][ATTACHMENT_FIELD_1_NAME]).toEqual("fileA.txt");

            await verifyDetailedAuditLogs(topFolderOptions, subfolder1Options, [sourceRowId1], undefined, userComment);

        });

        it('success, move from subfolder with multiple attachment fields', async () => {
            mock({
                'fileB.txt': 'fileB contents',
                'fileC.txt': 'fileC contents',
            });
            const sourceRowId1 = await createSourceWithAttachments('sub12-movetotop-1', subfolder1Options, {[ATTACHMENT_FIELD_1_NAME]: 'fileB.txt', [ATTACHMENT_FIELD_2_NAME]: 'fileC.txt'}, "DETAILED");

            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: topFolderOptions.containerPath,
                schemaName: 'exp.data',
                queryName: SOURCE_TYPE_NAME_1,
                rows: [{ RowId: sourceRowId1 }],
                auditBehavior: "DETAILED",
            }, { ...subfolder1Options, ...editorUserOptions }).expect(200);

            // Assert
            const { updateCounts, success } = response.body;
            expect(success).toBe(true);
            expect(updateCounts.sources).toBe(1);
            const sampleData = await _getSourceData(sourceRowId1, topFolderOptions, SOURCE_TYPE_NAME_1, "RowId," + ATTACHMENT_FIELD_1_NAME + "," + ATTACHMENT_FIELD_2_NAME);
            expect(sampleData.length).toBe(1);
            expect(sampleData[0][ATTACHMENT_FIELD_1_NAME]).toBe("fileB.txt");
            expect(sampleData[0][ATTACHMENT_FIELD_2_NAME]).toBe("fileC.txt");

            await verifyAttachmentAuditLogs(subfolder1Options, "fileB.txt");
            await verifyAttachmentAuditLogs(subfolder1Options, "fileC.txt");
            await verifyDetailedAuditLogs(subfolder1Options, topFolderOptions, [sourceRowId1]);
        });

        it('success, move multiple sources', async () => {
            // Arrange
            const sourceRowId1 = await _createSource('sub1-movetosub2-2', subfolder1Options, "DETAILED");
            const sourceRowId2 = await _createSource('sub1-movetosub2-3', subfolder1Options, "DETAILED");
            const sourceRowId3 = await _createSource('sub1-movetosub2-4', subfolder1Options, "DETAILED");

            // Act
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: subfolder2Options.containerPath,
                schemaName: 'exp.data',
                queryName: SOURCE_TYPE_NAME_1,
                rows: [{ RowId: sourceRowId1 }, { RowId: sourceRowId2 }, { RowId: sourceRowId3 }],
                auditBehavior: "DETAILED",
            }, { ...subfolder1Options, ...editorUserOptions }).expect(200);

            // Assert
            const { updateCounts, success } = response.body;
            expect(success).toBe(true);
            expect(updateCounts.sources).toBe(3);
            expect(updateCounts.sourceAliases).toBe(0);
            expect(updateCounts.sourceAuditEvents).toBe(3);

            let existsInSub1 = await _sourceExists(sourceRowId1, subfolder1Options);
            expect(existsInSub1).toBe(false);
            existsInSub1 = await _sourceExists(sourceRowId2, subfolder1Options);
            expect(existsInSub1).toBe(false);
            existsInSub1 = await _sourceExists(sourceRowId3, subfolder1Options);
            expect(existsInSub1).toBe(false);

            let existsInSub2 = await _sourceExists(sourceRowId1, subfolder2Options);
            expect(existsInSub2).toBe(true);
            existsInSub2 = await _sourceExists(sourceRowId2, subfolder2Options);
            expect(existsInSub2).toBe(true);
            existsInSub2 = await _sourceExists(sourceRowId3, subfolder2Options);
            expect(existsInSub2).toBe(true);

            const auditTransactionId = await verifySummaryAuditLogs(subfolder1Options,  3);
            await verifyDetailedAuditLogs(subfolder1Options, subfolder2Options, [sourceRowId1, sourceRowId2, sourceRowId3], auditTransactionId);

            // verify that we are able to delete the original container after things have moved
            await server.post('core', 'deleteContainer', undefined, { ...subfolder1Options }).expect(successfulResponse);
        });

        it('success, move all child derived samples to sibling', async () => {
            const subfolder3 = await server.createTestContainer();

            const subfolder3Options = { containerPath: subfolder3.path };
            await server.addUserToRole(editorUser.username, PermissionRoles.Editor, subfolder3.path);

            // create a source in the top folder
            const sourceRowId1 = await _createSource('top-parent-1', topFolderOptions, "DETAILED");

            // derive sources into new subfolder
            const derivedSources = await createDerivedSources(
                ['sub3-derived-1', 'sub3-derived-2'],
                SOURCE_TYPE_NAME_2,
                subfolder3Options,
                SOURCE_TYPE_NAME_1,
                [sourceRowId1]
            );

            const rowIdsToMove = derivedSources.map(data => data.rowId);
            // the two sources should have the same runId
            const runId1Before = await getSourceRunId(rowIdsToMove[0], subfolder3Options);
            const runId2Before = await getSourceRunId(rowIdsToMove[1], subfolder3Options);
            expect(runId1Before).toBe(runId2Before);

            // move sources to sibling folder
            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: subfolder2Options.containerPath,
                schemaName: 'exp.data',
                queryName: SOURCE_TYPE_NAME_1,
                rows: derivedSources,
                auditBehavior: "DETAILED",
            }, { ...subfolder3Options, ...editorUserOptions }).expect(200);

            const { updateCounts, success } = response.body;
            expect(success).toBe(true);
            expect(updateCounts.sources).toBe(2);
            expect(updateCounts.sourceDerivationRunsUpdated).toBe(1);
            expect(updateCounts.sourceDerivationRunsSplit).toBe(0);

            // verify run container is updated
            const runId1After = await getSourceRunId(rowIdsToMove[0], subfolder2Options);
            expect(runId1After).toBe(runId1Before);

            const runId2After = await getSourceRunId(rowIdsToMove[1], subfolder2Options);
            // runId should not change
            expect(runId2After).toBe(runId1Before);

            verifyRunData(runId1Before, subfolder2Options, 'Derive 2 data from top-parent-1');

            // delete original subfolder
            await server.post('core', 'deleteContainer', undefined, { ...subfolder3Options }).expect(successfulResponse);
            verifyRunData(runId1Before, subfolder2Options, 'Derive 2 data from top-parent-1');
        });

        it ('success, move source with source and sample parents', async () => {
            // create a sample type
            await server.post('property', 'createDomain', {
                kind: 'SampleSet',
                domainDesign: { name: SAMPLE_TYPE_NAME_1, fields: [{ name: 'Name',  }] }
            }, topFolderOptions).expect(successfulResponse);

            const subfolder3 = await server.createTestContainer();

            const subfolder3Options = { containerPath: subfolder3.path };
            await server.addUserToRole(editorUser.username, PermissionRoles.Editor, subfolder3.path);

            // create a sample in the top folder
            await createSample(server, 'top-parent-3', topFolderOptions, editorUserOptions);

            // create a source in the top folder
            await _createSource('top-source-1', topFolderOptions);

            // create samples with only source parent in subfolder
            const sourceParentSources = await createDerivedSources(['sub3-src1-source1', 'sub3-src1-source2'], SOURCE_TYPE_NAME_1, subfolder3Options, SOURCE_TYPE_NAME_1, ['top-source-1']);
            // create sources with source and sample parent in subfolder
            const samSourceParentSources = await createDerivedSources(['sub3-p3-src1-source1', 'sub3-p3-src1-source2'], SOURCE_TYPE_NAME_2, subfolder3Options, SOURCE_TYPE_NAME_1, ['top-source-1'], SAMPLE_TYPE_NAME_1, ['top-parent-3'], );

            // create parent source in subfolder
            await _createSource('sub3-parent-1', subfolder3Options);
            // create sources derived from parent in subfolder
            const sub3ParentSamples = await createDerivedSources(['sub3-parent-source1', 'sub3-parent-source2', 'sub3-parent-source3'], SOURCE_TYPE_NAME_2, subfolder3Options, SOURCE_TYPE_NAME_1, ['sub3-parent-1']);

            // move all source-parent-only sources, a subset of sample-and-source-parent sources, and one source from subfolder parent to sibling
            const movingRowIds = sourceParentSources.map(s => caseInsensitive(s, 'rowId'));
            movingRowIds.push(caseInsensitive(samSourceParentSources[1], 'rowId'));
            movingRowIds.push(caseInsensitive(sub3ParentSamples[0], 'rowId'));
            const movingId1Before = await getSourceRunId(movingRowIds[0], subfolder3Options);
            const notMovingId2Before = await getSourceRunId(caseInsensitive(samSourceParentSources[0], 'rowId'), subfolder3Options);
            const movingId2Before = await getSourceRunId(caseInsensitive(samSourceParentSources[1], 'rowId'), subfolder3Options);
            const movingId3Before = await getSourceRunId(caseInsensitive(sub3ParentSamples[0], 'rowId'), subfolder3Options);
            const notMovingId3Before = await getSourceRunId(caseInsensitive(sub3ParentSamples[1], 'rowId'), subfolder3Options);

            const response = await server.post('query', 'moveRows.api', {
                targetContainerPath: subfolder2Options.containerPath,
                schemaName: 'exp.data',
                queryName: SOURCE_TYPE_NAME_1,
                rows: movingRowIds.reduce((prev, curr) => {
                    prev.push({ RowId: curr });
                    return prev;
                }, []),
                auditBehavior: "DETAILED",
            }, { ...subfolder3Options, ...editorUserOptions }).expect(200);

            const { updateCounts, success } = response.body;
            expect(success).toBe(true);
            expect(updateCounts.sources).toBe(4);
            expect(updateCounts.sourceDerivationRunsUpdated).toBe(1);
            expect(updateCounts.sourceDerivationRunsSplit).toBe(2);

            // verify runs
            let runIdAfter = await getSourceRunId(movingRowIds[0], subfolder2Options);
            // runId should not change when all derived sources have moved
            expect(runIdAfter).toBe(movingId1Before);
            verifyRunData(runIdAfter, subfolder2Options, 'Derive 2 data from top-source-1');

            // runId should stay the same for subset sources that did not move
            runIdAfter = await getSourceRunId(caseInsensitive(samSourceParentSources[0], 'rowId'), subfolder3Options);
            expect(runIdAfter).toBe(notMovingId2Before);
            verifyRunData(runIdAfter, subfolder3Options, 'Derive data from top-source-1, top-parent-3');

            // new runId for subset source that moved
            runIdAfter = await getSourceRunId(caseInsensitive(samSourceParentSources[1], 'rowid'), subfolder2Options);
            expect(runIdAfter !== movingId2Before).toBe(true);
            verifyRunData(runIdAfter, subfolder2Options, 'Derive data from top-source-1, top-parent-3');

            // runId should stay the same for subset source that did not move
            runIdAfter = await getSourceRunId(caseInsensitive(sub3ParentSamples[1], 'rowid'), subfolder3Options);
            expect(runIdAfter).toBe(notMovingId3Before);
            verifyRunData(runIdAfter, subfolder3Options, 'Derive 2 data from sub3-parent-1');

            // new runId for subset source that moved
            runIdAfter = await getSourceRunId(caseInsensitive(sub3ParentSamples[0], 'rowid'), subfolder2Options);
            expect(runIdAfter !== movingId3Before).toBe(true);
            verifyRunData(runIdAfter, subfolder2Options, 'Derive data from sub3-parent-1');

            // delete original subfolder
            await server.post('core', 'deleteContainer', undefined, { ...subfolder3Options }).expect(successfulResponse);

        });
    });
});
