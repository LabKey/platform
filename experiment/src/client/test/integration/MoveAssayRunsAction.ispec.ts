import mock from 'mock-fs';
import { hookServer, RequestOptions, SecurityRole, successfulResponse } from '@labkey/test';
import {
    getAssayResults,
    getAssayRunMovedAuditLogs,
    importRun,
    runExists,
    uploadAssayFile,
} from "./utils";
import { caseInsensitive } from "@labkey/components";


const server = hookServer(process.env);
const CONTROLLER_NAME = 'assay'
const ACTION_NAME = "moveAssayRuns.api";
const PROJECT_NAME = 'MoveAssayRunsTest Project';

let topFolderOptions: RequestOptions;
let subfolder1Options;
let subfolder2Options;

let editorUserOptions: RequestOptions;
let editorUser;
let authorUserOptions: RequestOptions;
let subEditorUserOptions: RequestOptions;
let noPermsUserOptions: RequestOptions;

let assayAId, assayWithRunFileId, assayWithResultFileId;

const getAssayDesignPayload = (name: string, runFields: [], resultFields: []) => {
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

beforeAll(async () => {
    await server.init(PROJECT_NAME, {
        ensureModules: ['experiment', 'assay', "filecontent"],
    });
    topFolderOptions = { containerPath: PROJECT_NAME };

    // create subfolders to use in tests
    const subfolder1 = await server.createTestContainer();
    subfolder1Options = { containerPath: subfolder1.path };
    const subfolder2 = await server.createTestContainer();
    subfolder2Options = { containerPath: subfolder2.path };

    // create users with different permissions
    editorUser = await server.createUser('test_editor@expctrltest.com', 'pwSuperA1!');
    await server.addUserToRole(editorUser.username, SecurityRole.Editor, PROJECT_NAME);
    await server.addUserToRole(editorUser.username, SecurityRole.Editor, subfolder1.path);
    await server.addUserToRole(editorUser.username, SecurityRole.Editor, subfolder2.path);
    editorUserOptions = { requestContext: await server.createRequestContext(editorUser) };

    const subEditorUser = await server.createUser('test_subeditor@expctrltest.com', 'pwSuperA1!');
    await server.addUserToRole(subEditorUser.username, SecurityRole.Reader, PROJECT_NAME);
    await server.addUserToRole(subEditorUser.username, SecurityRole.Editor, subfolder1.path);
    await server.addUserToRole(subEditorUser.username, SecurityRole.Editor, subfolder2.path);
    subEditorUserOptions = { requestContext: await server.createRequestContext(subEditorUser) };

    const authorUser = await server.createUser('test_author@expctrltest.com', 'pwSuperA1!');
    await server.addUserToRole(authorUser.username, SecurityRole.Author, PROJECT_NAME);
    await server.addUserToRole(authorUser.username, SecurityRole.Author, subfolder1.path);
    await server.addUserToRole(authorUser.username, SecurityRole.Author, subfolder2.path);
    authorUserOptions = { requestContext: await server.createRequestContext(authorUser) };

    const noPermsUser = await server.createUser('test_no_perms@expctrltest.com', 'pwSuperA1!');
    noPermsUserOptions = { requestContext: await server.createRequestContext(noPermsUser) };

    const runFileField = {
        "defaultValueType": "LAST_ENTERED",
        "name": "RunFileField",
        "rangeURI": "http://cpas.fhcrc.org/exp/xml#fileLink",
        shownInDetailsView: true,
        shownInInsertView: true,
        shownInUpdateView: true,
        "scale": 4000,
    }

    const resultFileField = {
        "defaultValueType": "LAST_ENTERED",
        "name": "ResultFileField",
        "rangeURI": "http://cpas.fhcrc.org/exp/xml#fileLink",
        shownInDetailsView: true,
        shownInInsertView: true,
        shownInUpdateView: true,
        "scale": 4000,
    }

    const resultPropField = {
        conditionalFormats: [],
        name: "Prop",
        scale: 4000,
        shownInDetailsView: true,
        shownInInsertView: true,
        shownInUpdateView: true,
        isPrimaryKey: false,
    }

    // create an assay design at project container for use in tests
    const assayA = await server.post('assay', 'saveProtocol.api', getAssayDesignPayload('assayA', [], [resultPropField]), topFolderOptions).expect(successfulResponse);
    assayAId = assayA.body['data']['protocolId'];
    // create a second assay design with run file property at project container for use in tests
    const assayWithRunFile = await server.post('assay', 'saveProtocol.api', getAssayDesignPayload('assayWithRunFile', [runFileField], [resultPropField]), topFolderOptions).expect(successfulResponse);
    assayWithRunFileId = assayWithRunFile.body['data']['protocolId'];

    // create a third assay design with result file property at project container for use in tests
    const assayWithResultFile = await server.post('assay', 'saveProtocol.api', getAssayDesignPayload('assayWithResultFile', [runFileField], [resultPropField, resultFileField]), topFolderOptions).expect(successfulResponse);
    assayWithResultFileId = assayWithResultFile.body['data']['protocolId'];
});

afterAll(async () => {
    return server.teardown();
});

afterEach(() => {
    mock.restore();
});


describe(CONTROLLER_NAME, () => {

    describe(ACTION_NAME, () => {

        it('requires POST', () => {
            server.get(CONTROLLER_NAME, ACTION_NAME).expect(405);
        });

        it('error, no permissions', async () => {
            // Act
            const response = await server.post(CONTROLLER_NAME, ACTION_NAME, {}, {...topFolderOptions, ...noPermsUserOptions}).expect(403);

            // Assert
            const {exception, success} = response.body;
            expect(success).toBe(false);
            expect(exception).toEqual('User does not have permission to perform this operation.');
        });

        it('error, requires update permissions in current', async () => {
            // Act
            const response = await server.post(CONTROLLER_NAME, ACTION_NAME, {}, {...topFolderOptions, ...authorUserOptions}).expect(403);

            // Assert
            const {exception, success} = response.body;
            expect(success).toBe(false);
            expect(exception).toEqual('User does not have permission to perform this operation.');
        });


        it('error, empty assay run IDs param', async () => {
            // Act
            const response = await server.post(CONTROLLER_NAME, ACTION_NAME, {
                targetContainer: subfolder1Options.containerPath,
                rowIds: []
            }, {...topFolderOptions, ...editorUserOptions}).expect(400);

            // Assert
            const {exception, success} = response.body;
            expect(success).toBe(false);
            expect(exception).toEqual('Run IDs must be specified for the move operation.');
        });

        it('error, invalid run ID', async () => {
            // Act
            const response = await server.post(CONTROLLER_NAME, ACTION_NAME, {
                targetContainer: subfolder1Options.containerPath,
                rowIds: [-1]
            }, {...topFolderOptions, ...editorUserOptions}).expect(400);

            // Assert
            const {exception, success} = response.body;
            expect(success).toBe(false);
            expect(exception).toEqual('Unable to find all runs for the move operation.');
        });

        it('error, run ID not in current project', async () => {
            // Arrange
            const { runId } = await importRun(server, assayAId, 'sub1-notmoved-1', [{"Prop":"ABC"}], subfolder1Options, editorUserOptions);

            // Act
            const response = await server.post(CONTROLLER_NAME, ACTION_NAME, {
                targetContainer: subfolder1Options.containerPath,
                rowIds: [runId]
            }, {...topFolderOptions, ...editorUserOptions}).expect(400);

            // Assert
            const {exception, success} = response.body;
            expect(success).toBe(false);
            expect(exception).toEqual('All assay runs must be from the current container for the move operation.');

            const runExistsInTop = await runExists(server, runId, topFolderOptions);
            expect(runExistsInTop).toBe(false);
            const runExistsInSub1 = await runExists(server, runId, subfolder1Options);
            expect(runExistsInSub1).toBe(true);
        });

        it('success, move run from parent project to subfolder, no audit logging', async () => {
            // Arrange
            const { runId } = await importRun(server, assayAId, 'top-movetosub1-1', [{"Prop":"ABC"}], topFolderOptions, editorUserOptions);

            // Act
            const response = await server.post(CONTROLLER_NAME, ACTION_NAME, {
                targetContainer: subfolder1Options.containerPath,
                rowIds: [runId],
            }, {...topFolderOptions, ...editorUserOptions}).expect(200);

            // Assert
            const {updateCounts, success} = response.body;
            expect(success).toBe(true);
            expect(updateCounts.experiments).toBe(1);
            expect(updateCounts.experimentRuns).toBe(1);
            expect(updateCounts.expObject).toBe(2);
            expect(updateCounts.expData).toBe(1);
            expect(updateCounts.auditEvents).toBe(2); // one for run loaded, one for adding run to batch

            const movedEventInTop = await getAssayRunMovedAuditLogs(server, 'assayA', 'top-movetosub1-1', null, topFolderOptions);
            expect(movedEventInTop.length).toBe(0);

            const movedEventInSub1 = await getAssayRunMovedAuditLogs(server, 'assayA', 'top-movetosub1-1', null, subfolder1Options);
            expect(movedEventInSub1.length).toBe(0);

            const runExistsInTop = await runExists(server, runId, topFolderOptions);
            expect(runExistsInTop).toBe(false);
            const runExistsInSub1 = await runExists(server, runId, subfolder1Options);
            expect(runExistsInSub1).toBe(true);
        });

        it('success, move run from parent project to subfolder, detailed audit logging with comment', async () => {
            // Arrange
            const { runId } = await importRun(server, assayAId, 'top-movetosub1-3', [{"Prop":"ABC"}], topFolderOptions, editorUserOptions);
            const userComment = "Oops! Wrong project.";
            // Act
            const response = await server.post(CONTROLLER_NAME, ACTION_NAME, {
                targetContainer: subfolder1Options.containerPath,
                rowIds: [runId],
                auditBehavior: "DETAILED",
                userComment
            }, {...topFolderOptions, ...editorUserOptions}).expect(200);

            // Assert
            const {updateCounts, success} = response.body;
            expect(success).toBe(true);
            expect(updateCounts.experiments).toBe(1);
            expect(updateCounts.experimentRuns).toBe(1);
            expect(updateCounts.expObject).toBe(2);
            expect(updateCounts.expData).toBe(1);
            expect(updateCounts.auditEvents).toBe(2); // one for run loaded, one for adding run to batch

            const movedEventInTop = await getAssayRunMovedAuditLogs(server, 'assayA', 'top-movetosub1-3', userComment, topFolderOptions);
            expect(movedEventInTop.length).toBe(0);

            const movedEventInSub1 = await getAssayRunMovedAuditLogs(server, 'assayA', 'top-movetosub1-3', userComment, subfolder1Options);
            expect(movedEventInSub1.length).toBe(1);

            const runExistsInTop = await runExists(server, runId, topFolderOptions);
            expect(runExistsInTop).toBe(false);
            const runExistsInSub1 = await runExists(server, runId, subfolder1Options);
            expect(runExistsInSub1).toBe(true);

        });

        it('success, move run from sub project to parent project, summary audit logging with comment', async () => {
            // Arrange
            const { runId } = await importRun(server, assayAId, 'sub1-movetotop-31', [{"Prop":"ABC"}], subfolder1Options, editorUserOptions);
            const userComment = "Oops! Wrong child project.";
            // Act
            const response = await server.post(CONTROLLER_NAME, ACTION_NAME, {
                targetContainer: topFolderOptions.containerPath,
                rowIds: [runId],
                auditBehavior: "SUMMARY",
                userComment
            }, {...subfolder1Options, ...editorUserOptions}).expect(200);

            // Assert
            const {updateCounts, success} = response.body;
            expect(success).toBe(true);
            expect(updateCounts.experiments).toBe(1);
            expect(updateCounts.experimentRuns).toBe(1);
            expect(updateCounts.expObject).toBe(2);
            expect(updateCounts.expData).toBe(1);
            expect(updateCounts.auditEvents).toBe(2); // one for run loaded, one for adding run to batch

            const movedEventInTop = await getAssayRunMovedAuditLogs(server, 'assayA', 'sub1-movetotop-31', userComment, topFolderOptions);
            expect(movedEventInTop.length).toBe(1);

            const movedEventInSub1 = await getAssayRunMovedAuditLogs(server, 'assayA', 'sub1-movetotop-31', userComment, subfolder1Options);
            expect(movedEventInSub1.length).toBe(0);

            const runExistsInTop = await runExists(server, runId, topFolderOptions);
            expect(runExistsInTop).toBe(true);
            const runExistsInSub1 = await runExists(server, runId, subfolder1Options);
            expect(runExistsInSub1).toBe(false);

        });

        it('success, move run from child to sibling, detailed audit logging with comment', async () => {
            // Arrange
            const { runId } = await importRun(server, assayAId, 'sub2-movetosub1-1', [{"Prop":"ABC"}], subfolder2Options, editorUserOptions);
            const userComment = "Oops! Wrong sub project.";
            // Act
            const response = await server.post(CONTROLLER_NAME, ACTION_NAME, {
                targetContainer: subfolder1Options.containerPath,
                rowIds: [runId],
                auditBehavior: "DETAILED",
                userComment
            }, {...subfolder2Options, ...editorUserOptions}).expect(200);

            // Assert
            const {updateCounts, success} = response.body;
            expect(success).toBe(true);
            expect(updateCounts.experiments).toBe(1);
            expect(updateCounts.experimentRuns).toBe(1);
            expect(updateCounts.expObject).toBe(2);
            expect(updateCounts.expData).toBe(1);
            expect(updateCounts.auditEvents).toBe(2); // one for run loaded, one for adding run to batch

            const movedEventInSub1 = await getAssayRunMovedAuditLogs(server, 'assayA', 'sub2-movetosub1-1', userComment, subfolder1Options);
            expect(movedEventInSub1.length).toBe(1);

            const movedEventInSub2 = await getAssayRunMovedAuditLogs(server, 'assayA', 'sub2-movetosub1-1', userComment, subfolder2Options);
            expect(movedEventInSub2.length).toBe(0);

            const runExistsInTop = await runExists(server, runId, subfolder1Options);
            expect(runExistsInTop).toBe(true);
            const runExistsInSub1 = await runExists(server, runId, subfolder2Options);
            expect(runExistsInSub1).toBe(false);

        });

        it('success, move a re-run that replaced an existing run from parent project to subfolder', async () => {
            // Arrange
            // first run
            const { runId } = await importRun(server, assayAId, 'top-movetosub1-4-original', [{"Prop":"ABC"}], topFolderOptions, editorUserOptions);
            // re-import run
            const rerun = await importRun(server, assayAId, 'top-movetosub1-4-rerun', [{"Prop":"ABC"}], topFolderOptions, editorUserOptions, runId);
            const rerunId = rerun.runId;

            const userComment = "Oops! Wrong project.";
            // Act
            const response = await server.post(CONTROLLER_NAME, ACTION_NAME, {
                targetContainer: subfolder1Options.containerPath,
                rowIds: [rerunId],
                auditBehavior: "DETAILED",
                userComment
            }, {...topFolderOptions, ...editorUserOptions}).expect(200);

            // Assert
            const {updateCounts, success} = response.body;
            expect(success).toBe(true);
            expect(updateCounts.experiments).toBe(2);
            expect(updateCounts.experimentRuns).toBe(2);
            expect(updateCounts.expObject).toBe(4);
            expect(updateCounts.expData).toBe(2);
            expect(updateCounts.auditEvents).toBe(5); // 2 for run loaded, 2 for adding run to batch, 1 for replace

            const movedOriginalEventInTop = await getAssayRunMovedAuditLogs(server, 'assayA', 'top-movetosub1-4-original', userComment, topFolderOptions);
            expect(movedOriginalEventInTop.length).toBe(0);
            const movedRerunEventInTop = await getAssayRunMovedAuditLogs(server, 'assayA', 'top-movetosub1-4-rerun', userComment, topFolderOptions);
            expect(movedRerunEventInTop.length).toBe(0);

            const movedOriginalEventInSub1 = await getAssayRunMovedAuditLogs(server, 'assayA', 'top-movetosub1-4-original', userComment, subfolder1Options);
            expect(movedOriginalEventInSub1.length).toBe(1);
            const movedRerunEventInSub1 = await getAssayRunMovedAuditLogs(server, 'assayA', 'top-movetosub1-4-rerun', userComment, subfolder1Options);
            expect(movedRerunEventInSub1.length).toBe(1);

            const runExistsInTop = await runExists(server, runId, topFolderOptions);
            expect(runExistsInTop).toBe(false);
            const rerunExistsInTop = await runExists(server, rerunId, topFolderOptions);
            expect(rerunExistsInTop).toBe(false);
            const runExistsInSub1 = await runExists(server, runId, subfolder1Options);
            expect(runExistsInSub1).toBe(true);
            const rerunExistsInSub1 = await runExists(server, rerunId, subfolder1Options);
            expect(rerunExistsInSub1).toBe(true);

        });

        it('error, move one run in a batch that contains multiple runs', async () => {
            // Arrange
            // first run
            const { runId, batchId } = await importRun(server, assayAId, 'top-movetosub1-7-1', [{"Prop":"ABC"}], topFolderOptions, editorUserOptions);
            // add another run to the batch
            const run2 = await importRun(server, assayAId, 'top-movetosub1-7-2', [{"Prop":"ABC"}], topFolderOptions, editorUserOptions, undefined, batchId);
            const run2Id = run2.runId;

            const userComment = "Oops! Wrong project.";
            // Act
            const response = await server.post(CONTROLLER_NAME, ACTION_NAME, {
                targetContainer: subfolder1Options.containerPath,
                rowIds: [runId],
                auditBehavior: "DETAILED",
                userComment
            }, {...topFolderOptions, ...editorUserOptions}).expect(200);

            // Assert
            const {error, success} = response.body;
            expect(success).toBe(false);
            expect(error).toEqual('All runs from the same batch must be selected for move operation.');
        });

        it('success, move all runs in a batch from parent project to subfolder', async () => {
            // Arrange
            // first run
            const { runId, batchId } = await importRun(server, assayAId, 'top-movetosub1-5-1', [{"Prop":"ABC"}], topFolderOptions, editorUserOptions);
            // add another run to the batch
            const run2 = await importRun(server, assayAId, 'top-movetosub1-5-2', [{"Prop":"ABC"}], topFolderOptions, editorUserOptions, undefined, batchId);
            const run2Id = run2.runId;

            const userComment = "Oops! Wrong project.";
            // Act
            const response = await server.post(CONTROLLER_NAME, ACTION_NAME, {
                targetContainer: subfolder1Options.containerPath,
                rowIds: [runId, run2Id],
                auditBehavior: "DETAILED",
                userComment
            }, {...topFolderOptions, ...editorUserOptions}).expect(200);

            // Assert
            const {updateCounts, success} = response.body;
            expect(success).toBe(true);
            expect(updateCounts.experiments).toBe(1);
            expect(updateCounts.experimentRuns).toBe(2);
            expect(updateCounts.expObject).toBe(4);
            expect(updateCounts.expData).toBe(2);
            expect(updateCounts.auditEvents).toBe(4); // 2 for run loaded, 2 for adding run to batch

            const movedOriginalEventInTop = await getAssayRunMovedAuditLogs(server, 'assayA', 'top-movetosub1-5-1', userComment, topFolderOptions);
            expect(movedOriginalEventInTop.length).toBe(0);
            const movedRerunEventInTop = await getAssayRunMovedAuditLogs(server, 'assayA', 'top-movetosub1-5-2', userComment, topFolderOptions);
            expect(movedRerunEventInTop.length).toBe(0);

            const movedOriginalEventInSub1 = await getAssayRunMovedAuditLogs(server, 'assayA', 'top-movetosub1-5-1', userComment, subfolder1Options);
            expect(movedOriginalEventInSub1.length).toBe(1);
            const movedRerunEventInSub1 = await getAssayRunMovedAuditLogs(server, 'assayA', 'top-movetosub1-5-2', userComment, subfolder1Options);
            expect(movedRerunEventInSub1.length).toBe(1);

            const runExistsInTop = await runExists(server, runId, topFolderOptions);
            expect(runExistsInTop).toBe(false);
            const run2ExistsInTop = await runExists(server, run2Id, topFolderOptions);
            expect(run2ExistsInTop).toBe(false);
            const runExistsInSub1 = await runExists(server, runId, subfolder1Options);
            expect(runExistsInSub1).toBe(true);
            const run2ExistsInSub1 = await runExists(server, run2Id, subfolder1Options);
            expect(run2ExistsInSub1).toBe(true);

        });

    });

    it('success, move run with run file field', async () => {
        // Arrange
        const { runId } = await importRun(server, assayWithRunFileId, 'top-movetosub1-8', [{"Prop":"ABC"}], topFolderOptions, editorUserOptions);
        mock({
            'runFileA.txt': 'fileA contents',
        });
        await uploadAssayFile(server, 'assayWithRunFile', runId, true, 'RunFileField', 'runFileA.txt', topFolderOptions, editorUserOptions)

        const userComment = "Oops! Wrong project.";
        // Act
        const response = await server.post(CONTROLLER_NAME, ACTION_NAME, {
            targetContainer: subfolder1Options.containerPath,
            rowIds: [runId],
            auditBehavior: "DETAILED",
            userComment
        }, {...topFolderOptions, ...editorUserOptions}).expect(200);

        // Assert
        const {updateCounts, success} = response.body;
        expect(success).toBe(true);
        expect(updateCounts.experiments).toBe(1);
        expect(updateCounts.experimentRuns).toBe(1);
        expect(updateCounts.expObject).toBe(2);
        expect(updateCounts.expData).toBe(1);
        expect(updateCounts.movedFiles).toBe(1);
        expect(updateCounts.auditEvents).toBe(3); // one for run loaded, one for updateRows for file, one for adding run to batch

        const movedEventInTop = await getAssayRunMovedAuditLogs(server, 'assayWithRunFile', 'top-movetosub1-8', userComment, topFolderOptions);
        expect(movedEventInTop.length).toBe(0);

        const movedEventInSub1 = await getAssayRunMovedAuditLogs(server, 'assayWithRunFile', 'top-movetosub1-8', userComment, subfolder1Options);
        expect(movedEventInSub1.length).toBe(1);

        const runExistsInTop = await runExists(server, runId, topFolderOptions);
        expect(runExistsInTop).toBe(false);
        const runExistsInSub1 = await runExists(server, runId, subfolder1Options);
        expect(runExistsInSub1).toBe(true);

    });

    it('success, move run with result file field', async () => {
        // Arrange
        const { runId } = await importRun(server, assayWithResultFileId, 'top-movetosub1-9', [{"Prop":"ABC"}], topFolderOptions, editorUserOptions);
        const assayResults = await getAssayResults(server, 'assayWithResultFile', 'ResultFileField', runId, topFolderOptions);
        const resultRowId = caseInsensitive(assayResults[0], 'rowid');
        mock({
            'resultFileA.txt': 'fileA contents',
        });
        await uploadAssayFile(server, 'assayWithResultFile', resultRowId, false, 'ResultFileField', 'resultFileA.txt', topFolderOptions, editorUserOptions)

        const userComment = "Oops! Wrong project.";
        // Act
        const response = await server.post(CONTROLLER_NAME, ACTION_NAME, {
            targetContainer: subfolder1Options.containerPath,
            rowIds: [runId],
            auditBehavior: "DETAILED",
            userComment
        }, {...topFolderOptions, ...editorUserOptions}).expect(200);

        // Assert
        const {updateCounts, success} = response.body;
        expect(success).toBe(true);
        expect(updateCounts.experiments).toBe(1);
        expect(updateCounts.experimentRuns).toBe(1);
        expect(updateCounts.expObject).toBe(2);
        expect(updateCounts.expData).toBe(1);
        expect(updateCounts.movedFiles).toBe(1);
        expect(updateCounts.auditEvents).toBe(3);

        const movedEventInTop = await getAssayRunMovedAuditLogs(server, 'assayWithResultFile', 'top-movetosub1-9', userComment, topFolderOptions);
        expect(movedEventInTop.length).toBe(0);

        const movedEventInSub1 = await getAssayRunMovedAuditLogs(server, 'assayWithResultFile', 'top-movetosub1-9', userComment, subfolder1Options);
        expect(movedEventInSub1.length).toBe(1);

        const runExistsInTop = await runExists(server, runId, topFolderOptions);
        expect(runExistsInTop).toBe(false);
        const runExistsInSub1 = await runExists(server, runId, subfolder1Options);
        expect(runExistsInSub1).toBe(true);

    });


});