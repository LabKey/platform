/*
 * Copyright (c) 2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
const path = require('path');
const ExtractTextPlugin = require('extract-text-webpack-plugin');

const inProduction = process.env.NODE_ENV === 'production';
const baseJsDir = './resources/styles/js/';
const styleJs = baseJsDir + 'style.js';
const ext4Js = baseJsDir + 'ext4.js';
const ext3Js = baseJsDir + 'ext3.js';
const useSourceMaps = !inProduction;

module.exports = function(env) {
    var entry = {};
    if (env && env.builddependency) {
        entry.core = baseJsDir + 'resources.js';
    }
    else if (env && env.theme) {
        var themeName = env.theme;
        entry[themeName] = styleJs;
        entry['ext4_' + themeName] = ext4Js;
        entry['ext3_' + themeName] = ext3Js;
    }
    else {
        entry.seattle = styleJs;
        entry.ext4 = ext4Js;
        entry.ext3 = ext3Js;
    }

    return {
        context: path.resolve(__dirname, '..'),

        devtool: useSourceMaps ? 'source-map' : false,

        entry: entry,

        output: {
            path: path.resolve(__dirname, '../resources/web/core/css'),
            publicPath: './',
            filename: '[name].js'
        },

        plugins: [
            new ExtractTextPlugin({
                allChunks: true,
                filename: '[name].css'
            })
        ],

        resolve: {
            extensions: [ '.js' ]
        },

        externals: {
            jQuery: 'jQuery'
        },

        module: {
            rules: [
                {
                    // labkey scss
                    test: /\.scss$/,
                    loader: ExtractTextPlugin.extract({
                        use: [{
                            loader: 'css-loader',
                            options: {
                                minimize: inProduction,
                                sourceMap: useSourceMaps,
                                url: false
                            }
                        },{
                            loader: 'postcss-loader',
                            options: {
                                sourceMap: useSourceMaps ? 'inline' : undefined
                            }
                        },{
                            loader: 'sass-loader',
                            options: {
                                sourceMap: useSourceMaps
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
                                sourceMap: useSourceMaps
                            }
                        },{
                            loader: 'postcss-loader'
                        },{
                            loader: 'resolve-url-loader'
                        },{
                            loader: 'sass-loader',
                            options: {
                                sourceMap: useSourceMaps
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
    }
};