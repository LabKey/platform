/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import { ActionURL, PermissionRoles } from '@labkey/api';
import { hookServer, RequestOptions } from '@labkey/test';
import mock from 'mock-fs';
import supertest from 'supertest';

// @ts-expect-error process is not available in a browser environment
const env = process.env;
const server = hookServer(env);
const PROJECT_NAME = 'ParseFileActionTest Project';

// Mirror ParseFileAction's limits
const MAX_FILE_BYTES = 10 * 1024 * 1024;
const MAX_ROWS = 10_000;
const MAX_CELLS = 500_000;
const MAX_COLUMNS = 16_384;
const SMALL_TSV = 'small.tsv';
const BIG_TSV = 'big.tsv';
const LONG_TSV = 'long.tsv';
const XLSX = 'sampleType.xlsx';

// Read eagerly so the real file is captured before mock-fs replaces the file system; relative to the module directory
const XLSX_CONTENT = mock.load('test/sampledata/sampleType.xlsx', { lazy: false });

let readerOptions: RequestOptions;

beforeAll(async () => {
    await server.init(PROJECT_NAME, { ensureModules: ['experiment'] });
    const reader = await server.createUser('reader@parsefileactiontest.com');
    await server.addUserToRole(reader.username, PermissionRoles.Reader, PROJECT_NAME);
    readerOptions = { containerPath: PROJECT_NAME, requestContext: await server.createRequestContext(reader) };
});

beforeEach(() => {
    mock({ [SMALL_TSV]: 'Name\tValue\nA\t1\nB\t2\n' });
});

afterEach(() => {
    mock.restore();
});

afterAll(async () => {
    return server.teardown();
});

function parseFile(filePath: string, options: RequestOptions, maxRows?: number) {
    return server.request('experiment', 'parseFile.api', (agent, url) => {
        let request = agent.post(url);
        if (maxRows !== undefined)
            request = request.field('maxRows', String(maxRows));
        return request.attach('file', filePath);
    }, options);
}

describe('ParseFileAction', () => {
    it('rejects guests', async () => {
        // server.request always sends credentials, so use a bare agent; it keeps the guest session cookie
        const guest = supertest.agent(env.INTEGRATION_SERVER);
        const whoAmI = await guest.get(ActionURL.buildURL('login', 'whoAmI.api', PROJECT_NAME)).expect(200);

        await guest
            .post(ActionURL.buildURL('experiment', 'parseFile.api', PROJECT_NAME))
            .set('X-LABKEY-CSRF', whoAmI.body.CSRF)
            .attach('file', SMALL_TSV)
            .expect(401);
    });

    it('parses a small file for a reader', async () => {
        const response = await parseFile(SMALL_TSV, readerOptions).expect(200);
        expect(response.body.sheets[0].data).toEqual([
            ['Name', 'Value'],
            ['A', 1],
            ['B', 2],
        ]);
        expect(response.body.truncated).toBe(false);
    });

    it.each([
        [2, 2, true],
        [3, 3, false],
    ])('returns exactly maxRows=%i rows including the header for TSV', async (maxRows, expectedRows, truncated) => {
        const response = await parseFile(SMALL_TSV, readerOptions, maxRows).expect(200);
        expect(response.body.sheets[0].data.length).toEqual(expectedRows);
        expect(response.body.truncated).toBe(truncated);
    });

    it('rejects files over the size cap', async () => {
        mock({ [BIG_TSV]: 'a'.repeat(MAX_FILE_BYTES + 1) });
        const response = await parseFile(BIG_TSV, readerOptions).expect(400);
        expect(response.body.exception).toEqual('File exceeds the maximum allowed size of 10 MB.');
    });

    it.each([-1, MAX_ROWS * 2])('caps rows at the server maximum when maxRows=%i', async (maxRows) => {
        const rows = Array.from({ length: MAX_ROWS + 5 }, (_, i) => `S${i}\t${i}`);
        mock({ [LONG_TSV]: 'Name\tValue\n' + rows.join('\n') + '\n' });

        const response = await parseFile(LONG_TSV, readerOptions, maxRows).expect(200);
        const data = response.body.sheets[0].data;
        expect(data.length).toEqual(MAX_ROWS);
        expect(data[0]).toEqual(['Name', 'Value']);
        expect(response.body.truncated).toBe(true);
    });

    it('parses an Excel file and honors maxRows', async () => {
        mock({ [XLSX]: XLSX_CONTENT });

        const response = await parseFile(XLSX, readerOptions, 2).expect(200);
        const data = response.body.sheets[0].data;
        expect(response.body.sheets[0].name).toEqual('Sheet1');
        expect(data.length).toEqual(2);
        expect(data[0]).toEqual(['Name', 'IntCol', 'StringCol', 'DateCol', 'BoolCol', 'Parent']);
        expect(response.body.sheets[0].truncated).toBe(true);
        expect(response.body.truncated).toBe(true);
    });

    it('parses a whole Excel file without truncation', async () => {
        mock({ [XLSX]: XLSX_CONTENT });

        const response = await parseFile(XLSX, readerOptions).expect(200);
        expect(response.body.sheets[0].data.length).toEqual(5);
        expect(response.body.truncated).toBe(false);
    });

    it('rejects Excel content without an Excel extension', async () => {
        mock({ sampleType: XLSX_CONTENT });

        const response = await parseFile('sampleType', readerOptions).expect(400);
        expect(response.body.exception).toEqual('Excel files must have a .xls or .xlsx extension.');
    });

    it('rejects lines wider than the column cap', async () => {
        mock({ 'wide.tsv': 'A' + '\t'.repeat(MAX_COLUMNS) + '\nx\n' });

        const response = await parseFile('wide.tsv', readerOptions).expect(400);
        expect(response.body.exception).toEqual(`File has more than ${MAX_COLUMNS} columns.`);
    });

    it('rejects wide records split across quoted newlines', async () => {
        const chunk = '\t'.repeat(MAX_COLUMNS / 2);
        mock({ 'quoted.tsv': '"a\nb"' + chunk + '"c\nd"' + chunk + '"e\nf"' + chunk + '\nx\n' });

        const response = await parseFile('quoted.tsv', readerOptions).expect(400);
        expect(response.body.exception).toEqual(`File has more than ${MAX_COLUMNS} columns.`);
    });

    it('rejects TSV output over the cell cap', async () => {
        // 60 columns x 10,000 rows stays under the row and column caps but over the cell cap
        const width = 60;
        const header = Array.from({ length: width }, (_, i) => `C${i}`).join('\t');
        const row = Array.from({ length: width }, () => '1').join('\t');
        mock({ 'cells.tsv': header + '\n' + Array(MAX_ROWS).fill(row).join('\n') + '\n' });

        const response = await parseFile('cells.tsv', readerOptions).expect(400);
        expect(response.body.exception).toEqual(`File is too large to parse; it exceeds ${MAX_CELLS} cells.`);
    });

    it('keeps values that do not match the inferred type', async () => {
        const rows = Array.from({ length: 6_000 }, (_, i) => (i === 5_500 ? 'abc\t1' : `${i + 10}\t1`));
        mock({ 'late.tsv': 'A\tB\n' + rows.join('\n') + '\n' });

        const response = await parseFile('late.tsv', readerOptions).expect(200);
        const data = response.body.sheets[0].data;
        expect(data[1][0]).toEqual(10);
        expect(data[5_501][0]).toEqual('abc');
    });
});
