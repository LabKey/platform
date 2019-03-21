const MiniCssExtractPlugin = require('mini-css-extract-plugin');
const constants = require('./constants');

module.exports = {
    context: constants.context(__dirname),

    mode: 'development',

    entry: {
        'experiment-styles': [
            './src/client/theme/style.js'
        ]
    },

    output: {
        path: constants.outputPath(__dirname),
        publicPath: '/labkey/experiment/gen/',
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