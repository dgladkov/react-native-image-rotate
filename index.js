/**
 * @providesModule ImageRotate
 * @flow
 */
'use strict';
import { NativeModules } from 'react-native';

const RCTImageRotateModule = NativeModules.ImageRotateModule;

export default class ImageRotate {
  /**
   * Rotate the image specified by the URI param. If URI points to a remote
   * image, it will be downloaded automatically. If the image cannot be
   * loaded/downloaded, the failure callback will be called.
   *
   * If the rotate process is successful, the resultant rotated image
   * will be stored in the ImageStore, and the URI returned in the success
   * callback will point to the image in the store. Remember to delete the
   * rotated image from the ImageStore when you are done with it.
   *
   * Angles divisible by 90 are supported, negative angles can be used for
   * counter-clockwise rotation.
   */
  static rotateImage(
    uri: string,
    angle: number,
    success: (uri: string) => void,
    failure: (error: Object) => void
  ) {
    RCTImageRotateModule.rotateImage(uri, angle, success, failure);
  }
}
