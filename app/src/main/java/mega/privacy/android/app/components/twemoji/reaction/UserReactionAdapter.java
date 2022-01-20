package mega.privacy.android.app.components.twemoji.reaction;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.RelativeLayout;

import java.util.ArrayList;

import mega.privacy.android.app.MegaApplication;
import mega.privacy.android.app.MegaContactDB;
import mega.privacy.android.app.R;
import mega.privacy.android.app.components.RoundedImageView;
import mega.privacy.android.app.components.twemoji.EmojiTextView;
import mega.privacy.android.app.lollipop.ContactInfoActivityLollipop;
import mega.privacy.android.app.lollipop.controllers.ChatController;
import mega.privacy.android.app.lollipop.megachat.ChatActivityLollipop;
import mega.privacy.android.app.utils.ContactUtil;
import nz.mega.sdk.MegaApiAndroid;
import nz.mega.sdk.MegaChatApiAndroid;
import nz.mega.sdk.MegaUser;

import static mega.privacy.android.app.utils.AvatarUtil.*;
import static mega.privacy.android.app.utils.Constants.*;
import static mega.privacy.android.app.utils.ContactUtil.*;
import static mega.privacy.android.app.utils.TextUtil.*;
import static mega.privacy.android.app.utils.Util.*;
import static nz.mega.sdk.MegaChatApiJava.MEGACHAT_INVALID_HANDLE;

public class UserReactionAdapter extends ArrayAdapter<Long> implements View.OnClickListener {

    private static final int MAX_WIDTH_PORT = 270;
    private MegaApiAndroid megaApi;
    private MegaChatApiAndroid megaChatApi;
    private Context context;
    private ChatController chatC;


    public UserReactionAdapter(Context context, ArrayList<Long> users) {
        super(context, 0, users);
        this.context = context;

        if (megaApi == null) {
            megaApi = MegaApplication.getInstance().getMegaApi();
        }

        if (megaChatApi == null) {
            megaChatApi = MegaApplication.getInstance().getMegaChatApi();
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        DisplayMetrics outMetrics = MegaApplication.getInstance().getBaseContext().getResources().getDisplayMetrics();
        long userHandle = getItem(position);
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.user_reaction_item, parent, false);
        }

        RelativeLayout layout = convertView.findViewById(R.id.layout);
        RoundedImageView imageView = convertView.findViewById(R.id.contact_list_thumbnail);
        EmojiTextView name = convertView.findViewById(R.id.contact_list_name);
        name.setMaxWidthEmojis(scaleWidthPx(MAX_WIDTH_PORT, outMetrics));
        layout.setOnClickListener(this);
        layout.setTag(userHandle);

        String userName;
        String email;
        chatC = new ChatController(context);

        boolean myUserHandle = isMyUserHandle(userHandle);
        if (myUserHandle) {
            email = megaChatApi.getMyEmail();
            userName = megaChatApi.getMyFullname();
            if (isTextEmpty(userName)) {
                userName = email;
            }
            userName = getContext().getString(R.string.chat_me_text_bracket, userName);
        } else {
            MegaContactDB contactDB = getContactDB(userHandle);
            if (contactDB != null) {
                userName = getContactNameDB(contactDB);
                email = contactDB.getMail();
            } else {
                chatC.setNonContactAttributesInDB(userHandle);
                email = chatC.getParticipantEmail(userHandle);
                userName = chatC.getParticipantFullName(userHandle);
                if (isTextEmpty(userName)) {
                    userName = email;
                }
            }
        }
        name.setText(userName);

        /*Default Avatar*/
        int avatarColor = getColorAvatar(userHandle);
        imageView.setImageBitmap(getDefaultAvatar(avatarColor, userName, AVATAR_SIZE, true));

        /*Avatar*/
        if (myUserHandle) {
            Bitmap bitmap = getAvatarBitmap(email);
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
            }
        } else {
            String nameFileHandle = MegaApiAndroid.userHandleToBase64(userHandle);
            Bitmap bitmap = isTextEmpty(email) ? getAvatarBitmap(nameFileHandle) : getUserAvatar(nameFileHandle, email);
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
            }
        }

        return convertView;
    }

    @Override
    public void onClick(View view) {
        long handle = (long) view.getTag();

        switch (view.getId()) {
            case R.id.layout:
                if (handle == MEGACHAT_INVALID_HANDLE) {
                    break;
                }

                if (context instanceof ChatActivityLollipop) {
                    if (!isMyUserHandle(handle)) {
                        String email = chatC.getParticipantEmail(handle);
                        MegaUser contact = megaApi.getContact(email);
                        if (contact != null && contact.getVisibility() == MegaUser.VISIBILITY_VISIBLE) {
                            ((ChatActivityLollipop) context).hideBottomSheet();
                            ContactUtil.openContactInfoActivity(context, email);
                        }
                    } else {
                        ((ChatActivityLollipop) context).hideBottomSheet();
                        ((ChatActivityLollipop) context).showSnackbar(SNACKBAR_TYPE, context.getString(R.string.contact_is_me), MEGACHAT_INVALID_HANDLE);
                    }
                }
                break;
        }
    }

    private boolean isMyUserHandle(long handle) {
        return handle == megaChatApi.getMyUserHandle();
    }
}