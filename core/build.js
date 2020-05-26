/*
 * Copyright (c) 2018 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
const fs = require('fs-extra');

/**
 * Experimental build configuration to use @labkey/api distribution in lieu of default clientapi/core files.
 */
function copyAPIFiles() {
    log('Copying files from npm package ... ');
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

function log(msg) {
    process.stdout.write(msg);
}

copyAPIFiles();