/*
 * Copyright (c) 2023-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
module.exports = {
    globals: {
        LABKEY: {
            moduleContext: {},
            user: {
                id: 1004,
            },
        },
    },
    moduleFileExtensions: ['js', 'ts', 'tsx'],
    moduleDirectories: ['node_modules'],
    setupFiles: ['./node_modules/@labkey/test/dist/config/integration.setup.js'],
    setupFilesAfterEnv: ['./node_modules/@labkey/test/dist/config/integration.setup.afterenv.js'],
    testEnvironment: 'jsdom',
    testPathIgnorePatterns: ['/node_modules/'],
    transform: {
        '^.+\\.tsx?$': [
            'ts-jest',
            {
                tsconfig: 'node_modules/@labkey/build/configs/tsconfig.test.json',
            }
        ]
    },
    testRegex: '(\\.ispec)\\.(ts|tsx)$',
    preset: 'ts-jest',
    rootDir: '../../',
    testMatch: null,
    testResultsProcessor: 'jest-teamcity-reporter',
};
