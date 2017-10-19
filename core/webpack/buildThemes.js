/*
 * Copyright (c) 2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
var SOURCE_THEME_DIR = 'resources/themes/';
var TARGET_THEME_DIR = 'resources/styles/themeVariables/';
var TEMP_VARIABLE_FILE = '_variables.scss';
var OUTPUT_DIR = 'resources/web/core/css/';

var BASE_BUILD_CMD = 'webpack --config webpack/style.config.js --progress';

// white-list javascript file output for cleanup
var VALID_JS_OUTPUTS = ['core.js'];

var fs = require('fs');
var themeDirs = fs.readdirSync(SOURCE_THEME_DIR);
if (!fs.existsSync(TARGET_THEME_DIR)){
    fs.mkdirSync(TARGET_THEME_DIR);
}

var exec = require('child_process').execSync;

var cmd = BASE_BUILD_CMD + ' --env.builddependency=true';
console.log('\n\nBuilding core resources');
exec(cmd, {stdio:[0,1,2]}); // use option {stdio:[0,1,2]} to print stdout

for (var i=0; i < themeDirs.length; i++)
{
    var themeDir = themeDirs[i];
    var themeVarFullPath = SOURCE_THEME_DIR + themeDir + '/' + TEMP_VARIABLE_FILE;
    if (!fs.existsSync(themeVarFullPath)){
        console.log('\x1b[31m', '\n\nError: ' + themeVarFullPath + ' does not exist! Skipping theme building.', '\x1b[0m');
        continue;
    }
    console.log('\n\nBuilding theme: ' + themeDir);
    fs.writeFileSync(TARGET_THEME_DIR + TEMP_VARIABLE_FILE, fs.readFileSync(themeVarFullPath));

    cmd = BASE_BUILD_CMD + ' --env.theme=' + themeDir;
    exec(cmd, {stdio:[0,1,2]}); // use option {stdio:[0,1,2]} to print stdout
}

// clean up js files
var outputFiles = fs.readdirSync(OUTPUT_DIR);
for (var i=0; i < outputFiles.length; i++)
{
    var file = outputFiles[i];
    if (file.indexOf('.js', file.length - '.js'.length) !== -1)
    {
        if (VALID_JS_OUTPUTS.indexOf(file) === -1)
        {
            fs.unlinkSync(OUTPUT_DIR + file);
        }
    }
    else if (file.indexOf('.js.map', file.length - '.js.map'.length) !== -1)
    {
        fs.unlinkSync(OUTPUT_DIR + file);
    }
}
