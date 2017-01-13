/*
 * Copyright (c) 2016 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
const path = require('path');
const webpack = require('webpack');
var ExtractTextPlugin = require('extract-text-webpack-plugin');

module.exports = {
    context: path.resolve(__dirname, '..'),

    entry: './resources/styles/style.js',

    output: {
        path: path.resolve(__dirname, '../resources/web/core/css'),
        publicPath: '/labkey/core/css/',
        filename: 'out_style.js'
    },

    plugins: [
        new ExtractTextPlugin('main.css', {
            allChunks: true
        })
    ],

    module: {
        loaders: [
            {
                test: /\.css$/,
                loader: ExtractTextPlugin.extract('style-loader', ['css-loader', 'postcss-loader', 'resolve-url-loader']),
                exclude: /node_modules/
            },
            {
                test: /\.scss$/,
                loader: ExtractTextPlugin.extract('style-loader', ['css-loader?sourceMap', 'postcss-loader', 'resolve-url-loader', 'sass-loader?sourceMap']),
                exclude: /node_modules/
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