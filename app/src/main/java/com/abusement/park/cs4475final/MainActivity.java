package com.abusement.park.cs4475final;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.VideoView;

import org.jcodec.api.JCodecException;
import org.jcodec.api.android.SequenceEncoder;
import org.jcodec.common.NIOUtils;
import org.jcodec.common.SeekableByteChannel;
import org.jcodec.containers.mp4.demuxer.MP4Demuxer;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static android.provider.MediaStore.Video.Thumbnails.MINI_KIND;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int IMAGE_REQUEST_CODE = 1;
    private static final int VIDEO_REQUEST_CODE = 2;

    private static final int UPLOAD_IMAGE_STATE = 0;
    private static final int UPLOAD_VIDEO_STATE = 1;


    private ImageView uploadedImageView;
    private VideoView uploadedVideoView;
    private MediaController mediaController;
    private ProgressBar progressBar;
    private String videoFilepath;
    private int state = -1;

    static {
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Open CV not loaded");
        } else {
            Log.d(TAG, "Open CV loaded!");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        uploadedImageView = (ImageView) findViewById(R.id.uploadImageView);
        uploadedVideoView = (VideoView) findViewById(R.id.uploadVideoView);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        mediaController = new MediaController(this);
        mediaController.setAnchorView(uploadedVideoView);

    }

    public void uploadImage(View button) {
        Intent chooseImage = new Intent().setType("image/*").setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(chooseImage, "Select image to cartoonify"), IMAGE_REQUEST_CODE);
    }

    public void uploadVideo(View button) {
        Intent chooseVideo = new Intent().setType("video/mp4").setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(chooseVideo, "Select video to cartoonify"), VIDEO_REQUEST_CODE);
    }

    public void cartoonify(View button) throws IOException, JCodecException {
        if (state == UPLOAD_IMAGE_STATE) {
            Bitmap bmp = ((BitmapDrawable) uploadedImageView.getDrawable()).getBitmap();
            bmp = getCartoonBitmap(bmp);
            displayDialog(bmp);
        }
        if (state == UPLOAD_VIDEO_STATE) {
            // apparently opencv has very limited video support on android
            File uploadedVideoFile = new File(videoFilepath);
            new FilterFramesTask().execute(uploadedVideoFile);
        }
    }

    @NonNull
    /**
     * Applies the cartoon filter
     */
    private Bitmap getCartoonBitmap(Bitmap bmp) {
        bmp = bmp.copy(Bitmap.Config.RGB_565, true);

        Mat cartoon_rgb = new Mat();
        Mat cartoon_color = new Mat();
        Mat cartoon_gray = new Mat();

        Utils.bitmapToMat(bmp, cartoon_rgb);
        Utils.bitmapToMat(bmp, cartoon_color);

        Mat temp = new Mat();
        for (int i = 0; i < 2; i++) {
            Imgproc.pyrDown(cartoon_color, temp);
            temp.copyTo(cartoon_color);
        }
        temp = new Mat();
        Imgproc.cvtColor(cartoon_color, cartoon_color, Imgproc.COLOR_BGRA2BGR);
        cartoon_color.copyTo(temp);
        for (int i = 0; i < 7; i++) {
            Imgproc.bilateralFilter(cartoon_color, temp, 9, 9, 7);
            temp.copyTo(cartoon_color);
        }

        for (int i = 0; i < 2; i++) {
            Imgproc.pyrUp(cartoon_color, temp);
            temp.copyTo(cartoon_color);
        }
        // WAAAY too slow
//        for (int row = 0; row < cartoon_color.height(); row++) {
//            for (int col = 0; col < cartoon_color.width(); col++) {
//                double[] oldPixels = cartoon_color.get(row, col);
//                for (int i = 0; i < oldPixels.length; i++) {
//                    oldPixels[i] = Math.floor(oldPixels[i] / 24) * 24;
//                }
//                cartoon_color.put(row, col, oldPixels);
//            }
//        }
        Imgproc.cvtColor(cartoon_rgb, cartoon_rgb, Imgproc.COLOR_BGRA2BGR);
        Imgproc.cvtColor(cartoon_color, cartoon_color, Imgproc.COLOR_BGRA2BGR);
        Imgproc.cvtColor(cartoon_rgb, cartoon_gray, Imgproc.COLOR_BGRA2GRAY);

        Imgproc.medianBlur(cartoon_gray, cartoon_gray, 7);
        Imgproc.adaptiveThreshold(cartoon_gray, cartoon_gray, 255,
                Imgproc.ADAPTIVE_THRESH_MEAN_C, Imgproc.THRESH_BINARY, 9, 2);
        Imgproc.cvtColor(cartoon_gray, cartoon_gray, Imgproc.COLOR_GRAY2BGR);
        Core.bitwise_and(cartoon_color, cartoon_gray, cartoon_gray);
        Utils.matToBitmap(cartoon_gray, bmp);
        return bmp;
    }

    private void displayDialog(Bitmap bitmap) {
        Dialog dialog = new Dialog(MainActivity.this);
        dialog.setContentView(R.layout.cartoon_layout);
        ((ImageView)dialog.findViewById(R.id.cartoon_image_view)).setImageBitmap(bitmap);
        dialog.show();
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == IMAGE_REQUEST_CODE && resultCode == RESULT_OK) {
            Log.d(TAG, "Upload complete");
            uploadedImageView.setImageURI(data.getData());
            uploadedVideoView.setVisibility(View.GONE);
            uploadedImageView.setVisibility(View.VISIBLE);
            state = UPLOAD_IMAGE_STATE;
        }
        if (requestCode == VIDEO_REQUEST_CODE && resultCode == RESULT_OK) {
            Log.d(TAG, "Upload complete");
            videoFilepath = getVideoFilePath(data.getData());
            uploadedImageView.setVisibility(View.VISIBLE);
            uploadedImageView.setImageBitmap(ThumbnailUtils.createVideoThumbnail(videoFilepath, MINI_KIND));
            uploadedVideoView.setMediaController(mediaController);
            state = UPLOAD_VIDEO_STATE;
        }
    }

   /**
     * We'll just make a copy of the file and return that file name
     * @param uri
     * @return
     */
    private String getVideoFilePath(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            File temp = new File(Environment.getExternalStorageDirectory(), File.separator + "cs4475FinalApp"
                    + File.separator);
            temp.mkdirs();
            temp = File.createTempFile("cs4475temp", ".mp4", temp);
            OutputStream os = new FileOutputStream(temp);
            ProgressDialog progressDialog = new ProgressDialog(this);
            progressDialog.setMessage("Getting the video...");
            progressDialog.setTitle("Please wait");
            progressDialog.setCancelable(false);
            progressDialog.show();
            byte[] buffer = new byte[1 << 13];
            int bytesRead;
            while ( (bytesRead = is.read(buffer)) != -1 ) {
                os.write(buffer, 0, bytesRead);
            }
            os.flush();
            os.close();
            progressDialog.dismiss();
            return temp.getCanonicalPath();
        } catch (Exception e) {
            Log.d(TAG, "ERROR GETTING VIDEO FILE PATH", e);
            throw new RuntimeException(e);
        }
    }

    private class FilterFramesTask extends AsyncTask<File, Integer, File> {

        @Override
        protected void onPreExecute() {
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected void onPostExecute(File generatedVideoFile) {
            try {
                uploadedVideoView.setVideoPath(generatedVideoFile.getCanonicalPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
            uploadedVideoView.setVisibility(View.VISIBLE);
            uploadedImageView.setVisibility(View.GONE);
            Toast.makeText(MainActivity.this, "Finished creating video", Toast.LENGTH_LONG).show();
            progressBar.setVisibility(View.GONE);
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            progressBar.setProgress(values[0]);
        }

        @Override
        protected File doInBackground(File... params) {
            File uploadedVideoFile = params[0];
            File generatedVideoFile = new File(Environment.getExternalStorageDirectory() + File.separator +
                    "cs4475FinalApp" + File.separator);
            generatedVideoFile.mkdirs();
            try {
                generatedVideoFile = File.createTempFile("Edited", ".mp4", generatedVideoFile);
                SequenceEncoder encoder = new SequenceEncoder(generatedVideoFile);
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                retriever.setDataSource(uploadedVideoFile.getCanonicalPath());
                SeekableByteChannel ch = NIOUtils.readableFileChannel(uploadedVideoFile);
                int totalFrames = new MP4Demuxer(ch).getVideoTrack().getMeta().getTotalFrames();
                Log.d(TAG, "STARTING TO ENCODE FRAMES");
                for (int i = 0; i < totalFrames; i++) {
                    Log.d(TAG, "ENCODING IMAGE " + i);
                    // my phone is 24 fps
                    Bitmap bmp = retriever.getFrameAtTime(i * 1000000 / 24, MediaMetadataRetriever.OPTION_CLOSEST);
                    bmp = getCartoonBitmap(bmp);
                    // have to compress it to a thumbnail b/c of the low memory allocated to android apps
                    encoder.encodeImage(ThumbnailUtils.extractThumbnail(bmp, 200, 200));
                    publishProgress((int) ((i + 1) * 1.0 / totalFrames * 100));
                }
                encoder.finish();
                return generatedVideoFile;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
