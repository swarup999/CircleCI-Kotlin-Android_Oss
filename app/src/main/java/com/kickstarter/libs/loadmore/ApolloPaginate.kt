package com.kickstarter.libs.loadmore

import android.util.Pair
import com.kickstarter.libs.rx.transformers.Transformers
import com.kickstarter.models.ApolloEnvelope
import rx.Observable
import rx.functions.Func1
import rx.functions.Func2
import rx.subjects.PublishSubject
import java.net.MalformedURLException
import java.util.ArrayList

class ApolloPaginate<Data, Envelope : ApolloEnvelope, Params>(
    val nextPage: Observable<Void>,
    val startOverWith: Observable<Params>,
    val envelopeToListOfData: Func1<Envelope, List<Data>>,
    val loadWithParams: Func1<Pair<Params, String?>, Observable<Envelope>>,
    val pageTransformation: Func1<List<Data>, List<Data>>,
    val clearWhenStartingOver: Boolean = true,
    val concater: Func2<List<Data>?, List<Data>?, List<Data>?>,
    val distinctUntilChanged: Boolean
) {
    private val _morePath = PublishSubject.create<String?>()
    private val _isFetching = PublishSubject.create<Boolean>()
    private var isFetching: Observable<Boolean?> = this._isFetching
    private var loadingPage: Observable<Int?>? = null
    private var paginatedData: Observable<List<Data>>? = null

    init {
        paginatedData =
            startOverWith.switchMap { firstPageParams: Params ->
                this.dataWithPagination(
                    firstPageParams
                )
            }
        loadingPage =
            startOverWith.switchMap<Int> {
                nextPage.scan(1, { accum: Int, _ -> accum + 1 })
            }
    }

    class Builder<Data, Envelope : ApolloEnvelope, Params> {
        private var nextPage: Observable<Void>? = null
        private var startOverWith: Observable<Params>? = null
        private var envelopeToListOfData: Func1<Envelope, List<Data>>? = null
        private var loadWithParams: Func1<Pair<Params, String?>, Observable<Envelope>>? = null
        private var pageTransformation: Func1<List<Data>, List<Data>> = Func1<List<Data>, List<Data>> {
            x: List<Data> ->
            x
        }
        private var clearWhenStartingOver = false
        private var concater: Func2<List<Data>?, List<Data>?, List<Data>?> =
            Func2 { xs: List<Data>?, ys: List<Data>? ->
                mutableListOf<Data>().apply {
                    xs?.toMutableList()?.let { this.addAll(it) }
                    ys?.toMutableList()?.let { this.addAll(it) }
                }.toList()
            }
        private var distinctUntilChanged = false

        /**
         * [Required] An observable that emits whenever a new page of data should be loaded.
         */
        fun nextPage(nextPage: Observable<Void>): Builder<Data, Envelope, Params> {
            this.nextPage = nextPage
            return this
        }

        /**
         * [Optional] An observable that emits when a fresh first page should be loaded.
         */
        fun startOverWith(startOverWith: Observable<Params>): Builder<Data, Envelope, Params> {
            this.startOverWith = startOverWith
            return this
        }

        /**
         * [Required] A function that takes an `Envelope` instance and returns the list of data embedded in it.
         */
        fun envelopeToListOfData(envelopeToListOfData: Func1<Envelope, List<Data>>): Builder<Data, Envelope, Params> {
            this.envelopeToListOfData = envelopeToListOfData
            return this
        }

        /**
         * [Required] A function that takes a `Params` and performs the associated network request
         * and returns an `Observable<Envelope>`
         </Envelope> */
        fun loadWithParams(loadWithParams: Func1<Pair<Params, String?>, Observable<Envelope>>): Builder<Data, Envelope, Params> {
            this.loadWithParams = loadWithParams
            return this
        }

        /**
         * [Optional] Function to transform every page of data that is loaded.
         */
        fun pageTransformation(pageTransformation: Func1<List<Data>, List<Data>>): Builder<Data, Envelope, Params> {
            this.pageTransformation = pageTransformation
            return this
        }

        /**
         * [Optional] Determines if the list of loaded data is cleared when starting over from the first page.
         */
        fun clearWhenStartingOver(clearWhenStartingOver: Boolean): Builder<Data, Envelope, Params> {
            this.clearWhenStartingOver = clearWhenStartingOver
            return this
        }

        /**
         * [Optional] Determines how two lists are concatenated together while paginating. A regular `ListUtils::concat` is probably
         * sufficient, but sometimes you may want `ListUtils::concatDistinct`
         */
        fun concater(concater: Func2<List<Data>?, List<Data>?, List<Data>?>): Builder<Data, Envelope, Params> {
            this.concater = concater
            return this
        }

        /**
         * [Optional] Determines if the list of loaded data is should be distinct until changed.
         */
        fun distinctUntilChanged(distinctUntilChanged: Boolean): Builder<Data, Envelope, Params> {
            this.distinctUntilChanged = distinctUntilChanged
            return this
        }

        @Throws(RuntimeException::class)
        fun build(): ApolloPaginate<Data, Envelope, Params> {
            // Early error when required field is not set
            if (nextPage == null) {
                throw RuntimeException("`nextPage` is required")
            }
            if (envelopeToListOfData == null) {
                throw RuntimeException("`envelopeToListOfData` is required")
            }
            if (loadWithParams == null) {
                throw RuntimeException("`loadWithParams` is required")
            }

            // Default params for optional fields
            if (startOverWith == null) {
                startOverWith = Observable.just(null)
            }

            return ApolloPaginate(
                nextPage!!,
                startOverWith!!,
                envelopeToListOfData!!,
                loadWithParams!!,
                pageTransformation,
                clearWhenStartingOver,
                concater,
                distinctUntilChanged
            )
        }
    }

    companion object {
        fun <Data, Envelope : ApolloEnvelope, FirstPageParams> builder(): Builder<Data, Envelope, FirstPageParams> = Builder()
    }

    /**
     * Returns an observable that emits the accumulated list of paginated data each time a new page is loaded.
     */
    private fun dataWithPagination(firstPageParams: Params): Observable<List<Data>?>? {
        val data = paramsAndMoreUrlWithPagination(firstPageParams)?.concatMap {
            fetchData(it)
        }?.takeUntil { obj ->
            obj?.isEmpty()
        }

        val paginatedData =
            if (clearWhenStartingOver)
                data?.scan(ArrayList(), concater)
            else
                data?.scan(concater)

        return if (distinctUntilChanged)
            paginatedData?.distinctUntilChanged()
        else
            paginatedData
    }

    /**
     * Returns an observable that emits the params for the next page of data *or* the more URL for the next page.
     */
    private fun paramsAndMoreUrlWithPagination(firstPageParams: Params): Observable<Pair<Params, String?>>? {
        return _morePath
            .map { path: String? ->
                Pair<Params, String?>(
                    firstPageParams,
                    path
                )
            }
            .compose(Transformers.takeWhen(nextPage))
            .startWith(Pair(firstPageParams, null))
    }

    private fun fetchData(paginatingData: Pair<Params, String?>): Observable<List<Data>?> {

        return loadWithParams.call(paginatingData)
            .retry(2)
            .compose(Transformers.neverError())
            .doOnNext { envelope: Envelope ->
                keepMorePath(envelope)
            }
            .map(envelopeToListOfData)
            .map(this.pageTransformation)
            .takeUntil { data: List<Data> -> data.isEmpty() }
            .doOnSubscribe { _isFetching.onNext(true) }
            .doAfterTerminate {
                _isFetching.onNext(false)
            }
    }

    private fun keepMorePath(envelope: Envelope) {
        try {
            _morePath.onNext(envelope.pageInfoEnvelope()?.endCursor)
        } catch (ignored: MalformedURLException) {
            ignored.printStackTrace()
        }
    }

    // Outputs
    fun paginatedData(): Observable<List<Data>>? {
        return paginatedData
    }

    fun isFetching(): Observable<Boolean?> {
        return isFetching
    }

    fun loadingPage(): Observable<Int?>? {
        return loadingPage
    }
}
