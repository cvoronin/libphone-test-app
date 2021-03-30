package ru.cvoronin.libphone

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import ru.cvoronin.libphone.databinding.ActivityMainBinding

// https://libphonenumber.appspot.com/phonenumberparser?number=9621234567&country=RU

class MainActivity : AppCompatActivity(), RegionListener {

    private val phoneUtil: PhoneNumberUtil by lazy { PhoneNumberUtil.createInstance(this) }

    private val regionSubject = BehaviorSubject.create<String>()

    private val phoneSubject = BehaviorSubject.create<String>()

    private val compositeDisposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityMainBinding.inflate(layoutInflater)
        binding.regionListner = this
        setContentView(binding.root)

        regionSubject.onNext("RU")
        phoneSubject.onNext("")

        binding.editText.doAfterTextChanged { phoneSubject.onNext(it.toString()) }

        compositeDisposable += createFormatDisposable(binding)
        compositeDisposable += createFormatAsYouTimeDisposable(binding)
    }

    private fun createFormatDisposable(binding: ActivityMainBinding) =
        Observable
            .combineLatest(
                regionSubject,
                phoneSubject
            ) { regionCode, phoneNumber -> FormatParams(regionCode, phoneNumber) }
            .map { formatPhoneNumber(it) }
            .subscribeOn(Schedulers.computation())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { binding.phoneData = it }

    private fun createFormatAsYouTimeDisposable(binding: ActivityMainBinding) =
        Observable
            .combineLatest(
                regionSubject,
                phoneSubject
            ) { regionCode, phoneNumber -> FormatParams(regionCode, phoneNumber) }
            .map { formatAsYouType(it) }
            .subscribeOn(Schedulers.computation())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { binding.asYouType = it }

    override fun onDestroy() {
        compositeDisposable.dispose()
        super.onDestroy()
    }

    private fun formatPhoneNumber(params: FormatParams): FormattedPhoneNumber =
        try {
            val parsed = phoneUtil.parse(params.phoneNumber, params.regionCode)
            FormattedPhoneNumber(
                phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL),
                phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.NATIONAL),
                phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164),
                phoneUtil.format(parsed, PhoneNumberUtil.PhoneNumberFormat.RFC3966)
            )
        } catch (e: Exception) {
            e.printStackTrace()
            FormattedPhoneNumber.ERROR
        }

    private fun formatAsYouType(params: FormatParams): String {
        if (params.phoneNumber.isEmpty()) return ""

        var result = ""
        val formatter = phoneUtil.getAsYouTypeFormatter(params.regionCode)

        // Shlemiel the painter’s algorithm, но для прототипа пусть будет так
        params.phoneNumber.asSequence().forEach {
            result = formatter.inputDigit(it)
        }
        return result
    }

    override fun onRegionSelected(code: String) = regionSubject.onNext(code)
}

data class FormatParams(
    val regionCode: String,
    val phoneNumber: String
)

data class FormattedPhoneNumber(
    val international: String,
    val national: String,
    val e164: String,
    val RFC3966: String
) {
    companion object {
        val ERROR = FormattedPhoneNumber("", "", "", "")
    }
}

interface RegionListener {
    fun onRegionSelected(code: String)
}