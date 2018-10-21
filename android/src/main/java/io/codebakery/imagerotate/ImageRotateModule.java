package io.codebakery.imagerotate;

import javax.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.text.TextUtils;

import com.facebook.common.logging.FLog;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.GuardedAsyncTask;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.JSApplicationIllegalArgumentException;
import com.facebook.react.common.ReactConstants;

/**
 * Native module that provides image rotation functionality.
 */
public class ImageRotateModule extends ReactContextBaseJavaModule {

    private static final List<String> LOCAL_URI_PREFIXES = Arrays.asList(
            "file://", "content://");

    private static final String TEMP_FILE_PREFIX = "ReactNative_rotated_image_";

    /** Compress quality of the output file. */
    private static final int COMPRESS_QUALITY = 90;

    @SuppressLint("InlinedApi") private static final String[] EXIF_ATTRIBUTES = new String[] {
            ExifInterface.TAG_APERTURE,
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_FLASH,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_PROCESSING_METHOD,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_IMAGE_LENGTH,
            ExifInterface.TAG_IMAGE_WIDTH,
            ExifInterface.TAG_ISO,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.TAG_SUBSEC_TIME,
            ExifInterface.TAG_SUBSEC_TIME_DIG,
            ExifInterface.TAG_SUBSEC_TIME_ORIG,
            ExifInterface.TAG_WHITE_BALANCE
    };

    public ImageRotateModule(ReactApplicationContext reactContext) {
        super(reactContext);
        new CleanTask(getReactApplicationContext()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public String getName() {
        return "ImageRotateModule";
    }

    @Override
    public Map<String, Object> getConstants() {
        return Collections.emptyMap();
    }

    @Override
    public void onCatalystInstanceDestroy() {
        new CleanTask(getReactApplicationContext()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Asynchronous task that cleans up cache dirs (internal and, if available, external) of rotated
     * image files. This is run when the catalyst instance is being destroyed (i.e. app is shutting
     * down) and when the module is instantiated, to handle the case where the app crashed.
     */
    private static class CleanTask extends GuardedAsyncTask<Void, Void> {
        private final Context mContext;

        private CleanTask(ReactContext context) {
            super(context);
            mContext = context;
        }

        @Override
        protected void doInBackgroundGuarded(Void... params) {
            cleanDirectory(mContext.getCacheDir());
            File externalCacheDir = mContext.getExternalCacheDir();
            if (externalCacheDir != null) {
                cleanDirectory(externalCacheDir);
            }
        }

        private void cleanDirectory(File directory) {
            File[] toDelete = directory.listFiles(
                    new FilenameFilter() {
                        @Override
                        public boolean accept(File dir, String filename) {
                            return filename.startsWith(TEMP_FILE_PREFIX);
                        }
                    });
            if (toDelete != null) {
                for (File file: toDelete) {
                    file.delete();
                }
            }
        }
    }

    /**
     * Rotate an image. If all goes well, the success callback will be called with the file:// URI of
     * the new image as the only argument. This is a temporary file - consider using
     * CameraRollManager.saveImageWithTag to save it in the gallery.
     *
     * @param uri the MediaStore URI of the image to rotate
     * @param angle rotation angle
     * @param success callback to be invoked when the image has been rotated; the only argument that
     *        is passed to this callback is the file:// URI of the new image
     * @param error callback to be invoked when an error occurs (e.g. can't create file etc.)
     */
    @ReactMethod
    public void rotateImage(
            String uri,
            final float angle,
            final Callback success,
            final Callback error) {

        if (uri == null || uri.isEmpty()) {
            throw new JSApplicationIllegalArgumentException("Please specify a URI");
        }

        RotateTask rotateTask = new RotateTask(
                getReactApplicationContext(),
                uri,
                angle,
                success,
                error);

        rotateTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private static class RotateTask extends GuardedAsyncTask<Void, Void> {
        final Context mContext;
        final String mUri;
        final float mAngle;
        final Callback mSuccess;
        final Callback mError;

        private RotateTask(
                ReactContext context,
                String uri,
                float angle,
                Callback success,
                Callback error) {
            super(context);

            mContext = context;
            mUri = uri;
            mAngle = angle;
            mSuccess = success;
            mError = error;
        }

        private InputStream openBitmapInputStream() throws IOException {
            InputStream stream;
            if (isLocalUri(mUri)) {
                stream = mContext.getContentResolver().openInputStream(Uri.parse(mUri));
            } else {
                URLConnection connection = new URL(mUri).openConnection();
                stream = connection.getInputStream();
            }
            if (stream == null) {
                throw new IOException("Cannot open bitmap: " + mUri);
            }
            return stream;
        }

        @Override
        protected void doInBackgroundGuarded(Void... params) {
            try {
                BitmapFactory.Options outOptions = new BitmapFactory.Options();

                Bitmap rotated = rotate(outOptions);

                String mimeType = outOptions.outMimeType;
                if (mimeType == null || mimeType.isEmpty()) {
                    throw new IOException("Could not determine MIME type");
                }

                File tempFile = createTempFile(mContext, mimeType);
                writeCompressedBitmapToFile(rotated, mimeType, tempFile);

                if (mimeType.equals("image/jpeg")) {
                    copyExif(mContext, Uri.parse(mUri), tempFile);
                }

                mSuccess.invoke(Uri.fromFile(tempFile).toString());

            } catch (Exception e) {
                mError.invoke(e.getMessage());
            }
        }

        /**
         * Reads and rotates the bitmap.
         * @param outOptions Bitmap options, useful to determine {@code outMimeType}.
         */
        private Bitmap rotate(BitmapFactory.Options outOptions) throws IOException {
            InputStream inputStream = openBitmapInputStream();
            try {
                // This can use a lot of memory
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, outOptions);
                if (bitmap == null) {
                    throw new IOException("Cannot decode bitmap: " + mUri);
                }
                Matrix rotateMatrix = new Matrix();
                rotateMatrix.postRotate(mAngle);

                return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), rotateMatrix, true);
            } catch (OutOfMemoryError outOfMemoryError) {
                // we are out of memory
                throw new RuntimeException("We are out of memory");
            } finally {
                if (inputStream != null) {
                    inputStream.close();
                }
            }
        }

    }

    // Utils

    private static void copyExif(Context context, Uri oldImage, File newFile) throws IOException {
        File oldFile = getFileFromUri(context, oldImage);
        if (oldFile == null) {
            FLog.w(ReactConstants.TAG, "Couldn't get real path for uri: " + oldImage);
            return;
        }

        ExifInterface oldExif = new ExifInterface(oldFile.getAbsolutePath());
        ExifInterface newExif = new ExifInterface(newFile.getAbsolutePath());
        for (String attribute : EXIF_ATTRIBUTES) {
            String value = oldExif.getAttribute(attribute);
            if (value != null) {
                newExif.setAttribute(attribute, value);
            }
        }
        newExif.saveAttributes();
    }

    private static @Nullable File getFileFromUri(Context context, Uri uri) {
        if (uri.getScheme().equals("file")) {
            return new File(uri.getPath());
        } else if (uri.getScheme().equals("content")) {
            Cursor cursor = context.getContentResolver()
                    .query(uri, new String[] { MediaStore.MediaColumns.DATA }, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        String path = cursor.getString(0);
                        if (!TextUtils.isEmpty(path)) {
                            return new File(path);
                        }
                    }
                } finally {
                    cursor.close();
                }
            }
        }

        return null;
    }

    private static boolean isLocalUri(String uri) {
        for (String localPrefix : LOCAL_URI_PREFIXES) {
            if (uri.startsWith(localPrefix)) {
                return true;
            }
        }
        return false;
    }

    private static String getFileExtensionForType(@Nullable String mimeType) {
        if ("image/png".equals(mimeType)) {
            return ".png";
        }
        if ("image/webp".equals(mimeType)) {
            return ".webp";
        }
        return ".jpg";
    }

    private static Bitmap.CompressFormat getCompressFormatForType(String type) {
        if ("image/png".equals(type)) {
            return Bitmap.CompressFormat.PNG;
        }
        if ("image/webp".equals(type)) {
            return Bitmap.CompressFormat.WEBP;
        }
        return Bitmap.CompressFormat.JPEG;
    }

    private static void writeCompressedBitmapToFile(Bitmap rotated, String mimeType, File tempFile)
            throws IOException {
        OutputStream out = new FileOutputStream(tempFile);
        try {
            rotated.compress(getCompressFormatForType(mimeType), COMPRESS_QUALITY, out);
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }

    /**
     * Create a temporary file in the cache directory on either internal or external storage,
     * whichever is available and has more free space.
     *
     * @param mimeType the MIME type of the file to create (image/*)
     */
    private static File createTempFile(Context context, @Nullable String mimeType)
            throws IOException {
        File externalCacheDir = context.getExternalCacheDir();
        File internalCacheDir = context.getCacheDir();
        File cacheDir;
        if (externalCacheDir == null && internalCacheDir == null) {
            throw new IOException("No cache directory available");
        }
        if (externalCacheDir == null) {
            cacheDir = internalCacheDir;
        }
        else if (internalCacheDir == null) {
            cacheDir = externalCacheDir;
        } else {
            cacheDir = externalCacheDir.getFreeSpace() > internalCacheDir.getFreeSpace() ?
                    externalCacheDir : internalCacheDir;
        }
        return File.createTempFile(TEMP_FILE_PREFIX, getFileExtensionForType(mimeType), cacheDir);
    }

}
