#import "ImageRotateModule.h"

#import <UIKit/UIKit.h>

#import <React/RCTConvert.h>
#import <React/RCTLog.h>
#import <React/RCTUtils.h>
#import "RCTImageUtils.h"

#import "RCTImageStoreManager.h"
#import "RCTImageLoader.h"

@implementation ImageRotateModule

RCT_EXPORT_MODULE()

@synthesize bridge = _bridge;

static CGFloat DegreesToRadians(CGFloat degrees) {
    return degrees * M_PI / 180.0;
};

/**
 * Rotates an image and adds the result to the image store.
 *
 * @param imageURL A URL, a string identifying an asset etc.
 * @param angle Rotation angle in degrees
 */
RCT_EXPORT_METHOD(rotateImage:(NSURLRequest *)imageURL
                  angle:(nonnull NSNumber *)angle
                  successCallback:(RCTResponseSenderBlock)successCallback
                  errorCallback:(RCTResponseErrorBlock)errorCallback)
{

  [_bridge.imageLoader loadImageWithURLRequest:imageURL callback:^(NSError *error, UIImage *image) {
    if (error) {
      errorCallback(error);
      return;
    }
      
    // calculate the size of the rotated view's containing box for our drawing space
    UIView *rotatedViewBox = [[UIView alloc] initWithFrame:CGRectMake(0, 0, image.size.width, image.size.height)];
    CGAffineTransform t = CGAffineTransformMakeRotation(DegreesToRadians([angle doubleValue]));
    rotatedViewBox.transform = t;
    CGSize rotatedSize = rotatedViewBox.frame.size;
      
    // Create the bitmap context
    UIGraphicsBeginImageContext(rotatedSize);
    CGContextRef bitmap = UIGraphicsGetCurrentContext();
      
    // Move the origin to the middle of the image so we will rotate and scale around the center.
    CGContextTranslateCTM(bitmap, rotatedSize.width / 2, rotatedSize.height / 2);
      
    // Rotate the image context
    CGContextRotateCTM(bitmap, DegreesToRadians([angle doubleValue]));
      
    // Now, draw the rotated/scaled image into the context
    CGContextScaleCTM(bitmap, 1.0, -1.0);
    CGContextDrawImage(bitmap, CGRectMake(-image.size.width / 2, -image.size.height / 2, image.size.width, image.size.height), [image CGImage]);
      
    UIImage *rotatedImage = UIGraphicsGetImageFromCurrentImageContext();
    UIGraphicsEndImageContext();

    // Store image
    [_bridge.imageStoreManager storeImage:rotatedImage withBlock:^(NSString *rotatedImageTag) {
      if (!rotatedImageTag) {
        NSString *errorMessage = @"Error storing rotated image in RCTImageStoreManager";
        RCTLogWarn(@"%@", errorMessage);
        errorCallback(RCTErrorWithMessage(errorMessage));
        return;
      }
      successCallback(@[rotatedImageTag]);
    }];
  }];
}

@end
