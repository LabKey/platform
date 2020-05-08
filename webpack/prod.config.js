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

    // Generate dependencies via lib.xml rather than view.xml
    if (entryPoint.generateLib === true) {
        plugins = plugins.concat([
            new HtmlWebpackPlugin({
                inject: false,
                module: process.env.LK_MODULE,
                name: entryPoint.name,
                title: entryPoint.title,
                permission: entryPoint.permission,
                filename: '../../../web/' + process.env.LK_MODULE + '/gen/' + entryPoint.name + '.lib.xml',
                template: '../webpack/lib.template.xml'
            }),
        ]);
    } else {
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
        filename: '[name].[contenthash].js'
    },

    module: {
        rules: constants.loaders.TYPESCRIPT_LOADERS.concat(constants.loaders.STYLE_LOADERS)
    },

    resolve: {
        extensions: constants.extensions.TYPESCRIPT
    },

    optimization: {
        splitChunks: {
            maxSize: 2 * 1000000, // 2 MB
            cacheGroups: {
                commons: {
                    test: /[\\/]node_modules[\\/]/,
                    name: 'vendors',
                    chunks: 'all'
                }
            }
        }
    },

    plugins: plugins
};

