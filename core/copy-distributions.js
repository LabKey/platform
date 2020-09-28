/*
 * Copyright (c) 2018 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
const fs = require('fs-extra');

/**
 * Copy @labkey/api distribution to module resources.
 */
function copyAPIFiles() {
    log('Copying @labkey/api distribution from npm package ... ');
    const apiDistDir = __dirname + '/node_modules/@labkey/api/dist/';
    const targetDir = __dirname + '/resources/web/clientapi/';

    const files = [
        'labkey-api-js-core.min.js',
        'labkey-api-js-core.min.js.map'
    ];

    files.forEach((file) => {
        fs.copy(apiDistDir + file, targetDir + file);
    });
    log('Done.\n');
}

/**
 * Copy @labkey/themes distribution to module resources.
 */
function copyThemeFiles() {
    log('Copying @labkey/themes distribution from npm package ... ');
    const apiDistDir = __dirname + '/node_modules/@labkey/themes/dist/';
    const targetDir = __dirname + '/resources/web/core/css/';

    fs.copy(apiDistDir, targetDir);

    log('Done.\n');
}

function log(msg) {
    process.stdout.write(msg);
}

copyAPIFiles();
copyThemeFiles();