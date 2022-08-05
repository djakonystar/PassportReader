package site.texnopos.djakonystar.passportreader.viewmodel

import android.content.Context
import android.nfc.tech.IsoDep
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.jmrtd.BACKeySpec
import site.texnopos.djakonystar.passportreader.data.ReadTask
import site.texnopos.djakonystar.passportreader.model.EDocument
import site.texnopos.djakonystar.passportreader.util.Resource

class ReadViewModel(private val readTask: ReadTask): ViewModel() {
    private val compositeDisposable = CompositeDisposable()

    private var mutableEDocument: MutableLiveData<Resource<EDocument>> = MutableLiveData()
    val eDocument: LiveData<Resource<EDocument>> = mutableEDocument

    fun doRead(context: Context, isoDep: IsoDep, bacKey: BACKeySpec) {
        mutableEDocument.value = Resource.loading()
        readTask.doTask(context, isoDep, bacKey)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { eDocument ->
                    mutableEDocument.value = Resource.success(eDocument)
                },
                { e ->
                    mutableEDocument.value = Resource.error(e.localizedMessage)
                }
            )
    }

    override fun onCleared() {
        super.onCleared()
        compositeDisposable.clear()
    }
}
