package mega.privacy.android.app.utils;

import android.content.Context;
import android.content.Intent;
import android.util.Base64;

import com.jeremyliao.liveeventbus.LiveEventBus;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import mega.privacy.android.app.MegaContactAdapter;
import mega.privacy.android.app.MegaContactDB;
import mega.privacy.android.app.R;
import mega.privacy.android.app.lollipop.ContactInfoActivityLollipop;
import mega.privacy.android.app.lollipop.megachat.ContactAttachmentActivityLollipop;
import nz.mega.sdk.MegaApiJava;
import nz.mega.sdk.MegaStringList;
import nz.mega.sdk.MegaStringMap;
import nz.mega.sdk.MegaUser;
import mega.privacy.android.app.MegaApplication;

import static mega.privacy.android.app.constants.BroadcastConstants.*;
import static mega.privacy.android.app.constants.EventConstants.EVENT_CONTACT_NAME_CHANGE;
import static mega.privacy.android.app.utils.Constants.ACTION_CHAT_OPEN;
import static mega.privacy.android.app.utils.Constants.CHAT_ID;
import static mega.privacy.android.app.utils.Constants.MESSAGE_ID;
import static mega.privacy.android.app.utils.Constants.NAME;
import static mega.privacy.android.app.utils.TextUtil.*;
import static mega.privacy.android.app.utils.LogUtil.*;
import static nz.mega.sdk.MegaApiJava.INVALID_HANDLE;

public class ContactUtil {

    public static MegaContactDB getContactDB(long contactHandle) {
        return MegaApplication.getInstance().getDbH().findContactByHandle(String.valueOf(contactHandle));
    }

    public static String getMegaUserNameDB(MegaUser user) {
        if (user == null) return null;
        String nameContact = getContactNameDB(user.getHandle());
        if (nameContact != null) {
            return nameContact;
        }

        return user.getEmail();
    }

    public static String getContactNameDB(MegaContactDB contactDB) {
        if (contactDB == null) {
            return null;
        }

        String nicknameText = contactDB.getNickname();
        if (nicknameText != null) {
            return nicknameText;
        }

        String firstNameText = contactDB.getName();
        String lastNameText = contactDB.getLastName();
        String emailText = contactDB.getMail();

        return buildFullName(firstNameText, lastNameText, emailText);
    }

    public static String getContactNameDB(long contactHandle) {
        MegaContactDB contactDB = getContactDB(contactHandle);
        if (contactDB != null) {
            return getContactNameDB(contactDB);
        }

        return null;
    }

    public static String getNicknameContact(long contactHandle) {
        MegaContactDB contactDB = getContactDB(contactHandle);
        if (contactDB == null)
            return null;

        return contactDB.getNickname();
    }

    public static String getNicknameContact(String email) {
        MegaContactDB contactDB = MegaApplication.getInstance().getDbH().findContactByEmail(email);
        if (contactDB != null) {
            return contactDB.getNickname();
        }

        return null;
    }

    /**
     * Method for obtaining the nickname to be displayed in the notifications in the notifications section.
     *
     * @param context Context of Activity.
     * @param email   The user email.
     * @return The nickname.
     */
    public static String getNicknameForNotificationsSection(Context context, String email) {
        String nickname = getNicknameContact(email);
        if (nickname != null) {
            return String.format(context.getString(R.string.section_notification_user_with_nickname), nickname, email);
        }

        return email;
    }

    public static String buildFullName(String name, String lastName, String mail) {
        String fullName = "";
        if (!isTextEmpty(name)) {
            fullName = name;
            if (!isTextEmpty(lastName)) {
                fullName = fullName + " " + lastName;
            }
        } else if (!isTextEmpty(lastName)) {
            fullName = lastName;
        } else if (!isTextEmpty(mail)) {
            fullName = mail;
        }
        return fullName;
    }

    public static String getFirstNameDB(long contactHandle) {
        MegaContactDB contactDB = getContactDB(contactHandle);
        if (contactDB != null) {
            String nicknameText = contactDB.getNickname();
            if (nicknameText != null) {
                return nicknameText;
            }

            String firstNameText = contactDB.getName();
            if (!isTextEmpty(firstNameText)) {
                return firstNameText;
            }

            String lastNameText = contactDB.getLastName();
            if (!isTextEmpty(lastNameText)) {
                return lastNameText;
            }

            String emailText = contactDB.getMail();
            if (!isTextEmpty(emailText)) {
                return emailText;
            }
        }
        return "";
    }

    public static void updateDBNickname(MegaApiJava api, Context context, MegaStringMap map) {
        ArrayList<MegaContactAdapter> contactsDB = getContactsDBList(api);
        if (contactsDB == null || contactsDB.isEmpty()) return;
        //No nicknames
        if (map == null || map.size() == 0) {
            for (int i = 0; i < contactsDB.size(); i++) {
                long contactDBHandle = contactsDB.get(i).getMegaUser().getHandle();
                String nickname = getNicknameContact(contactDBHandle);
                if (nickname != null) {
                    MegaApplication.getInstance().getDbH().setContactNickname(null, contactDBHandle);
                    notifyNicknameUpdate(context, contactDBHandle);
                }
            }
            return;
        }

        //Some nicknames
        MegaStringList listHandles = map.getKeys();
        for (int i = 0; i < contactsDB.size(); i++) {
            long contactDBHandle = contactsDB.get(i).getMegaUser().getHandle();
            String newNickname = null;
            for (int j = 0; j < listHandles.size(); j++) {
                long userHandle = MegaApiJava.base64ToUserHandle(listHandles.get(j));
                if (contactDBHandle == userHandle) {
                    newNickname = getNewNickname(map, listHandles.get(j));
                    break;
                }
            }
            String oldNickname = contactsDB.get(i).getMegaContactDB().getNickname();
            if ((newNickname == null && oldNickname == null) || (newNickname != null && oldNickname != null && newNickname.equals(oldNickname))) {
                continue;
            } else {
                MegaApplication.getInstance().getDbH().setContactNickname(newNickname, contactDBHandle);
                notifyNicknameUpdate(context, contactDBHandle);
            }
        }
    }

    public static void notifyNicknameUpdate(Context context, long userHandle) {
        notifyUserNameUpdate(context, ACTION_UPDATE_NICKNAME, userHandle);
        LiveEventBus.get(EVENT_CONTACT_NAME_CHANGE, Long.class).post(userHandle);
    }

    public static void notifyFirstNameUpdate(Context context, long userHandle) {
        notifyUserNameUpdate(context, ACTION_UPDATE_FIRST_NAME, userHandle);
        LiveEventBus.get(EVENT_CONTACT_NAME_CHANGE, Long.class).post(userHandle);
    }

    public static void notifyLastNameUpdate(Context context, long userHandle) {
        notifyUserNameUpdate(context, ACTION_UPDATE_LAST_NAME, userHandle);
        LiveEventBus.get(EVENT_CONTACT_NAME_CHANGE, Long.class).post(userHandle);
    }

    public static void notifyUserNameUpdate(Context context, String action, long userHandle) {
        Intent intent = new Intent(action)
            .putExtra(EXTRA_USER_HANDLE, userHandle);
        context.sendBroadcast(intent);
    }

    private static String getNewNickname(MegaStringMap map, String key) {
        String nicknameEncoded = map.get(key);
        if (nicknameEncoded == null)
            return null;

        try {
            byte[] data = Base64.decode(nicknameEncoded, Base64.URL_SAFE);
            return new String(data, StandardCharsets.UTF_8);
        } catch (java.lang.Exception e) {
            logError("Error retrieving new nickname " + e);
            return null;
        }
    }

    private static ArrayList<MegaContactAdapter> getContactsDBList(MegaApiJava api) {
        ArrayList<MegaContactAdapter> visibleContacts = new ArrayList<>();
        ArrayList<MegaUser> contacts = api.getContacts();
        for (int i = 0; i < contacts.size(); i++) {
            if (contacts.get(i).getVisibility() == MegaUser.VISIBILITY_VISIBLE) {
                long contactHandle = contacts.get(i).getHandle();
                MegaContactDB contactDB = getContactDB(contactHandle);
                String fullName = getContactNameDB(contactDB);
                MegaContactAdapter megaContactAdapter = new MegaContactAdapter(contactDB, contacts.get(i), fullName);
                visibleContacts.add(megaContactAdapter);
            }
        }
        return visibleContacts;
    }

    public static void updateFirstName(String name, String email) {
        MegaApplication.getInstance().getDbH().setContactName(name, email);
    }

    public static void updateLastName(String lastName, String email) {
        MegaApplication.getInstance().getDbH().setContactLastName(lastName, email);
    }

    /**
     * Checks if the user who their handle is received by parameter is a contact.
     *
     * @param userHandle    handle of the user
     * @return true if the user is a contact, false otherwise.
     */
    public static boolean isContact(long userHandle) {
        if (userHandle == INVALID_HANDLE) {
            return false;
        }

        return isContact(MegaApiJava.userHandleToBase64(userHandle));
    }

    /**
     * Checks if the user who their email of handle in base64 is received by parameter is a contact.
     *
     * @param emailOrUserHandleBase64   email or user's handle in base64
     * @return true if the user is a contact, false otherwise.
     */
    public static boolean isContact(String emailOrUserHandleBase64) {
        if (isTextEmpty(emailOrUserHandleBase64)) {
            return false;
        }

        MegaUser contact = MegaApplication.getInstance().getMegaApi().getContact(emailOrUserHandleBase64);
        return contact != null && contact.getVisibility() == MegaUser.VISIBILITY_VISIBLE;
    }

    /**
     * Gets a contact's email from DB.
     *
     * @param contactHandle contact's identifier
     * @return The contact's email.
     */
    public static String getContactEmailDB(long contactHandle) {
        MegaContactDB contactDB = getContactDB(contactHandle);
        return contactDB != null ? getContactEmailDB(contactDB) : null;
    }

    /**
     * Gets a contact's email from DB.
     *
     * @param contactDB contact's MegaContactDB
     * @return The contact's email.
     */
    public static String getContactEmailDB(MegaContactDB contactDB) {
        return contactDB != null ? contactDB.getMail() : null;
    }

    /**
     * Method to open ContactInfoActivityLollipop.class.
     *
     * @param context Activity context.
     * @param name    The name of the contact.
     */
    public static void openContactInfoActivity(Context context, String name) {
        openContactInfoActivity(context, name, false);
    }

    /**
     * Method to open ContactInfoActivityLollipop.class.
     *
     * @param context        Activity context.
     * @param name           The name of the contact.
     * @param isChatRoomOpen True, if the chatRoom is already open. False, otherwise.
     */
    public static void openContactInfoActivity(Context context, String name, boolean isChatRoomOpen) {
        Intent i = new Intent(context, ContactInfoActivityLollipop.class);
        i.putExtra(NAME, name);
        i.putExtra(ACTION_CHAT_OPEN, isChatRoomOpen);
        context.startActivity(i);
    }

    /**
     * Method to open ContactAttachmentActivityLollipop.class.
     *
     * @param context Activity context.
     * @param chatId  The ID of a chat.
     * @param msgId   The ID of a message.
     */
    public static void openContactAttachmentActivity(Context context, long chatId, long msgId) {
        Intent i = new Intent(context, ContactAttachmentActivityLollipop.class);
        i.putExtra(CHAT_ID, chatId);
        i.putExtra(MESSAGE_ID, msgId);
        context.startActivity(i);
    }
}
