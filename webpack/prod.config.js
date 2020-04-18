/*
 * Copyright (c) 2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
const lkModule = process.env.LK_MODULE;
const HtmlWebpackPlugin = require('html-webpack-plugin');
const MiniCssExtractPlugin = require('mini-css-extract-plugin');
const entryPoints = require('../' + lkModule + '/src/client/entryPoints');
const constants = require('./constants');

// set based on the lk module calling this config
__dirname = lkModule;

let entries = {};
let plugins = [];
for (let i = 0; i < entryPoints.apps.length; i++) {
    const entryPoint = entryPoints.apps[i];

    entries[entryPoint.name] = entryPoint.path + '/app.tsx';

    // Skip generation of module views for apps that do not need it
    if (entryPoint.generateViews === false) {
        continue;
    }

    plugins = plugins.concat([
        new HtmlWebpackPlugin({
            inject: false,
            module: process.env.LK_MODULE,
            name: entryPoint.name,
            title: entryPoint.title,
            permission: entryPoint.permission,
            filename: '../../../views/' + entryPoint.name + '.view.xml',
            template: '../webpack/app.view.template.xml'
        }),
        new HtmlWebpackPlugin({
            inject: false,
            filename: '../../../views/' + entryPoint.name + '.html',
            template: '../webpack/app.template.html'
        }),
        new HtmlWebpackPlugin({
            inject: false,
            mode: 'dev',
            module: process.env.LK_MODULE,
            name: entryPoint.name,
            title: entryPoint.title,
            permission: entryPoint.permission,
            filename: '../../../views/' + entryPoint.name + 'Dev.view.xml',
            template: '../webpack/app.view.template.xml'
        }),
        new HtmlWebpackPlugin({
            inject: false,
            mode: 'dev',
            name: entryPoint.name,
            filename: '../../../views/' + entryPoint.name + 'Dev.html',
            template: '../webpack/app.template.html'
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

    // TODO: re-enable this once we understand the interactions of the chunks and splitting better
    //       NOTE: that this will require changes to the app.view.template.xml
    // optimization: {
    //     splitChunks: {
    //         chunks: 'all'
    //     }
    // },

    plugins: plugins
};

