package com.github.dhaval2404.imagepicker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.github.dhaval2404.imagepicker.constant.ImageProvider
import com.github.dhaval2404.imagepicker.provider.CameraProvider
import com.github.dhaval2404.imagepicker.provider.CompressionProvider
import com.github.dhaval2404.imagepicker.provider.CropProvider
import com.github.dhaval2404.imagepicker.provider.GalleryProvider
import java.io.File

/**
 * Pick Image
 *
 * @author Dhaval Patel
 * @version 1.0
 * @since 04 January 2019
 */
class ImagePickerActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "image_picker"

        /**
         * Key to Save/Retrieve Image File state
         */
        private const val STATE_IMAGE_FILE = "state.image_file"

        internal fun getCancelledIntent(context: Context): Intent {
            val intent = Intent()
            val message = context.getString(R.string.error_task_cancelled)
            intent.putExtra(ImagePicker.EXTRA_ERROR, message)
            return intent
        }
    }

    private var mGalleryProvider: GalleryProvider? = null
    private var mCameraProvider: CameraProvider? = null
    private lateinit var mCropProvider: CropProvider
    private lateinit var mCompressionProvider: CompressionProvider

    /** File provided by GalleryProvider or CameraProvider */
    private var mImageFile: File? = null

    /** File provided by CropProvider */
    private var mCropFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoreInstanceState(savedInstanceState)
        loadBundle(savedInstanceState)
    }

    /**
     * Restore saved state
     */
    private fun restoreInstanceState(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            mImageFile = savedInstanceState.getSerializable(STATE_IMAGE_FILE) as File?
        }
    }

    /**
     * Save all appropriate activity state.
     */
    public override fun onSaveInstanceState(outState: Bundle) {
        outState.putSerializable(STATE_IMAGE_FILE, mImageFile)
        mCameraProvider?.onSaveInstanceState(outState)
        mCropProvider.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    /**
     * Parse Intent Bundle and initialize variables
     */
    private fun loadBundle(savedInstanceState: Bundle?) {
        // Create Crop Provider
        mCropProvider = CropProvider(this)
        mCropProvider.onRestoreInstanceState(savedInstanceState)

        // Create Compression Provider
        mCompressionProvider = CompressionProvider(this)

        // Retrieve Image Provider
        val provider: ImageProvider? =
            intent?.getSerializableExtra(ImagePicker.EXTRA_IMAGE_PROVIDER) as ImageProvider?

        // Create Gallery/Camera Provider
        when (provider) {
            ImageProvider.GALLERY -> {
                mGalleryProvider = GalleryProvider(this)
                // Pick Gallery Image
                savedInstanceState ?: mGalleryProvider?.startIntent()
            }
            ImageProvider.CAMERA -> {
                mCameraProvider = CameraProvider(this)
                mCameraProvider?.onRestoreInstanceState(savedInstanceState)
                // Pick Camera Image
                savedInstanceState ?: mCameraProvider?.startIntent()
            }
            else -> {
                // Something went Wrong! This case should never happen
                Log.e(TAG, "Image provider can not be null")
                setError(getString(R.string.error_task_cancelled))
            }
        }
    }

    /**
     * Dispatch incoming result to the correct provider.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        mCameraProvider?.onRequestPermissionsResult(requestCode)
        mGalleryProvider?.onRequestPermissionsResult(requestCode)
    }

    /**
     * Dispatch incoming result to the correct provider.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        mCameraProvider?.onActivityResult(requestCode, resultCode, data)
        mGalleryProvider?.onActivityResult(requestCode, resultCode, data)
        mCropProvider.onActivityResult(requestCode, resultCode, data)
    }

    /**
     * Handle Activity Back Press
     */
    override fun onBackPressed() {
        setResultCancel()
    }

    /**
     * {@link CameraProvider} and {@link GalleryProvider} Result will be available here.
     *
     * @param file Capture/Gallery image file
     */
    fun setImage(file: File) {
        mImageFile = file
        when {
            mCropProvider.isCropEnabled() -> mCropProvider.startIntent(file)
            mCompressionProvider.isCompressionRequired(file) -> mCompressionProvider.compress(file)
            else -> setResult(file)
        }
    }

    /**
     * {@link CameraProvider} and {@link GalleryProvider} with allowed multiple file
     * result will be available here.
     *
     * @param filePaths Capture/Gallery image file
     */
    fun setImage(filePaths: ArrayList<String>) {
        setResult(filePaths)
    }

    /**
     * {@link CropProviders} Result will be available here.
     *
     * Check if compression is enable/required. If yes then start compression else return result.
     *
     * @param file Crop image file
     */
    fun setCropImage(file: File) {
        mCropFile = file

        mCameraProvider?.let {
            // Delete Camera file after crop. Else there will be two image for the same action.
            // In case of Gallery Provider, we will get original image path, so we will not delete that.
            mImageFile?.delete()
            mImageFile = null
        }

        if (mCompressionProvider.isCompressionRequired(file)) {
            mCompressionProvider.compress(file)
        } else {
            setResult(file)
        }
    }

    /**
     * {@link CompressionProvider} Result will be available here.
     *
     * @param file Compressed image file
     */
    fun setCompressedImage(file: File) {
        // This is the case when Crop is not enabled
        mCameraProvider?.let {
            // Delete Camera file after Compress. Else there will be two image for the same action.
            // In case of Gallery Provider, we will get original image path, so we will not delete that.
            mImageFile?.delete()
        }

        // If crop file is not null, Delete it after crop
        mCropFile?.delete()
        mCropFile = null

        setResult(file)
    }

    /**
     * Set Result, Image is successfully capture/picked/cropped/compressed.
     *
     * @param file final image file
     */
    private fun setResult(file: File) {
        val intent = Intent()
        intent.data = Uri.fromFile(file)
        intent.putExtra(ImagePicker.EXTRA_FILE_PATH, file.absolutePath)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    /**
     * Set multiple files result, Image is successfully capture/picked/cropped/compressed.
     *
     * @param filePaths final image file
     */
    private fun setResult(filePaths: ArrayList<String>) {
        val intent = Intent()
        intent.putExtra(ImagePicker.EXTRA_FILE_PATHS, filePaths)
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    /**
     * User has cancelled the task
     */
    fun setResultCancel() {
        setResult(Activity.RESULT_CANCELED, getCancelledIntent(this))
        finish()
    }

    /**
     * Error occurred while processing image
     *
     * @param message Error Message
     */
    fun setError(message: String) {
        val intent = Intent()
        intent.putExtra(ImagePicker.EXTRA_ERROR, message)
        setResult(ImagePicker.RESULT_ERROR, intent)
        finish()
    }
}
