/*
 * Copyright (c) 2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
const HtmlWebpackPlugin = require('html-webpack-plugin');
const MiniCssExtractPlugin = require('mini-css-extract-plugin');
const constants = require('./constants');
const entryPoints = require('./entryPoints');

let entries = {};
let plugins = [];
for (let i = 0; i < entryPoints.apps.length; i++) {
    const entryPoint = entryPoints.apps[i];

    entries[entryPoint.name] = entryPoint.path + '/app.tsx';

    plugins = plugins.concat([
        new HtmlWebpackPlugin({
            inject: false,
            name: entryPoint.name,
            title: entryPoint.title,
            permission: entryPoint.permission,
            filename: '../../../views/' + entryPoint.name + '.view.xml',
            template: 'webpack/app.view.template.xml'
        }),
        new HtmlWebpackPlugin({
            inject: false,
            filename: '../../../views/' + entryPoint.name + '.html',
            template: 'webpack/app.template.html'
        }),
        new HtmlWebpackPlugin({
            inject: false,
            mode: 'dev',
            name: entryPoint.name,
            title: entryPoint.title,
            permission: entryPoint.permission,
            filename: '../../../views/' + entryPoint.name + 'Dev.view.xml',
            template: 'webpack/app.view.template.xml'
        }),
        new HtmlWebpackPlugin({
            inject: false,
            mode: 'dev',
            name: entryPoint.name,
            filename: '../../../views/' + entryPoint.name + 'Dev.html',
            template: 'webpack/app.template.html'
        })
    ]);
}

plugins.push(new MiniCssExtractPlugin());

module.exports = {
    context: constants.context(__dirname),

    mode: 'production',

    devtool: 'source-map',

    entry: entries,

    output: {
        path: constants.outputPath(__dirname),
        publicPath: './', // allows context path to resolve in both js/css
        filename: '[name].js'
    },

    module: {
        rules: constants.loaders.TYPESCRIPT_LOADERS.concat(constants.loaders.STYLE_LOADERS)
    },

    resolve: {
        extensions: constants.extensions.TYPESCRIPT
    },

    plugins: plugins
};

