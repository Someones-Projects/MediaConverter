package com.f1uctus.mediaconverter;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.MediaController;
import android.widget.SeekBar;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.arthenica.mobileffmpeg.Config;
import com.arthenica.mobileffmpeg.FFmpeg;
import com.arthenica.mobileffmpeg.FFprobe;
import com.arthenica.mobileffmpeg.MediaInformation;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static com.arthenica.mobileffmpeg.Config.RETURN_CODE_CANCEL;
import static com.arthenica.mobileffmpeg.Config.RETURN_CODE_SUCCESS;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_OPEN = 123;
    private static final int REQUEST_SAVE = 124;

    private String TEMP_OUTPUT_PATH;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Config.enableLogCallback(message -> Log.d(Config.TAG, message.getText()));
        TEMP_OUTPUT_PATH = getCacheDir().getAbsolutePath() + "/temp";
    }

    public void onChooseFileClick(View view) {
        withReadPermission(() -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT)
                .setType("video/*");

            startActivityForResult(
                Intent.createChooser(intent, "Выберите видеофайл"),
                REQUEST_OPEN
            );
        });
    }

    private String inputFilePath;
    private String outputFilePath;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_OPEN) {
                Uri inUri = data.getData();
                inputFilePath = PathUtils.getPath(this, inUri);
                MediaInformation info = FFprobe.getMediaInformation(inputFilePath);
                if (info != null) {
                    play(R.id.videoView1, inputFilePath);
                    outputFilePath = null;
                } else {
                    Toast.makeText(this, "Выбранный файл не является корректным видеофайлом", Toast.LENGTH_LONG).show();
                }
            } else if (requestCode == REQUEST_SAVE) {
                Uri outUri = data.getData();
                outputFilePath = PathUtils.getPath(this, outUri);
            }
        }
    }

    public void play(int videoViewId, String filePath) {
        final VideoView vv = findViewById(videoViewId);
        vv.setVideoPath(filePath);
        MediaController mediaController = new MediaController(this);
        vv.setMediaController(mediaController);
        mediaController.setMediaPlayer(vv);
        vv.setVisibility(View.VISIBLE);
        vv.start();
        vv.setOnCompletionListener(mp -> vv.start());
    }

    public void onConvertClick(View view) {
        SeekBar brightnessBar = findViewById(R.id.brightnessBar);
        double brightness = (brightnessBar.getProgress() - 50) / 50.0;
        withWritePermission(() -> {
            if (outputFilePath == null) {
                String ext = MimeTypeMap.getFileExtensionFromUrl(inputFilePath);
                outputFilePath = TEMP_OUTPUT_PATH + "." + ext;
            }
            int rc = FFmpeg.execute("-i " + inputFilePath + " -y -vf eq=brightness=" + brightness + " -c:a copy " + outputFilePath);

            if (rc == RETURN_CODE_SUCCESS) {
                Log.i(Config.TAG, "Command execution completed successfully.");
                ((VideoView) findViewById(R.id.videoView1)).seekTo(0);
                play(R.id.videoView2, outputFilePath);
            } else if (rc == RETURN_CODE_CANCEL) {
                Log.i(Config.TAG, "Command execution cancelled by user.");
            } else {
                Log.i(Config.TAG, String.format("Command execution failed with rc=%d and the output below.", rc));
                Config.printLastCommandOutput(Log.INFO);
            }
        });
    }

    public void onSaveClick(View view) {
        selectOutputFile();
    }

    private void selectOutputFile() {
        withWritePermission(() -> {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .setType("video/*");
            startActivityForResult(intent, REQUEST_SAVE);
        });
    }

    private void withReadPermission(Runnable action) {
        int permissionCheck = readFilesCode();
        if (permissionCheck != PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                new String[] { Manifest.permission.READ_EXTERNAL_STORAGE },
                0
            );
            permissionCheck = readFilesCode();
        }
        if (permissionCheck == PERMISSION_GRANTED) {
            action.run();
        }
    }

    private void withWritePermission(Runnable action) {
        int permissionCheck = writeFilesCode();
        if (permissionCheck != PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE },
                0
            );
            permissionCheck = writeFilesCode();
        }
        if (permissionCheck == PERMISSION_GRANTED) {
            action.run();
        }
    }

    private int readFilesCode() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    private int writeFilesCode() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }
}