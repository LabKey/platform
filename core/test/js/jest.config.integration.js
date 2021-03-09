module.exports = {
    "globals": {
        "ts-jest": {
            "tsconfig": "node_modules/@labkey/build/webpack/tsconfig.json"
        },
        "LABKEY": {
            "moduleContext": {},
            "user": {
                "id": 1004
            }
        }
    },
    "moduleFileExtensions": [
        "js",
        "ts",
        "tsx"
    ],
    "moduleDirectories": [
        "node_modules"
    ],
    "setupFiles": [
        "@labkey/test/dist/config/integration.setup.js"
    ],
    "setupFilesAfterEnv": [
        "./test/js/setup.ts",
        "@labkey/test/dist/config/integration.setup.afterenv.js"
    ],
    "testPathIgnorePatterns": [
        "/node_modules/",
        "packages/freezermanager"
    ],
    "testRegex": "(\\.ispec)\\.(ts|tsx)$",
    "preset": "ts-jest",
    "rootDir": "../../",
    "testMatch": null,
    "testResultsProcessor": "jest-teamcity-reporter"
};
