package jim.h.common.android.zxinglib.integrator;

import jim.h.common.android.zxinglib.CaptureActivity;
import jim.h.common.android.zxinglib.Intents;
import android.app.Activity;
import android.content.Intent;

/**
 * @author Jim.H
 */
public final class IntentIntegrator {

    public static final int REQUEST_CODE = 0x0ba7c0de;

    private IntentIntegrator() {
    }

    /**
     * @param activity 父 activity
     * @param layoutResId 扫描界面的布局文件资源ID
     * @param viewFinderViewResId 扫描界面中的显示扫描成功图像
     * @param previewViewResId 扫描界面中的显示扫描图像的控件资源ID
     * @param useFrontLight 是否打开闪光灯
     */
    public static void initiateScan(Activity activity, int layoutResId, int viewFinderViewResId,
                                    int previewViewResId, boolean useFrontLight) {
        Intent intent = new Intent(activity, CaptureActivity.class);
        intent.putExtra(CaptureActivity.KEY_LAYOUT_RES_ID, layoutResId);
        intent.putExtra(CaptureActivity.KEY_VIEW_FINDER_VIEW_RES_ID, viewFinderViewResId);
        intent.putExtra(CaptureActivity.KEY_PREVIEW_VIEW_RES_ID, previewViewResId);
        intent.putExtra(CaptureActivity.KEY_USE_FRONT_LIGHT, useFrontLight);
        activity.startActivityForResult(intent, REQUEST_CODE);
    }

    public static IntentResult parseActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                String contents = intent.getStringExtra(Intents.Scan.RESULT);
                String formatName = intent.getStringExtra(Intents.Scan.RESULT_FORMAT);
                return new IntentResult(contents, formatName);
            } else {
                return new IntentResult(null, null);
            }
        }
        return null;
    }
}
