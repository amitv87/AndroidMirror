const webpack = require('webpack');
const merge = require('webpack-merge');
const HtmlWebpackPlugin = require('html-webpack-plugin');
const MiniCssExtractPlugin = require('mini-css-extract-plugin');
const SVGSpritemapPlugin = require('svg-spritemap-webpack-plugin');

var isProd = process.argv[3] == 'webpack.prod.js';
var useWasm = process.env.USE_WASM == '1';
var beingPublished = process.env.PUBLISH == '1';

module.exports = {
  opts:{
    isProd,
    useWasm,
    beingPublished,
  },
  config:{
    entry: './app.js',
    output: {
      path: __dirname + '/dist',
      filename: 'bundle.js'
    },
    node: {
      fs: 'empty',
    },
    module: {
      rules: [
        // { test: /\.css$/i, use: ['style-loader', 'css-loader'] },
        {test: /\.css$/i, use: [MiniCssExtractPlugin.loader, 'css-loader']},
      ],
    },
    plugins: [
      new webpack.DefinePlugin({useWasm}),
      new MiniCssExtractPlugin({filename: 'css/main.css'}),
      new SVGSpritemapPlugin('res/*.svg', {sprite: {prefix: false}}),
      new HtmlWebpackPlugin({inject: !isProd, filename: 'index.html', template: 'template.html', useWasm,}),
    ],
  }
}
