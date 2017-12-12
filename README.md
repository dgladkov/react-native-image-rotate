# react-native-image-rotate

This project supports react-native >= 0.40.0

This module is meant to be used together with react-native's
[ImageEditor](https://github.com/facebook/react-native/blob/master/Libraries/Image/ImageEditor.js)
to create full-featured crop tool

## Installation

First install the package via npm

`$ npm install react-native-image-rotate`

then use [rnpm](https://github.com/rnpm/rnpm) to link native libraries

`$ react-native link react-native-image-rotate`

## Usage

Package exposes only one class that contains one method with following signature:

```javascript

static rotateImage(
    uri: string,
    angle: number,
    success: (uri: string) => void,
    failure: (error: Object) => void
  ) : void
```

## Example

Check the [Example directory](https://github.com/dgladkov/react-native-image-rotate/tree/master/Example)
for a working iOS/Android example

## License

MIT
