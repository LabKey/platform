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
    setupFiles: ['@labkey/test/dist/config/integration.setup.js'],
    setupFilesAfterEnv: ['@labkey/test/dist/config/integration.setup.afterenv.js'],
    testEnvironment: 'jsdom',
    testPathIgnorePatterns: ['/node_modules/'],
    transform: {
        '^.+\\.tsx?$': [
            'ts-jest',
            {
                isolatedModules: true,
                tsconfig: 'node_modules/@labkey/build/webpack/tsconfig.json',
            }
        ]
    },
    testRegex: '(\\.ispec)\\.(ts|tsx)$',
    preset: 'ts-jest',
    rootDir: '../../',
    testMatch: null,
    testResultsProcessor: 'jest-teamcity-reporter',
};
