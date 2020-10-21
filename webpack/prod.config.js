/*
 * Copyright (c) 2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
const lkModule = process.env.LK_MODULE;
const entryPoints = require('../' + lkModule + '/src/client/entryPoints');
const constants = require('./constants');

// set based on the lk module calling this config
__dirname = lkModule;

module.exports = {
    context: constants.context(__dirname),

    mode: 'production',

    devtool: 'source-map',

    entry: constants.processEntries(entryPoints),

    output: {
        path: constants.outputPath(__dirname),
        publicPath: './', // allows context path to resolve in both js/css
        filename: '[name].[contenthash].cache.js'
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

    plugins: constants.processPlugins(entryPoints),
};

