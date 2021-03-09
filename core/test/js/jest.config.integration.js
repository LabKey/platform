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
    "setupFilesAfterEnv": [
        "./test/js/setup.ts"
    ],
    "testPathIgnorePatterns": [
        "/node_modules/"
    ],
    "testRegex": "(\\.ispec)\\.(ts|tsx)$",
    "preset": "ts-jest",
    "rootDir": "../../",
    "testMatch": null,
    "testResultsProcessor": "jest-teamcity-reporter"
};
