package cz.budikpet.bachelorwork.mvp.main

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import android.util.Log
import com.google.api.services.calendar.model.Calendar
import com.google.api.services.calendar.model.CalendarListEntry
import cz.budikpet.bachelorwork.MyApplication
import cz.budikpet.bachelorwork.data.Repository
import cz.budikpet.bachelorwork.data.models.ItemType
import cz.budikpet.bachelorwork.data.models.Model
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import javax.inject.Inject

class MainActivityViewModel : ViewModel() {
    private val TAG = "MY_${this.javaClass.simpleName}"

    private val events = MutableLiveData<List<Model.Event>>()

    @Inject
    internal lateinit var repository: Repository

    private var compositeDisposable = CompositeDisposable()

    init {
        MyApplication.appComponent.inject(this)
    }

    fun onDestroy() {
        compositeDisposable.clear()
    }

    fun checkAuthorization(response: AuthorizationResponse?, exception: AuthorizationException?) {
        repository.checkAuthorization(response, exception)
    }

    fun signOut() {
        repository.signOut()
    }

    fun getSiriusApiEvents(): LiveData<List<Model.Event>> {
        return events
    }

    fun searchSiriusApiEvents(itemType: ItemType, id: String) {
        val disposable = repository.searchSiriusApiEvents(itemType, id)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result ->
                    events.postValue(result.events)
                },
                { error -> Log.e(TAG, "Error: ${error}") }
            )

        compositeDisposable.add(disposable)
    }

    fun getCalendarList() {
        val FIELDS = "id,summary"
        val FEED_FIELDS = "items($FIELDS)"

        val disposable = repository.getGoogleCalendarServiceObservable()
            .flatMap { calendar ->
                Single.fromCallable {
                    calendar.calendarList().list().setFields(FEED_FIELDS).execute()
                }
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result ->
                    Log.i(TAG, "GetCalendarEvents")
                    for (item in result.items) {
                        Log.i(TAG, "Name: ${item.summary}")
                    }
                },
                { error ->
                    Log.e(TAG, "GetCalendarEvents: $error")
                }
            )

        compositeDisposable.add(disposable)
    }

    fun addGoogleCalendar(name: String) {
        val FIELDS = "id,summary"
        val FEED_FIELDS = "items($FIELDS)"

        val calendarModel = Calendar()
        calendarModel.summary = name

        val disposable = repository.getGoogleCalendarServiceObservable()
            .flatMap { calendarService ->
                Log.i(TAG, "AddingCalendar")
                Single.fromCallable {
                    calendarService.calendars()
                        .insert(calendarModel)
                        .setFields(FIELDS)
                        .execute()
                }
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io())
                    .flatMap { createdCalendar ->
                        val entry: CalendarListEntry = createdCalendar.createMyEntry()
                        Single.fromCallable {
                            calendarService.calendarList()
                                .update(createdCalendar.id, entry)
                                .setColorRgbFormat(true)
                                .execute()
                        }
                    }
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result ->
                    Log.i(TAG, "AddGoogleCalendar")
                    Log.i(TAG, result.summary)
                },
                { error ->
                    Log.e(TAG, "AddGoogleCalendar: ${error}")
                }
            )

        compositeDisposable.add(disposable)
    }

    fun getGoogleCalendarEntries(name: String) {
        val disposable = repository.getCalendarEventsObservable(name)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { result ->
                    Log.i(TAG, "AddGoogleCalendar")
                    Log.i(TAG, result.toString())
                },
                { error ->
                    Log.e(TAG, "AddGoogleCalendar: ${error}")
                })
        compositeDisposable.add(disposable)
    }
}

private fun Calendar.createMyEntry(): CalendarListEntry {
    val entry = CalendarListEntry()
    entry.id = id
    entry.hidden = true
    entry.foregroundColor = "#000000"
    entry.backgroundColor = "#d3d3d3"

    return entry
}
