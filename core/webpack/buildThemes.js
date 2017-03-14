var fs = require('fs');

var sourceThemeDir = 'resources/themes/';
var targetThemeDir = 'resources/styles/themeVariables/';
var tempVariableFile = '_variables.scss';
var themeDirs = fs.readdirSync(sourceThemeDir);

if (!fs.existsSync(targetThemeDir)){
    fs.mkdirSync(targetThemeDir);
}

var exec = require('child_process').execSync;
var baseCmd = 'webpack --config webpack/style.config.js --progress';

for (var i=0; i < themeDirs.length; i++)
{
    var themeDir = themeDirs[i];
    var themeVarFullPath = sourceThemeDir + themeDir + '/' + tempVariableFile;
    if (!fs.existsSync(themeVarFullPath)){
        console.log('\x1b[31m', '\n\nError: ' + themeVarFullPath + ' does not exist! Skipping theme building.', '\x1b[0m');
        continue;
    }
    console.log('\n\nBuilding theme: ' + themeDir);
    fs.writeFileSync(targetThemeDir + tempVariableFile, fs.readFileSync(themeVarFullPath));

    var cmd = baseCmd + ' --env.theme=' + themeDir;
    exec(cmd, {stdio:[0,1,2]}); // use option {stdio:[0,1,2]} to print stdout
}
