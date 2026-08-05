/*
 * Copyright (c) 2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
// jquery-dateFormat.js publishes its API onto a jQuery global, so stub one before loading it
const jq: any = {};
(globalThis as any).jQuery = jq;
require('../../../webapp/internal/jQuery/jquery-dateFormat.js');

// parseDate() only reaches for these five members of a Date, so a stub lets us pin toTimeString()
// output without depending on the machine's timezone.
const dateStub = (timeString: string) => ({
    getFullYear: () => 2026,
    getMonth: () => 6,
    getDate: () => 27,
    getMilliseconds: () => 0,
    toTimeString: () => timeString,
});

describe('DateFormat.format.date', () => {
    test('timezone label containing a colon keeps the time', () => {
        // Firefox renders Pacific/Honolulu as 'GMT-10:00'; that colon used to split the time into four
        // parts, so parseTime() bailed and the time came back as 00:00.
        const honolulu = dateStub('08:00:00 GMT-1000 (GMT-10:00)');
        expect(jq.format.date(honolulu, 'HH:mm')).toBe('08:00');
        expect(jq.format.date(honolulu, 'yyyy-MM-dd HH:mm:ss')).toBe('2026-07-27 08:00:00');
    });

    test('timezone label without a colon still works', () => {
        expect(jq.format.date(dateStub('08:00:00 GMT-0700 (Pacific Daylight Time)'), 'HH:mm')).toBe('08:00');
        expect(jq.format.date(dateStub('13:45:09 GMT+0000 (Coordinated Universal Time)'), 'HH:mm:ss')).toBe('13:45:09');
    });
});
