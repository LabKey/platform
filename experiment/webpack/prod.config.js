/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
const webpack = require('webpack');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const constants = require('./constants');

// TODO figure out how to get app.view.template.xml to load all chunks with prefix "vendors" so we don't have to list them here
// TODO can we pull these out into a separate config file (what are the least number of files to touch to add a new entry point)
const entryPoints = [{
    name: 'assayDesigner',
    title: 'Assay Designer',
    permission: 'admin',
    path: './src/client/AssayDesigner',
    chunks: ['vendors~assayDesigner~domainDesigner', 'assayDesigner']
},{
    name: 'domainDesigner',
    title: 'Domain Designer',
    permission: 'admin',
    path: './src/client/DomainDesigner',
    chunks: ['vendors~assayDesigner~domainDesigner', 'vendors~domainDesigner', 'domainDesigner']
}];

let entries = {};
let plugins = [];
for (let i = 0; i < entryPoints.length; i++) {
    const entryPoint = entryPoints[i];

    entries[entryPoint.name] = entryPoint.path + '/app.tsx';

    plugins = plugins.concat([
        new HtmlWebpackPlugin({
            inject: false,
            chunks: entryPoint.chunks || [],
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
        rules: constants.loaders.TYPESCRIPT_LOADERS
    },

    resolve: {
        extensions: constants.extensions.TYPESCRIPT
    },

    optimization: {
        splitChunks: {
            chunks: 'all'
        }
    },

    plugins: plugins
};

