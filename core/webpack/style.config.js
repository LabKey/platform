/*
 * Copyright (c) 2016 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
const path = require('path');
const webpack = require('webpack');
const ExtractTextPlugin = require('extract-text-webpack-plugin');

module.exports = {
    context: path.resolve(__dirname, '..'),

    devtool: 'source-map',

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
        new ExtractTextPlugin({
            allChunks: true,
            filename: '[name].css'
        })
    ],

    module: {
        rules: [
            {
                // labkey scss
                test: /\.scss$/,
                loader: ExtractTextPlugin.extract({
                    use: [{
                        loader: 'css-loader',
                        options: {
                            sourceMap: true,
                            url: false
                        }
                    },{
                        loader: 'postcss-loader',
                        options: {
                            sourceMap: 'inline'
                        }
                    },{
                        loader: 'sass-loader',
                        options: {
                            debug: true,
                            sourceMap: true
                        }
                    }],
                    fallback: 'style-loader'
                }),
                exclude: [/node_modules/]
            },
            {
                // TODO: Need to figure out a way to get ~scss imports to resolve via the webpack loader.
                // That way we can have specific libraries run through the resolve-url-loader.
                // For now, font-awesome is included directly in style.js
                // node modules scss
                test: /\.scss$/,
                loader: ExtractTextPlugin.extract({
                    use: [{
                        loader: 'css-loader',
                        options: {
                            sourceMap: true
                        }
                    },{
                        loader: 'postcss-loader'
                    },{
                        loader: 'resolve-url-loader'
                    },{
                        loader: 'sass-loader',
                        options: {
                            sourceMap: true
                        }
                    }],
                    fallback: 'style-loader'
                }),
                include: [/node_modules/]
            },
            {
                test: /\.(png|jpg|gif)$/,
                loader: 'url-loader?limit=25000'
            },
            { test: /\.woff(\?v=\d+\.\d+\.\d+)?$/, loader: "url-loader?limit=10000&mimetype=application/font-woff" },
            { test: /\.woff2(\?v=\d+\.\d+\.\d+)?$/, loader: "url-loader?limit=10000&mimetype=application/font-woff" },
            { test: /\.ttf(\?v=\d+\.\d+\.\d+)?$/, loader: "url-loader?limit=10000&mimetype=application/octet-stream" },
            { test: /\.eot(\?v=\d+\.\d+\.\d+)?$/, loader: "file-loader" },
            { test: /\.svg(\?v=\d+\.\d+\.\d+)?$/, loader: "url-loader?limit=10000&mimetype=image/svg+xml" }
        ]
    }
};