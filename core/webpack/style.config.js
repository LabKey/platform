/*
 * Copyright (c) 2016 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
const path = require('path');
const webpack = require('webpack');
var ExtractTextPlugin = require('extract-text-webpack-plugin');

module.exports = {
    context: path.resolve(__dirname, '..'),

    entry: {
        core: './resources/styles/js/style.js',
        ext3: './resources/styles/js/ext3.js',
        guide: './resources/styles/js/guide.js'
    },

    output: {
        path: path.resolve(__dirname, '../resources/web/core/css'),
        publicPath: '/labkey/core/css/',
        filename: '[name].js'
    },

    plugins: [
        new ExtractTextPlugin('[name].css', {
            allChunks: true
        })
    ],

    module: {
        loaders: [
            {
                // labkey scss
                test: /\.scss$/,
                loader: ExtractTextPlugin.extract('style-loader', ['css-loader?sourceMap&url=false', 'postcss-loader', 'sass-loader?sourceMap']),
                include: [
                    path.resolve(__dirname, '../resources/styles/scss')
                ]
            },
            {
                // bootstrap / font-awesome scss
                test: /\.scss$/,
                loader: ExtractTextPlugin.extract('style-loader', ['css-loader?sourceMap', 'postcss-loader', 'resolve-url-loader', 'sass-loader?sourceMap']),
                include: [
                    path.resolve(__dirname, '../node_modules/bootstrap-sass'),
                    path.resolve(__dirname, '../node_modules/font-awesome')
                ]
            },
            {
                test: /\.(png|jpg|gif)$/,
                loader: 'url?limit=25000'
            },
            { test: /\.woff(\?v=\d+\.\d+\.\d+)?$/, loader: "url?limit=10000&mimetype=application/font-woff" },
            { test: /\.woff2(\?v=\d+\.\d+\.\d+)?$/, loader: "url?limit=10000&mimetype=application/font-woff" },
            { test: /\.ttf(\?v=\d+\.\d+\.\d+)?$/, loader: "url?limit=10000&mimetype=application/octet-stream" },
            { test: /\.eot(\?v=\d+\.\d+\.\d+)?$/, loader: "file" },
            { test: /\.svg(\?v=\d+\.\d+\.\d+)?$/, loader: "url?limit=10000&mimetype=image/svg+xml" }
        ]
    },
    postcss: () => {
        return [
            require('precss'),
            require('autoprefixer'),
            // require('cssnano') // comment in for minification
        ];
    }
};