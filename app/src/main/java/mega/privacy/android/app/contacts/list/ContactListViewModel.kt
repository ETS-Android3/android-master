package mega.privacy.android.app.contacts.list

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import mega.privacy.android.app.R
import mega.privacy.android.app.arch.BaseRxViewModel
import mega.privacy.android.app.contacts.list.data.ContactActionItem
import mega.privacy.android.app.contacts.list.data.ContactActionItem.Type
import mega.privacy.android.app.contacts.list.data.ContactItem
import mega.privacy.android.app.contacts.usecase.GetChatRoomUseCase
import mega.privacy.android.app.contacts.usecase.GetContactRequestsUseCase
import mega.privacy.android.app.contacts.usecase.GetContactsUseCase
import mega.privacy.android.app.contacts.usecase.RemoveContactUseCase
import mega.privacy.android.app.utils.LogUtil.logError
import mega.privacy.android.app.utils.RxUtil.debounceImmediate
import mega.privacy.android.app.utils.StringResourcesUtils.getString
import mega.privacy.android.app.utils.notifyObserver
import nz.mega.sdk.MegaUser
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * ViewModel that handles all related logic to Contacts for the current user.
 *
 * @property getContactsUseCase         Use case to retrieve current contacts
 * @property getContactRequestsUseCase  Use case to retrieve contact requests
 * @property getChatRoomUseCase         Use case to get current chat room for existing user
 * @property removeContactUseCase       Use case to remove existing contact
 */
@HiltViewModel
class ContactListViewModel @Inject constructor(
    private val getContactsUseCase: GetContactsUseCase,
    private val getContactRequestsUseCase: GetContactRequestsUseCase,
    private val getChatRoomUseCase: GetChatRoomUseCase,
    private val removeContactUseCase: RemoveContactUseCase
) : BaseRxViewModel() {

    companion object {
        private const val REQUEST_TIMEOUT_IN_MS = 100L
    }

    private var queryString: String? = null
    private val contacts: MutableLiveData<List<ContactItem.Data>> = MutableLiveData()
    private val contactActions: MutableLiveData<List<ContactActionItem>> = MutableLiveData()

    init {
        retrieveContactActions()
        retrieveContacts()
    }

    private fun retrieveContactActions() {
        getContactRequestsUseCase.getIncomingRequestsSize()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onNext = { incomingRequests ->
                    contactActions.value = listOf(
                        ContactActionItem(Type.REQUESTS, getString(R.string.section_requests), incomingRequests),
                        ContactActionItem(Type.GROUPS, getString(R.string.section_groups))
                    )
                },
                onError = { error ->
                    logError(error.stackTraceToString())
                }
            )
            .addTo(composite)
    }

    private fun retrieveContacts() {
        getContactsUseCase.get()
            .debounceImmediate(REQUEST_TIMEOUT_IN_MS, TimeUnit.MILLISECONDS)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onNext = { items ->
                    contacts.value = items.toList()
                },
                onError = { error ->
                    logError(error.stackTraceToString())
                }
            )
            .addTo(composite)
    }

    fun getContactActions(): LiveData<List<ContactActionItem>> =
        contactActions

    fun getRecentlyAddedContacts(): LiveData<List<ContactItem>> =
        contacts.map { items ->
            if (queryString.isNullOrBlank() && items.any { it.isNew }) {
                val itemsWithHeaders = mutableListOf<ContactItem>()
                itemsWithHeaders.add(ContactItem.Header(getString(R.string.section_recently_added)))
                itemsWithHeaders.addAll(items.filter { it.isNew })
                itemsWithHeaders.add(ContactItem.Header(getString(R.string.section_contacts)))
                itemsWithHeaders
            } else {
                emptyList()
            }
        }

    fun getContactsWithHeaders(): LiveData<List<ContactItem>> =
        contacts.map { items ->
            val itemsWithHeaders = mutableListOf<ContactItem>()
            items?.forEachIndexed { index, item ->
                if (queryString.isNullOrBlank()) {
                    if (index == 0 || !items[index - 1].getFirstCharacter().equals(items[index].getFirstCharacter(), true)) {
                        itemsWithHeaders.add(ContactItem.Header(item.getFirstCharacter()))
                    }
                    itemsWithHeaders.add(item)
                } else if (item.matches(queryString!!)) {
                    itemsWithHeaders.add(item)
                }
            }
            itemsWithHeaders
        }

    fun getContact(userHandle: Long): LiveData<ContactItem.Data?> =
        contacts.map { contact -> contact.find { it.handle == userHandle } }

    fun getMegaUser(userHandle: Long): LiveData<MegaUser> =
        getContact(userHandle).switchMap { user ->
            val result = MutableLiveData<MegaUser>()
            getContactsUseCase.getMegaUser(user!!.email)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onSuccess = { megaUser ->
                        result.value = megaUser
                    },
                    onError = { error ->
                        logError(error.stackTraceToString())
                    }
                )
                .addTo(composite)
            result
        }

    fun getChatRoomId(userHandle: Long): LiveData<Long> {
        val result = MutableLiveData<Long>()
        getChatRoomUseCase.get(userHandle)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onSuccess = { chatId ->
                    result.value = chatId
                },
                onError = { error ->
                    logError(error.stackTraceToString())
                }
            )
            .addTo(composite)
        return result
    }

    fun removeContact(megaUser: MegaUser) {
        removeContactUseCase.remove(megaUser)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(onError = { error ->
                logError(error.stackTraceToString())
            })
            .addTo(composite)
    }

    fun setQuery(query: String?) {
        queryString = query
        contacts.notifyObserver()
    }
}
