package jim.h.common.android.zxinglib;

import java.io.IOException;
import java.util.Vector;

import jim.h.common.android.zxinglib.camera.CameraManager;
import jim.h.common.android.zxinglib.view.ViewfinderView;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;

/**
 * 扫描activity的抽象类
 * 
 * @author Jim.H
 */
public class CaptureActivity extends Activity implements Callback {

    public static final String     KEY_LAYOUT_RES_ID           = "layoutResId";
    public static final String     KEY_VIEW_FINDER_VIEW_RES_ID = "viewFinderViewResId";
    public static final String     KEY_PREVIEW_VIEW_RES_ID     = "previewViewResId";
    public static final String     KEY_USE_FRONT_LIGHT         = "useFrontLight";

    private static final String    TAG                         = CaptureActivity.class
                                                                       .getSimpleName();

    private static final long      VIBRATE_DURATION            = 200L;
    private static final long      INTENT_RESULT_DURATION      = 1500L;

    private CaptureActivityHandler handler;
    private ViewfinderView         viewfinderView;
    private boolean                hasSurface;
    private Vector<BarcodeFormat>  decodeFormats;
    private String                 characterSet;
    private InactivityTimer        inactivityTimer;
    private boolean                vibrate                     = false;
    private int                    layoutResId;
    private int                    viewFinderViewResId;
    private int                    previewViewResId;
    private boolean                useFrontLight;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        layoutResId = intent.getIntExtra(KEY_LAYOUT_RES_ID, R.layout.capture);
        viewFinderViewResId = intent.getIntExtra(KEY_VIEW_FINDER_VIEW_RES_ID, R.id.viewfinder_view);
        previewViewResId = intent.getIntExtra(KEY_PREVIEW_VIEW_RES_ID, R.id.preview_view);
        useFrontLight = intent.getBooleanExtra(KEY_USE_FRONT_LIGHT, false);

        setContentView(layoutResId);
        //初始化 CameraManager
        CameraManager.init(getApplication(), useFrontLight);

        viewfinderView = (ViewfinderView) findViewById(viewFinderViewResId);
        hasSurface = false;
        inactivityTimer = new InactivityTimer(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        SurfaceView surfaceView = (SurfaceView) findViewById(previewViewResId);
        SurfaceHolder surfaceHolder = surfaceView.getHolder();
        if (hasSurface) {
            initCamera(surfaceHolder);
        } else {
            surfaceHolder.addCallback(this);
            surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }
        decodeFormats = null;
        characterSet = null;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (handler != null) {
            handler.quitSynchronously();
            handler = null;
        }
        CameraManager.get().closeDriver();
    }

    @Override
    protected void onDestroy() {
        inactivityTimer.shutdown();
        super.onDestroy();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (!hasSurface) {
            hasSurface = true;
            initCamera(holder);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        hasSurface = false;
    }

    public ViewfinderView getViewfinderView() {
        return viewfinderView;
    }

    public Handler getHandler() {
        return handler;
    }

    public void drawViewfinder() {
        viewfinderView.drawViewfinder();
    }

    public void handleDecode(Result obj, Bitmap barcode) {
        inactivityTimer.onActivity();
        drawResultPoints(barcode, obj);

        playBeepSoundAndVibrate();
        viewfinderView.drawResultBitmap(barcode);
        Log.i(TAG, obj.getBarcodeFormat().toString() + ":" + obj.getText());

        Intent intent = new Intent(getIntent().getAction());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        intent.putExtra(Intents.Scan.RESULT, obj.toString());
        intent.putExtra(Intents.Scan.RESULT_FORMAT, obj.getBarcodeFormat().toString());
        byte[] rawBytes = obj.getRawBytes();
        if (rawBytes != null && rawBytes.length > 0) {
            intent.putExtra(Intents.Scan.RESULT_BYTES, rawBytes);
        }
        Message message = Message.obtain(handler, R.id.return_scan_result);
        message.obj = intent;
        handler.sendMessageDelayed(message, INTENT_RESULT_DURATION);
    }

    private void drawResultPoints(Bitmap barcode, Result rawResult) {
        ResultPoint[] points = rawResult.getResultPoints();
        if (points != null && points.length > 0) {
            Canvas canvas = new Canvas(barcode);
            Paint paint = new Paint();
            paint.setColor(0xffffffff);
            paint.setStrokeWidth(3.0f);
            paint.setStyle(Paint.Style.STROKE);
            Rect border = new Rect(2, 2, barcode.getWidth() - 2, barcode.getHeight() - 2);
            canvas.drawRect(border, paint);

            paint.setColor(0xc000ff00);
            if (points.length == 2) {
                paint.setStrokeWidth(4.0f);
                drawLine(canvas, paint, points[0], points[1]);
            } else if (points.length == 4
                    && (rawResult.getBarcodeFormat().equals(BarcodeFormat.UPC_A) || rawResult
                            .getBarcodeFormat().equals(BarcodeFormat.EAN_13))) {
                drawLine(canvas, paint, points[0], points[1]);
                drawLine(canvas, paint, points[2], points[3]);
            } else {
                paint.setStrokeWidth(10.0f);
                for (ResultPoint point : points) {
                    canvas.drawPoint(point.getX(), point.getY(), paint);
                }
            }
        }
    }

    private static void drawLine(Canvas canvas, Paint paint, ResultPoint a, ResultPoint b) {
        canvas.drawLine(a.getX(), a.getY(), b.getX(), b.getY(), paint);
    }

    private void initCamera(SurfaceHolder surfaceHolder) {
        try {
            CameraManager.get().openDriver(surfaceHolder);
        } catch (IOException ioe) {
            return;
        } catch (RuntimeException e) {
            return;
        }
        if (handler == null) {
            handler = new CaptureActivityHandler(this, decodeFormats, characterSet);
        }
    }

    private void playBeepSoundAndVibrate() {
        if (vibrate) {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            vibrator.vibrate(VIBRATE_DURATION);
        }
    }
}
