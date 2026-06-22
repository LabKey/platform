/*
 * Copyright (c) 2023-2026 LabKey Corporation. All rights reserved. No portion of this work may be reproduced
 * in any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
module.exports = {
    globals: {
        LABKEY: {
            contextPath: '/labkey',
            container: {
                path: '/DefaultTestContainer',
                formats: {
                    dateFormat: 'yyyy-MM-dd',
                    dateTimeFormat: 'yyyy-MM-dd HH:mm',
                    timeFormat: 'HH:mm',
                },
            },
            user: {
                displayName: 'Bill',
                id: 1004,
            },
        },
    },
    moduleFileExtensions: ['ts', 'tsx', 'js'],
    moduleNameMapper: {
        '\\.(scss|css)$': '<rootDir>/test/js/styleMock.js',
    },
    // This is actually the default configuration for reporters, but for some reason with our modules it gets wiped out
    // and then there is no test summary when failures happen, which means that you'll almost never see what tests
    // failed unless they were the last tests to run.
    reporters: [['default', { summaryThreshold: 10 }]],
    setupFilesAfterEnv: ['./test/js/setup.ts'],
    testEnvironment: 'jsdom',
    testRegex: '(\\.(test))\\.(ts|tsx)$',
    testResultsProcessor: 'jest-teamcity-reporter',
    transform: {
        '^.+\\.tsx?$': [
            'ts-jest',
            {
                tsconfig: 'node_modules/@labkey/build/webpack/tsconfig.test.json',
            },
        ],
    },
};
