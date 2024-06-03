import { hookServer, RequestOptions, successfulResponse } from '@labkey/test';
import mock from 'mock-fs';

import {
    deleteAssayDesign,
    getAssayDesignPayload,
    getAssayDesignRowIdByName, importRun,
    initProject
} from './utils';
import { ASSAY_DESIGNER_ROLE } from '@labkey/components';

const server = hookServer(process.env);
const PROJECT_NAME = 'DataClassCrudJestProject';

let readerUser, readerUserOptions;
let editorUser, editorUserOptions;
let designer, designerOptions;
let designerReader, designerReaderOptions;
let designerEditor, designerEditorOptions;
let admin, adminOptions;

let topFolderOptions: RequestOptions;
let subfolder1Options;
let subfolder2Options;

beforeAll(async () => {
    const options = await initProject(server, PROJECT_NAME, ASSAY_DESIGNER_ROLE);

    topFolderOptions = options.topFolderOptions;
    subfolder1Options = options.subfolder1Options;
    subfolder2Options = options.subfolder2Options;
    readerUser = options.readerUser;
    readerUserOptions = options.readerUserOptions;

    editorUser = options.editorUser;
    editorUserOptions = options.editorUserOptions;

    designer = options.designer;
    designerOptions = options.designerOptions;

    designerReader = options.designerReader;
    designerReaderOptions = options.designerReaderOptions;

    designerEditor = options.designerEditor;
    designerEditorOptions = options.designerReaderOptions;

    admin = options.admin;
    adminOptions = options.adminOptions;
});

afterAll(async () => {
    return server.teardown();
});

afterEach(() => {
    mock.restore();
});

const resultPropField = {
    conditionalFormats: [],
    name: "Prop",
    scale: 4000,
    shownInDetailsView: true,
    shownInInsertView: true,
    shownInUpdateView: true,
    isPrimaryKey: false,
}

describe('Assay Designer - Permissions', () => {
    it('Lack designer or Reader permission', async () => {
        await server.post(
            'assay',
            'saveProtocol.api',
            getAssayDesignPayload('Failed', [], []),
            {...topFolderOptions, ...readerUserOptions}
        ).expect(403);

        await server.post(
            'assay',
            'saveProtocol.api',
            getAssayDesignPayload('Failed', [], []),
            {...topFolderOptions, ...editorUserOptions}
        ).expect(403);

        await server.post(
            'assay',
            'saveProtocol.api',
            getAssayDesignPayload('Failed', [], []),
            {...topFolderOptions, ...designerOptions}
        ).expect(403);

    });

    describe('Create/update/delete designs', () => {
        it('Designer can create, update and delete empty design', async () => {
            const dataType = "ToDelete";
            const assayDesignPaylod = getAssayDesignPayload(dataType, [], []);
            await server.post(
                'assay',
                'saveProtocol.api',
                getAssayDesignPayload(dataType, [], []),
                {...topFolderOptions, ...designerReaderOptions}
            ).expect((res) => {
                const result = JSON.parse(res.text);
                assayDesignPaylod.protocolId = result.data.protocolId;
                const domains = result.data.domains;
                assayDesignPaylod.domains[0].domainId = domains[0].domainId;
                assayDesignPaylod.domains[0].domainURI = domains[0].domainURI;
                assayDesignPaylod.domains[1].domainId = domains[1].domainId;
                assayDesignPaylod.domains[1].domainURI = domains[1].domainURI;
                assayDesignPaylod.domains[2].domainId = domains[2].domainId;
                assayDesignPaylod.domains[2].domainURI = domains[2].domainURI;
                return true;
            });

            assayDesignPaylod.domains[2].fields = [resultPropField];

            await server.post(
                'assay',
                'saveProtocol.api',
                assayDesignPaylod,
                {...topFolderOptions, ...designerReaderOptions}
            ).expect(successfulResponse);

            const dataTypeRowId = await getAssayDesignRowIdByName(server, dataType, topFolderOptions);
            expect(dataTypeRowId).toBe(assayDesignPaylod.protocolId);

            const deleteResult = await deleteAssayDesign(server, assayDesignPaylod.protocolId, topFolderOptions, designerReaderOptions);
            expect(deleteResult.status).toEqual(200);

            const removeddataType = await getAssayDesignRowIdByName(server, dataType, topFolderOptions);
            expect(removeddataType).toEqual(0);
        });
        //
        it('Designer can update non-empty design but cannot delete non-empty design, admin can delete non-empty design', async () => {
            const dataType = "FailedDelete";
            const assayDesignPaylod = getAssayDesignPayload(dataType, [], []);
            await server.post(
                'assay',
                'saveProtocol.api',
                getAssayDesignPayload(dataType, [], []),
                {...topFolderOptions, ...designerReaderOptions}
            ).expect((res) => {
                const result = JSON.parse(res.text);
                assayDesignPaylod.protocolId = result.data.protocolId;
                const domains = result.data.domains;
                assayDesignPaylod.domains[0].domainId = domains[0].domainId;
                assayDesignPaylod.domains[0].domainURI = domains[0].domainURI;
                assayDesignPaylod.domains[1].domainId = domains[1].domainId;
                assayDesignPaylod.domains[1].domainURI = domains[1].domainURI;
                assayDesignPaylod.domains[2].domainId = domains[2].domainId;
                assayDesignPaylod.domains[2].domainURI = domains[2].domainURI;
                return true;
            });

            assayDesignPaylod.domains[2].fields = [resultPropField];

            // create run in child folder
            const { runId } = await importRun(server, assayDesignPaylod.protocolId, 'ChildRun', [{"Prop":"ABC"}], subfolder1Options, editorUserOptions);
            expect(runId > 0).toBeTruthy();

            await server.post(
                'assay',
                'saveProtocol.api',
                assayDesignPaylod,
                {...topFolderOptions, ...designerReaderOptions}
            ).expect(successfulResponse);

            // verify data exist in child prevent designer from delete design
            let deleteResult = await deleteAssayDesign(server, assayDesignPaylod.protocolId, topFolderOptions, designerReaderOptions);
            expect(deleteResult.status).toEqual(500);

            let failedRemoveddataType = await getAssayDesignRowIdByName(server, dataType, topFolderOptions);
            expect(failedRemoveddataType).toEqual(assayDesignPaylod.protocolId);

            // create another run in top folder
            await importRun(server, assayDesignPaylod.protocolId, 'TopRun', [{"Prop":"EFG"}], topFolderOptions, editorUserOptions);

            // verify data exist in Top prevent designer from delete design
            deleteResult = await deleteAssayDesign(server, assayDesignPaylod.protocolId, topFolderOptions, designerEditorOptions);
            expect(deleteResult.status).toEqual(500);

            failedRemoveddataType = await getAssayDesignRowIdByName(server, dataType, topFolderOptions);
            expect(failedRemoveddataType).toEqual(assayDesignPaylod.protocolId);

            //admin can delete design with data
            deleteResult = await deleteAssayDesign(server, assayDesignPaylod.protocolId, topFolderOptions, adminOptions);
            expect(deleteResult.status).toEqual(200);

            const removedDataType = await getAssayDesignRowIdByName(server, dataType, topFolderOptions);
            expect(removedDataType).toEqual(0);
        });
    });
})