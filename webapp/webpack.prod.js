const merge = require('webpack-merge');
const common = require('./webpack.config.js');
const CopyPlugin = require('copy-webpack-plugin');
const TerserPlugin = require('terser-webpack-plugin');
const OptimizeCSSAssetsPlugin = require('optimize-css-assets-webpack-plugin');

var opts = common.opts;

var plugins = [
  new OptimizeCSSAssetsPlugin(),
  new CopyPlugin({ patterns: [
    { from: './js/wasm', to: 'js/wasm', info: { minimized: true}, },
  ]}),
];

module.exports = merge(common.config, {
  mode: 'production',
  devtool: 'source-map',
  optimization: {
    minimize: true,
    minimizer: [
      new TerserPlugin({
        cache: false,
        parallel: true,
        sourceMap: !opts.beingPublished,
        terserOptions: {
          mangle: {
            properties: {
              reserved: [
                'initVideo', 'y', 'u', 'v', 'stride', // wasm
                'wsHost', 'p2p', 'token', 'wasm', 'offscreen', 'video', 'av', 'gl', // hash params
                'srcObject', 'captureStream', 'transferControlToOffscreen', // wasm player
                'fps', 'v', 'h', 'requestPictureInPicture', 'onleavepictureinpicture', // native video
                'output', 'error', 'configure', 'decode', 'codec', 'type', 'data', 'timestamp', 'close',
                'optimizeForLatency', 'hardwareAcceleration', 'codedWidth', 'codedHeight',
                'numberOfFrames', 'sampleRate', 'planeIndex', 'allocationSize', 'copyTo', 'copyToChannel',
              ],
            },
          },
          warnings: !opts.beingPublished,
          compress: {
            drop_console: opts.beingPublished,
          },
          ie8: false,
          module: false,
          toplevel: false,
          keep_fnames: false,
          keep_classnames: false,
        },
      }),
    ],
  },
  plugins,
});
