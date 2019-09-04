/*
 * Copyright (c) 2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
const MiniCssExtractPlugin = require('mini-css-extract-plugin');
const constants = require('./constants');

module.exports = {
    context: constants.context(__dirname),

    mode: 'development',

    entry: {
        'demo-styles': [
            './src/client/theme/style.js'
        ]
    },

    output: {
        path: constants.outputPath(__dirname),
        publicPath: '/labkey/demo/gen/',
        filename: '[name].js'
    },

    module: {
        rules: constants.loaders.STYLE_LOADERS
    },

    plugins: [
        new MiniCssExtractPlugin({
            filename: '[name].css'
        })
    ]
};