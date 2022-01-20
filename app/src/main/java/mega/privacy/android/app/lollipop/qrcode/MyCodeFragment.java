package mega.privacy.android.app.lollipop.qrcode;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.appbar.AppBarLayout;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import mega.privacy.android.app.DatabaseHandler;
import mega.privacy.android.app.MegaApplication;
import mega.privacy.android.app.R;
import mega.privacy.android.app.UserCredentials;
import mega.privacy.android.app.components.ListenScrollChangesHelper;
import mega.privacy.android.app.utils.MegaProgressDialogUtil;
import mega.privacy.android.app.utils.ColorUtils;
import mega.privacy.android.app.utils.StringResourcesUtils;
import mega.privacy.android.app.utils.Util;
import nz.mega.sdk.MegaApiAndroid;
import nz.mega.sdk.MegaError;
import nz.mega.sdk.MegaRequest;
import nz.mega.sdk.MegaUser;

import static mega.privacy.android.app.utils.CacheFolderManager.*;
import static mega.privacy.android.app.utils.Constants.*;
import static mega.privacy.android.app.utils.FileUtil.*;
import static mega.privacy.android.app.utils.LogUtil.*;
import static mega.privacy.android.app.utils.AvatarUtil.*;

public class MyCodeFragment extends Fragment implements View.OnClickListener{

    final int RELATIVE_WIDTH = 280;
    final int WIDTH = 500;
    final int AVATAR_LEFT = 182;
    final int AVATAR_RIGHT = 317;
    final int AVATAR_WIDTH = 135;
    public final static String QR_IMAGE_FILE_NAME_OLD = "QRcode.jpg";
    public final static String QR_IMAGE_FILE_NAME = "QR_code_image.jpg";

    /** Avatar's border width */
    public final static int BORDER_WIDTH = 3;

    MegaUser myUser;
    String myEmail;
    MegaApiAndroid megaApi;
    DatabaseHandler dbH = null;
    Handler handler;

    long handle = -1;
    String contactLink = null;

    private ActionBar aB;
    private AppBarLayout abL;

    private RelativeLayout relativeContainerQRCode;
    private ImageView qrcode;
    private TextView qrcode_link;
    private Button qrcode_copy_link;
    private ScrollView scrollView;
    private View v;

    private Context context;

    private Bitmap qrCodeBitmap;
    private File qrFile = null;

    public static AlertDialog processingDialog;

    private boolean copyLink = true;
    private boolean createQR = false;

    DisplayMetrics outMetrics;

    public static MyCodeFragment newInstance() {
        logDebug("newInstance");
        MyCodeFragment fragment = new MyCodeFragment();
        return fragment;
    }

    @Override
    public void onCreate (Bundle savedInstanceState){
        logDebug("onCreate");

        super.onCreate(savedInstanceState);

        if (megaApi == null){
            megaApi = ((MegaApplication) ((Activity)context).getApplication()).getMegaApi();
        }

        myEmail = megaApi.getMyUser().getEmail();
        myUser = megaApi.getMyUser();
        dbH = DatabaseHandler.getDbHandler(context);
        handler = new Handler();

        if (savedInstanceState != null){
            handle = savedInstanceState.getLong("handle");
            contactLink = savedInstanceState.getString("contactLink");
        }

        //remove QR image in old format
        File oldImage = buildQrFile(context, myEmail + QR_IMAGE_FILE_NAME_OLD);
        if (oldImage != null) {
            oldImage.delete();
        }
    }

    public File queryIfQRExists() {
        logDebug("queryIfQRExists");
        qrFile = buildQrFile(context,myEmail + QR_IMAGE_FILE_NAME);
        if (isFileAvailable(qrFile)) {
            return qrFile;
        }
        return null;
    }

    public void setImageQR (){
        logDebug("setImageQR");

        if (qrFile.exists()) {
            if (qrFile.length() > 0) {
                BitmapFactory.Options bOpts = new BitmapFactory.Options();
                bOpts.inPurgeable = true;
                bOpts.inInputShareable = true;
                qrCodeBitmap = BitmapFactory.decodeFile(qrFile.getAbsolutePath(), bOpts);
                qrcode.setImageBitmap(qrCodeBitmap);
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putLong("handle", handle);
        outState.putString("contactLink", contactLink);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        logDebug("onCreateView");

        v = inflater.inflate(R.layout.fragment_mycode, container, false);

        Display display = ((QRCodeActivity) context).getWindowManager().getDefaultDisplay();
        outMetrics = new DisplayMetrics();
        display.getMetrics(outMetrics);

        if (aB == null){
            aB = ((AppCompatActivity)context).getSupportActionBar();
        }

        if(aB!=null){
            aB.setTitle(StringResourcesUtils.getString(R.string.section_qr_code)
                    .toUpperCase(Locale.getDefault()));

            aB.setHomeButtonEnabled(true);
            aB.setDisplayHomeAsUpEnabled(true);
        }

        abL = requireActivity().findViewById(R.id.app_bar_layout);
        scrollView = v.findViewById(R.id.my_code_scrollview);
        relativeContainerQRCode = (RelativeLayout) v.findViewById(R.id.qr_code_relative_container);
        qrcode = (ImageView) v.findViewById(R.id.qr_code_image);
        qrcode_link = (TextView) v.findViewById(R.id.qr_code_link);
        qrcode_copy_link = (Button) v.findViewById(R.id.qr_code_button_copy_link);
        copyLink = true;
        createQR = false;
        qrcode_copy_link.setText(getResources().getString(R.string.button_copy_link));
        qrcode_copy_link.setEnabled(false);
        qrcode_copy_link.setOnClickListener(this);

        if (contactLink != null){
            qrcode_link.setText(contactLink);
            qrcode_copy_link.setEnabled(true);
        }

        new ListenScrollChangesHelper().addViewToListen(scrollView, (v, scrollX, scrollY, oldScrollX, oldScrollY) -> checkScroll());

        Configuration configuration = getResources().getConfiguration();
        int width = getDP(RELATIVE_WIDTH);
        LinearLayout.LayoutParams params;
        if(configuration.orientation==Configuration.ORIENTATION_LANDSCAPE){
            params = new LinearLayout.LayoutParams(width-80, width-80);
            params.gravity = Gravity.CENTER;
            params.setMargins(0, 0, 0, getDP(20));
            relativeContainerQRCode.setLayoutParams(params);
            relativeContainerQRCode.setPadding(0, -40, 0, 0);

        }else{
            params = new LinearLayout.LayoutParams(width, width);
            params.gravity = Gravity.CENTER;
            params.setMargins(0, getDP(55), 0, getDP(58));
            relativeContainerQRCode.setLayoutParams(params);
        }
        createLink();

        return v;
    }

    public void checkScroll() {
        if (scrollView != null) {
            boolean withElevation = scrollView.canScrollVertically(-1);
            abL.setElevation(withElevation ? getResources().getDimension(R.dimen.toolbar_elevation) : 0);
            ColorUtils.changeStatusBarColorForElevation(requireActivity(), withElevation);
        }
    }

    int getDP(int value){
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    public Bitmap createQRCode (Bitmap qr, Bitmap avatar){
        logDebug("createQRCode");

        Bitmap qrCode = Bitmap.createBitmap(WIDTH,WIDTH, Bitmap.Config.ARGB_8888);
        int width = AVATAR_WIDTH;
        float offset = (float)(width / 2);

        Canvas c = new Canvas(qrCode);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        // Avatar border's color
        paint.setColor(ContextCompat.getColor(context, R.color.white_dark_grey));

        avatar = Bitmap.createScaledBitmap(avatar, width, width, false);
        c.drawBitmap(qr, 0f, 0f, null); // Util.dp2px(3)
        c.drawCircle(AVATAR_LEFT + offset, AVATAR_LEFT + offset, offset + Util.dp2px(BORDER_WIDTH),paint);
        c.drawBitmap(avatar, AVATAR_LEFT, AVATAR_LEFT, null);

        return qrCode;
    }

    public Bitmap queryQR () {
        Map<EncodeHintType, ErrorCorrectionLevel> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        BitMatrix bitMatrix;

        try {
            bitMatrix = new MultiFormatWriter().encode(contactLink, BarcodeFormat.QR_CODE, WIDTH, WIDTH, hints);
        } catch (WriterException e) {
            e.printStackTrace();
            return null;
        }
        int w = bitMatrix.getWidth();
        int h = bitMatrix.getHeight();
        int[] pixels = new int[w * h];
        int color = ContextCompat.getColor(context, R.color.dark_grey);

        Bitmap bitmap = Bitmap.createBitmap(WIDTH, WIDTH, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(ContextCompat.getColor(context, R.color.white_grey_700));
        c.drawRect(0, 0, WIDTH, WIDTH, paint);
        paint.setColor(color);

        for (int y = 0; y < h; y++) {
            int offset = y * w;
            for (int x = 0; x < w; x++) {
                pixels[offset + x] = bitMatrix.get(x, y) ? color : ContextCompat.getColor(context, R.color.white_grey_700);
            }
        }

        bitmap.setPixels(pixels, 0, w, 0, 0, w,  h);
        return bitmap;

    }

    public Bitmap setUserAvatar(){
        logDebug("setUserAvatar");

        File avatar = buildAvatarFile(context, myEmail + ".jpg");
        Bitmap bitmap = null;
        if (isFileAvailable(avatar)){
            if (avatar.length() > 0){
                BitmapFactory.Options bOpts = new BitmapFactory.Options();
                bOpts.inPurgeable = true;
                bOpts.inInputShareable = true;
                bitmap = BitmapFactory.decodeFile(avatar.getAbsolutePath(), bOpts);
                if (bitmap == null) {
                    return createDefaultAvatar();
                }
                else{
                    return getCircleBitmap(bitmap);
                }
            }
            else{
                return createDefaultAvatar();
            }
        }
        else{
            return createDefaultAvatar();
        }
    }

    private Bitmap getCircleBitmap(Bitmap bitmap) {
        logDebug("getCircleBitmap");

        final Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(output);

        final int color = Color.RED;
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        final RectF rectF = new RectF(rect);

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(color);
        canvas.drawOval(rectF, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);

        bitmap.recycle();

        return output;
    }

    public Bitmap createDefaultAvatar(){
        logDebug("createDefaultAvatar()");

        UserCredentials credentials = dbH.getCredentials();
        String fullName;
        if(credentials!=null){
            fullName = credentials.getFirstName();
            if (fullName == null) {
                fullName = credentials.getLastName();
                if (fullName == null) {

                    fullName = ((QRCodeActivity) context).getName();
                    if(fullName == null) {
                        fullName = myEmail;
                    }
                }
            }
        }
        else{
            fullName = myEmail;
        }
        return getDefaultAvatar(getColorAvatar(myUser), fullName, AVATAR_SIZE, true);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        logDebug("onConfigurationChanged");
        if(newConfig.orientation==Configuration.ORIENTATION_LANDSCAPE){
            logDebug("Changed to LANDSCAPE");

        }else{
            logDebug("Changed to PORTRAIT");

        }
    }

    @Override
    public void onAttach(Activity activity) {
        logDebug("onAttach");
        super.onAttach(activity);
        context = activity;
        aB = ((AppCompatActivity)activity).getSupportActionBar();
    }

    @Override
    public void onAttach(Context context) {
        logDebug("onAttach context");
        super.onAttach(context);
        this.context = context;
        aB = ((AppCompatActivity)getActivity()).getSupportActionBar();
    }

    @Override
    public void onClick(View v) {
        logDebug("onClick");
        switch (v.getId()) {
            case R.id.qr_code_button_copy_link: {
                if (copyLink) {
                    copyLink();
                }
                else {
                    createLink();
                }
                break;
            }
        }
    }

    public void copyLink () {
        logDebug("copyLink");

        ClipboardManager clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("label", contactLink);
        clipboardManager.setPrimaryClip(clip);
        ((QRCodeActivity) context).showSnackbar(v, getString(R.string.qrcode_link_copied));
    }

    public void createLink () {
        if (megaApi == null) {
            megaApi = ((MegaApplication) ((Activity)context).getApplication()).getMegaApi();
        }
        qrFile = queryIfQRExists();
        if (qrFile != null && qrFile.exists()) {
            setImageQR();
            megaApi.contactLinkCreate(false, (QRCodeActivity) context);
        }
        else {
            megaApi.contactLinkCreate(false, (QRCodeActivity) context);
            AlertDialog temp = null;
            try{
                temp = MegaProgressDialogUtil.createProgressDialog(context, getString(R.string.generatin_qr));
                temp.show();
            }
            catch(Exception e){
                logError(e.getMessage());
            }
            processingDialog = temp;
        }
    }

    public void initCreateQR(MegaRequest request, MegaError e){
        boolean reset = false;
        if (handle != -1 && handle != request.getNodeHandle() && copyLink){
            reset = true;
        }
        if (e.getErrorCode() == MegaError.API_OK) {
            logDebug("Contact link create LONG: " + request.getNodeHandle());
            logDebug("Contact link create BASE64: " + "https://mega.nz/C!" + MegaApiAndroid.handleToBase64(request.getNodeHandle()));

            handle = request.getNodeHandle();
            contactLink = "https://mega.nz/C!" + MegaApiAndroid.handleToBase64(request.getNodeHandle());
            qrcode_link.setText(contactLink);
            qrCodeBitmap = createQRCode(queryQR(), setUserAvatar());
            File qrCodeFile = buildQrFile(context, myEmail + QR_IMAGE_FILE_NAME);

            if (qrCodeFile != null) {
                try {
                    FileOutputStream out = new FileOutputStream(qrCodeFile, false);
                    qrCodeBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
                } catch (FileNotFoundException e1) {
                    e1.printStackTrace();
                }
            }
            qrcode.setImageBitmap(qrCodeBitmap);
            qrcode_copy_link.setEnabled(true);
            if (reset){
                ((QRCodeActivity) context).resetSuccessfully(true);
            }
            if (createQR){
                ((QRCodeActivity) context).showSnackbar(v, getResources().getString(R.string.qrcode_create_successfully));
                ((QRCodeActivity) context).createSuccessfully();
                qrcode_copy_link.setText(getResources().getString(R.string.button_copy_link));
                createQR = false;
                copyLink = true;
            }
            if (processingDialog != null) {
                processingDialog.dismiss();
            }
        }
        else {
            if (reset){
                ((QRCodeActivity) context).resetSuccessfully(false);
            }
        }
    }

    public void initDeleteQR(MegaRequest request, MegaError e){
        if (e.getErrorCode() == MegaError.API_OK){
            logDebug("Contact link delete:" + e.getErrorCode() + "_" + request.getNodeHandle() + "_"  + MegaApiAndroid.handleToBase64(request.getNodeHandle()));
            File qrCodeFile = buildQrFile(context, myEmail + QR_IMAGE_FILE_NAME);
            if (isFileAvailable(qrCodeFile)){
                qrCodeFile.delete();
            }
            ((QRCodeActivity) context).showSnackbar(v, getResources().getString(R.string.qrcode_delete_successfully));
            qrcode.setImageBitmap(Bitmap.createBitmap(WIDTH, WIDTH, Bitmap.Config.ARGB_8888));
            qrcode_copy_link.setText(getResources().getString(R.string.button_create_qr));
            copyLink = false;
            createQR = true;
            qrcode_link.setText("");
            ((QRCodeActivity) context).deleteSuccessfully();
        }
        else {
            ((QRCodeActivity) context).showSnackbar(v, getResources().getString(R.string.qrcode_delete_not_successfully));
        }
    }

    public void resetQRCode () {
        logDebug("resetQRCode");

        if (megaApi == null){
            megaApi = ((MegaApplication) ((Activity)context).getApplication()).getMegaApi();
        }
//        megaApi.contactLinkDelete(handle, this);
        megaApi.contactLinkCreate(true, (QRCodeActivity) context);
    }

    public void deleteQRCode() {
        logDebug("deleteQRCode");

        if (megaApi == null) {
            megaApi = ((MegaApplication) ((Activity)context).getApplication()).getMegaApi();
        }
        megaApi.contactLinkDelete(handle, (QRCodeActivity) context);
    }
}
