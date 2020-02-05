/*
 * Copyright (c) 2017-2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
const path = require('path');
const MiniCssExtractPlugin = require('mini-css-extract-plugin');
const UglifyJsPlugin = require('uglifyjs-webpack-plugin');
const OptimizeCSSAssetsPlugin = require('optimize-css-assets-webpack-plugin');

const inProduction = process.env.NODE_ENV === 'production';
const baseJsDir = '../resources/styles/js/';
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

        mode: inProduction ? 'production' : 'development',

        devtool: useSourceMaps ? 'source-map' : false,

        performance: {
            hints: false
        },

        entry: entry,

        output: {
            path: path.resolve(__dirname, '../../resources/web/core/css'),
            publicPath: './',
            filename: '[name].js'
        },

        plugins: [
            new MiniCssExtractPlugin({
                filename: '[name].css'
            })
        ],

        resolve: {
            extensions: [ '.js' ]
        },

        externals: {
            jQuery: 'jQuery'
        },

        optimization: inProduction ? {
            minimizer: [
                new UglifyJsPlugin({
                    cache: false,
                    parallel: true,
                    sourceMap: useSourceMaps
                }),
                new OptimizeCSSAssetsPlugin({})
            ]
        } : undefined,

        module: {
            rules: [
                {
                    // labkey scss
                    test: /\.scss$/,
                    use: [
                        MiniCssExtractPlugin.loader,
                        {
                            loader: 'css-loader',
                            options: {
                                sourceMap: useSourceMaps,
                                url: false
                                // TODO: Consider passing a function and only processing font awesome relative URLs
                                // This wasn't working as expected with css-loader
                                // see https://github.com/webpack-contrib/css-loader#url
                            }
                        },
                        {
                            loader: 'postcss-loader'
                        },
                        {
                            loader: 'sass-loader',
                            options: {
                                sourceMap: useSourceMaps
                            }
                        }
                    ],
                    exclude: [/node_modules/]
                },
                {
                    // Duplicate configuration with alternate css-loader "url" property set
                    // See https://github.com/webpack/webpack/issues/5433#issuecomment-357489401
                    // labkey scss
                    test: /\.scss$/,
                    use: [
                        MiniCssExtractPlugin.loader,
                        {
                            loader: 'css-loader',
                            options: {
                                sourceMap: useSourceMaps,
                                url: true
                            }
                        },
                        {
                            loader: 'postcss-loader'
                        },
                        {
                            loader: 'sass-loader',
                            options: {
                                sourceMap: useSourceMaps
                            }
                        }
                    ],
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