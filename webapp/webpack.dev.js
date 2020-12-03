const path = require('path');
const merge = require('webpack-merge');
const common = require('./webpack.config.js');

module.exports = merge(common.config, {
  mode: 'development',
  devtool: 'inline-source-map',
  devServer: {
    port: 9000,
    host: '0.0.0.0',
    compress: true,
  },
});
