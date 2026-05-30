/*
 * Copyright (c) 2024-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import { hookServer, RequestOptions, selectRandomN, successfulResponse } from '@labkey/test';

import {
    deleteAssayDesign,
    getAssayDesignPayload,
    getAssayDesignRowIdByName,
    ILLEGAL_DOMAIN_CHARSET,
    importRun,
    initProject
} from './utils';
import { ASSAY_DESIGNER_ROLE } from '@labkey/components';

// @ts-expect-error process is not available in a browser environment
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

const resultPropField = {
    name: "Prop",
    scale: 4000,
    shownInDetailsView: true,
    shownInInsertView: true,
    shownInUpdateView: true,
    isPrimaryKey: false,
};

describe('Assay Designer - Permissions', () => {
    it('Lack designer or Reader permission', async () => {
        await server.post(
            'assay',
            'saveProtocol.api',
            getAssayDesignPayload('Failed'),
            {...topFolderOptions, ...readerUserOptions}
        ).expect(403);

        await server.post(
            'assay',
            'saveProtocol.api',
            getAssayDesignPayload('Failed'),
            {...topFolderOptions, ...editorUserOptions}
        ).expect(403);

        await server.post(
            'assay',
            'saveProtocol.api',
            getAssayDesignPayload('Failed'),
            {...topFolderOptions, ...designerOptions}
        ).expect(403);

    });

    async function verifyAssayDesignCreateFailure(badDomainName: string, error: string) {
        let exception = null;
        const badDomainNameResp = await server.post(
            'assay',
            'saveProtocol.api',
            getAssayDesignPayload(badDomainName),
            {...topFolderOptions, ...designerReaderOptions}
        ).expect((resp) => {
            exception = JSON.parse(resp.text).exception;
        })
        expect(exception).toBe(error.replace('REPLACE', badDomainName));
    }

    async function verifyAssayDesignUpdateFailure(payload: any, badDomainName: string, error: string) {
        let exception = null;
        payload['name'] = badDomainName;
        const badDomainNameResp = await server.post(
            'assay',
            'saveProtocol.api',
            payload,
            {...topFolderOptions, ...designerReaderOptions}
        ).expect((resp) => {
            exception = JSON.parse(resp.text).exception;
        })

        expect(exception).toBe(error.replace('REPLACE', badDomainName));
    }

    it('Assay design name validation', async () => {
        const badNames = {
            '': 'Assay Design name must not be blank.',
            ' ': 'Assay Design name must not be blank.',
            'with\0nullCharacter': `Invalid Assay Design name 'REPLACE'. Assay Design name must contain only valid unicode characters.`,
            'with\tnewLines': `Invalid Assay Design name 'REPLACE'. Assay Design name may not contain 'tab', 'new line', or 'return' characters.`,
            '.startWithDot': `Invalid Assay Design name 'REPLACE'. Assay Design name must start with a letter or a number.`,
            ['c' + selectRandomN(ILLEGAL_DOMAIN_CHARSET.split(''), 2).join('')]: `Invalid Assay Design name 'REPLACE'. Assay Design name may not contain any of these characters: ` + ILLEGAL_DOMAIN_CHARSET,
            'a -b': `Invalid Assay Design name 'REPLACE'. Assay Design name may not contain space followed by dash.`
        };

        let badNameKeys = Object.keys(badNames);
        for (let i = 0; i < badNameKeys.length; i++)
            await verifyAssayDesignCreateFailure(badNameKeys[i], badNames[badNameKeys[i]]);
        
        let assayDesignPayload = getAssayDesignPayload('good');

        await server.post(
            'assay',
            'saveProtocol.api',
            assayDesignPayload,
            {...topFolderOptions, ...designerReaderOptions}
        ).expect((res) => {
            const result = JSON.parse(res.text);
            assayDesignPayload.protocolId = result.data.protocolId;
            const domains = result.data.domains;
            assayDesignPayload.domains[0].domainId = domains[0].domainId;
            assayDesignPayload.domains[0].domainURI = domains[0].domainURI;
            assayDesignPayload.domains[1].domainId = domains[1].domainId;
            assayDesignPayload.domains[1].domainURI = domains[1].domainURI;
            assayDesignPayload.domains[2].domainId = domains[2].domainId;
            assayDesignPayload.domains[2].domainURI = domains[2].domainURI;
            return true;
        });

        for (let i = 0; i < badNameKeys.length; i++)
            await verifyAssayDesignUpdateFailure(assayDesignPayload, badNameKeys[i], badNames[badNameKeys[i]]);

    });

    describe('Create/update/delete designs', () => {
        it('Designer can create, update and delete empty design, reader and editors cannot create/update/delete design.', async () => {
            const dataType = "ToBeDeleted";
            const assayDesignPayload = getAssayDesignPayload(dataType);

            await server.post(
                'assay',
                'saveProtocol.api',
                assayDesignPayload,
                {...topFolderOptions, ...readerUserOptions}
            ).expect(403);

            await server.post(
                'assay',
                'saveProtocol.api',
                assayDesignPayload,
                {...topFolderOptions, ...editorUserOptions}
            ).expect(403);

            await server.post(
                'assay',
                'saveProtocol.api',
                assayDesignPayload,
                {...topFolderOptions, ...designerReaderOptions}
            ).expect((res) => {
                const result = JSON.parse(res.text);
                assayDesignPayload.protocolId = result.data.protocolId;
                const domains = result.data.domains;
                assayDesignPayload.domains[0].domainId = domains[0].domainId;
                assayDesignPayload.domains[0].domainURI = domains[0].domainURI;
                assayDesignPayload.domains[1].domainId = domains[1].domainId;
                assayDesignPayload.domains[1].domainURI = domains[1].domainURI;
                assayDesignPayload.domains[2].domainId = domains[2].domainId;
                assayDesignPayload.domains[2].domainURI = domains[2].domainURI;
                return true;
            });

            assayDesignPayload.domains[2].fields = [resultPropField];

            await server.post(
                'assay',
                'saveProtocol.api',
                assayDesignPayload,
                {...topFolderOptions, ...readerUserOptions}
            ).expect(403);

            await server.post(
                'assay',
                'saveProtocol.api',
                assayDesignPayload,
                {...topFolderOptions, ...editorUserOptions}
            ).expect(403);

            await server.post(
                'assay',
                'saveProtocol.api',
                assayDesignPayload,
                {...topFolderOptions, ...designerReaderOptions}
            ).expect(successfulResponse);

            const dataTypeRowId = await getAssayDesignRowIdByName(server, dataType, topFolderOptions);
            expect(dataTypeRowId).toBe(assayDesignPayload.protocolId);

            let deleteResult = await deleteAssayDesign(server, assayDesignPayload.protocolId, topFolderOptions, readerUserOptions);
            expect(deleteResult.status).toEqual(403);
            deleteResult = await deleteAssayDesign(server, assayDesignPayload.protocolId, topFolderOptions, editorUserOptions);
            expect(deleteResult.status).toEqual(403);

            deleteResult = await deleteAssayDesign(server, assayDesignPayload.protocolId, topFolderOptions, designerReaderOptions);
            expect(deleteResult.status).toEqual(200);

            const removeddataType = await getAssayDesignRowIdByName(server, dataType, topFolderOptions);
            expect(removeddataType).toEqual(0);
        });
        //
        it('Designer can update non-empty design but cannot delete non-empty design, admin can delete non-empty design', async () => {
            const dataType = "FailedDelete";
            const assayDesignPayload = getAssayDesignPayload(dataType);
            await server.post(
                'assay',
                'saveProtocol.api',
                getAssayDesignPayload(dataType),
                {...topFolderOptions, ...designerReaderOptions}
            ).expect((res) => {
                const result = JSON.parse(res.text);
                assayDesignPayload.protocolId = result.data.protocolId;
                const domains = result.data.domains;
                assayDesignPayload.domains[0].domainId = domains[0].domainId;
                assayDesignPayload.domains[0].domainURI = domains[0].domainURI;
                assayDesignPayload.domains[1].domainId = domains[1].domainId;
                assayDesignPayload.domains[1].domainURI = domains[1].domainURI;
                assayDesignPayload.domains[2].domainId = domains[2].domainId;
                assayDesignPayload.domains[2].domainURI = domains[2].domainURI;
                return true;
            });

            assayDesignPayload.domains[2].fields = [resultPropField];

            // create run in child folder
            const { runId } = await importRun(server, assayDesignPayload.protocolId, 'ChildRun', [{"Prop":"ABC"}], subfolder1Options, editorUserOptions);
            expect(runId > 0).toBeTruthy();

            await server.post(
                'assay',
                'saveProtocol.api',
                assayDesignPayload,
                {...topFolderOptions, ...designerReaderOptions}
            ).expect(successfulResponse);

            // verify data exist in child prevent designer from delete design
            let deleteResult = await deleteAssayDesign(server, assayDesignPayload.protocolId, topFolderOptions, designerReaderOptions);
            expect(deleteResult.status).toEqual(403);
            deleteResult = await deleteAssayDesign(server, assayDesignPayload.protocolId, topFolderOptions, readerUserOptions);
            expect(deleteResult.status).toEqual(403);
            deleteResult = await deleteAssayDesign(server, assayDesignPayload.protocolId, topFolderOptions, editorUserOptions);
            expect(deleteResult.status).toEqual(403);

            let failedRemoveddataType = await getAssayDesignRowIdByName(server, dataType, topFolderOptions);
            expect(failedRemoveddataType).toEqual(assayDesignPayload.protocolId);

            // create another run in top folder
            await importRun(server, assayDesignPayload.protocolId, 'TopRun', [{"Prop":"EFG"}], topFolderOptions, editorUserOptions);

            // verify data exist in Top prevent designer from delete design
            deleteResult = await deleteAssayDesign(server, assayDesignPayload.protocolId, topFolderOptions, designerEditorOptions);
            expect(deleteResult.status).toEqual(403);
            deleteResult = await deleteAssayDesign(server, assayDesignPayload.protocolId, topFolderOptions, readerUserOptions);
            expect(deleteResult.status).toEqual(403);
            deleteResult = await deleteAssayDesign(server, assayDesignPayload.protocolId, topFolderOptions, editorUserOptions);
            expect(deleteResult.status).toEqual(403);

            failedRemoveddataType = await getAssayDesignRowIdByName(server, dataType, topFolderOptions);
            expect(failedRemoveddataType).toEqual(assayDesignPayload.protocolId);

            //admin can delete design with data
            deleteResult = await deleteAssayDesign(server, assayDesignPayload.protocolId, topFolderOptions, adminOptions);
            expect(deleteResult.status).toEqual(200);

            const removedDataType = await getAssayDesignRowIdByName(server, dataType, topFolderOptions);
            expect(removedDataType).toEqual(0);
        });
    });
})
