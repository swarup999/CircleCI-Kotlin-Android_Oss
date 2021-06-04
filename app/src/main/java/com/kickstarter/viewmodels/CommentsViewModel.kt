package com.kickstarter.viewmodels

import android.util.Pair
import androidx.annotation.NonNull
import com.kickstarter.libs.ActivityViewModel
import com.kickstarter.libs.CurrentUserType
import com.kickstarter.libs.Either
import com.kickstarter.libs.Environment
import com.kickstarter.libs.loadmore.LoadingType
import com.kickstarter.libs.loadmore.PaginatedViewModelOutput
import com.kickstarter.libs.rx.transformers.Transformers
import com.kickstarter.libs.rx.transformers.Transformers.combineLatestPair
import com.kickstarter.libs.utils.ObjectUtils
import com.kickstarter.libs.utils.ProjectUtils
import com.kickstarter.models.Comment
import com.kickstarter.models.Project
import com.kickstarter.models.Update
import com.kickstarter.models.User
import com.kickstarter.services.ApiClientType
import com.kickstarter.services.ApolloClientType
import com.kickstarter.services.apiresponses.commentresponse.CommentEnvelope
import com.kickstarter.services.mutations.PostCommentData
import com.kickstarter.ui.IntentKey
import com.kickstarter.ui.activities.CommentsActivity
import com.kickstarter.ui.data.CommentCardData
import com.kickstarter.ui.views.CommentComposerStatus
import org.joda.time.DateTime
import rx.Observable
import rx.subjects.BehaviorSubject
import rx.subjects.PublishSubject

interface CommentsViewModel {

    interface Inputs {
        fun refresh()
        fun nextPage()
        fun postComment(comment: String, createdAt: DateTime)
        fun postCommentToServer(commentCardData: CommentCardData)
    }

    interface Outputs : PaginatedViewModelOutput<CommentCardData> {
        fun currentUserAvatar(): Observable<String?>
        fun commentComposerStatus(): Observable<CommentComposerStatus>
        fun enableReplyButton(): Observable<Boolean>
        fun showCommentComposer(): Observable<Boolean>
        fun commentsList(): Observable<List<CommentCardData>>
        fun setEmptyState(): Observable<Boolean>
        fun insertComment(): Observable<CommentCardData>
        fun commentPosted(): Observable<CommentCardData>
        fun updateFailedComment(): Observable<CommentCardData>
    }

    class ViewModel(@NonNull val environment: Environment) : ActivityViewModel<CommentsActivity>(environment), Inputs, Outputs {

        private val currentUser: CurrentUserType = environment.currentUser()
        private val client: ApiClientType = environment.apiClient()
        private val apolloClient: ApolloClientType = environment.apolloClient()
        val inputs: Inputs = this
        val outputs: Outputs = this
        private val refresh = PublishSubject.create<Void>()
        private val nextPage = PublishSubject.create<Void>()

        private val currentUserAvatar = BehaviorSubject.create<String?>()
        private val commentComposerStatus = BehaviorSubject.create<CommentComposerStatus>()
        private val showCommentComposer = BehaviorSubject.create<Boolean>()
        private val commentsList = BehaviorSubject.create<List<CommentCardData>?>()
        private val disableReplyButton = BehaviorSubject.create<Boolean>()

        private val postComment = PublishSubject.create<Pair<String, DateTime>>()
        private val postCommentToServer = BehaviorSubject.create<CommentCardData>()
        private val isLoadingMoreItems = BehaviorSubject.create<Boolean>()
        private val isRefreshing = BehaviorSubject.create<Boolean>()
        private val enablePagination = BehaviorSubject.create<Boolean>()
        private val setEmptyState = BehaviorSubject.create<Boolean>()
        private val insertComment = BehaviorSubject.create<CommentCardData>()
        private val commentPosted = BehaviorSubject.create<CommentCardData>()
        private val updateFailedComment = BehaviorSubject.create<CommentCardData>()
        private val failedPostedCommentObserver = BehaviorSubject.create<Void>()

        private var lastCommentCursour: String? = null
        override var loadMoreListData = mutableListOf<CommentCardData>()
        init {

            val loggedInUser = this.currentUser.loggedInUser()
                .filter { u -> u != null }
                .map { requireNotNull(it) }

            loggedInUser
                .compose(bindToLifecycle())
                .subscribe {
                    currentUserAvatar.onNext(it.avatar().small())
                }

            loggedInUser
                .compose(bindToLifecycle())
                .subscribe {
                    showCommentComposer.onNext(true)
                }

            val projectOrUpdate = intent()
                .map<Any?> {
                    val project = it.getParcelableExtra(IntentKey.PROJECT) as? Project
                    val update = it.getParcelableExtra(IntentKey.UPDATE)as? Update
                    project?.let {
                        Either.Left<Project?, Update?>(it)
                    }
                        ?: Either.Right<Project?, Update?>(update)
                }
                .ofType(Either::class.java)
                .take(1)

            val initialProject = projectOrUpdate.map {
                it as? Either<Project?, Update?>
            }.flatMap {
                it?.either<Observable<Project?>>(
                    { value: Project? -> Observable.just(value) },
                    { u: Update? -> client.fetchProject(u?.projectId().toString()).compose(Transformers.neverError()) }
                )
            }.map { requireNotNull(it) }
                .share()

            initialProject
                .compose(combineLatestPair(currentUser.observable()))
                .compose(bindToLifecycle())
                .subscribe {
                    val composerStatus = getCommentComposerStatus(Pair(it.first, it.second))
                    showCommentComposer.onNext(composerStatus != CommentComposerStatus.GONE)
                    commentComposerStatus.onNext(composerStatus)
                }

            loadCommentList(initialProject)

            this.currentUser.loggedInUser()
                .compose(Transformers.takePairWhen(this.postComment))
                .map {
                    buildCommentBody(it)
                }
                .compose<Pair<Comment, Project?>>(combineLatestPair(initialProject))
                .map {
                    CommentCardData.builder().comment(it.first).project(it.second).build()
                }
                .compose(bindToLifecycle())
                .subscribe {
                    this.insertComment.onNext(it)
                }

            val commentData = this.postCommentToServer.map { requireNotNull(it.comment) }

            this.currentUser.loggedInUser()
                .compose(Transformers.takePairWhen(commentData))
                .compose(Transformers.takePairWhen(this.failedPostedCommentObserver))
                .map {

                    Comment.builder()
                        .body(it.first.second.body())
                        .parentId(-1)
                        .authorBadges(listOf())
                        .createdAt(it.first.second.createdAt())
                        .cursor("")
                        .deleted(false)
                        .id(-1)
                        .repliesCount(0)
                        .author(it.first.first)
                        .build()
                }
                .compose<Pair<Comment, Project?>>(combineLatestPair(initialProject))
                .map {
                    CommentCardData.builder().comment(it.first).project(it.second).commentCardState(2).build()
                }
                .compose(bindToLifecycle())
                .subscribe {
                    this.updateFailedComment.onNext(it)
                }
            Observable
                .combineLatest(initialProject, commentData) { project, comment ->
                    return@combineLatest this.apolloClient.createComment(
                        PostCommentData(
                            project = project,
                            body = comment.body(),
                            clientMutationId = null,
                            parentId = null
                        )
                    ).doOnError {
                        this.failedPostedCommentObserver.onNext(null)
                    }
                        .onErrorResumeNext(Observable.empty())
                }
                .switchMap {
                    it
                }
                .compose<Pair<Comment, Project?>>(combineLatestPair(initialProject))
                .map {
                    CommentCardData.builder().comment(it.first).project(it.second).build()
                }
                .subscribe {
                    this.commentPosted.onNext(it)
                }
        }

        private fun loadCommentList(initialProject: Observable<Project>) {
            val projectSlug = initialProject
                .map { requireNotNull(it?.slug()) }

            projectSlug
                .compose(CommentEnvelopeTransformer(initialProject))
                .compose(bindToLifecycle())
                .subscribe {
                    bindCommentList(it.first, LoadingType.NORMAL, it.second)
                }

            projectSlug
                .compose(Transformers.takeWhen(this.nextPage))
                .doOnNext {
                    this.isLoadingMoreItems.onNext(true)
                }
                .compose(CommentEnvelopeTransformer(initialProject))
                .compose(bindToLifecycle())
                .subscribe {
                    updatePaginatedData(
                        LoadingType.LOAD_MORE,
                        it.first
                    )
                }

            projectSlug
                .compose(Transformers.takeWhen(this.refresh))
                .doOnNext {
                    this.isRefreshing.onNext(true)
                    // reset cursor
                    lastCommentCursour = null
                }.compose(CommentEnvelopeTransformer(initialProject))
                .compose(bindToLifecycle())
                .subscribe {
                    bindCommentList(it.first, LoadingType.PULL_REFRESH, it.second)
                }
        }

        private fun mapToCommentCardDataList(it: Pair<CommentEnvelope, Project?>) =
            it.first.comments?.map { comment: Comment ->
                CommentCardData.builder().comment(comment).project(it.second).build()
            }

        private fun buildCommentBody(it: Pair<User, Pair<String, DateTime>>): Comment {
            return Comment.builder()
                .body(it.second.first)
                .parentId(-1)
                .authorBadges(listOf())
                .createdAt(it.second.second)
                .cursor("")
                .deleted(false)
                .id(-1)
                .repliesCount(0)
                .author(it.first)
                .build()
        }

        private fun bindCommentList(commentCardDataList: List<CommentCardData>, loadingType: LoadingType, totalCount: Int?) {
            totalCount?.let { count ->
                this.setEmptyState.onNext(count < 1)
                updatePaginatedData(
                    loadingType,
                    commentCardDataList

                )
            }
        }

        inner class CommentEnvelopeTransformer(private val initialProject: Observable<Project>) : Observable.Transformer<String, Pair<List<CommentCardData>, Int>> {
            override fun call(t: Observable<String>): Observable<Pair<List<CommentCardData>, Int>> {
                return t.switchMap {
                    apolloClient.getProjectComments(it, lastCommentCursour)
                }
                    .filter { ObjectUtils.isNotNull(it) }
                    .compose<Pair<CommentEnvelope, Project?>>(combineLatestPair(initialProject))
                    .map { Pair(mapToCommentCardDataList(it), it.first.totalCount) }
            }
        }

        private fun getCommentComposerStatus(projectAndUser: Pair<Project, User?>) =
            when {
                projectAndUser.second == null -> CommentComposerStatus.GONE
                projectAndUser.first.isBacking || ProjectUtils.userIsCreator(projectAndUser.first, projectAndUser.second) -> CommentComposerStatus.ENABLED
                else -> CommentComposerStatus.DISABLED
            }

        override fun refresh() = refresh.onNext(null)
        override fun nextPage() = nextPage.onNext(null)

        override fun currentUserAvatar(): Observable<String?> = currentUserAvatar
        override fun commentComposerStatus(): Observable<CommentComposerStatus> = commentComposerStatus
        override fun showCommentComposer(): Observable<Boolean> = showCommentComposer
        override fun commentsList(): Observable<List<CommentCardData>> = commentsList
        override fun enableReplyButton(): Observable<Boolean> = disableReplyButton

        override fun setEmptyState(): Observable<Boolean> = setEmptyState
        override fun isLoadingMoreItems(): Observable<Boolean> = isLoadingMoreItems
        override fun enablePagination(): Observable<Boolean> = enablePagination
        override fun isRefreshing(): Observable<Boolean> = isRefreshing
        override fun insertComment(): Observable<CommentCardData> = this.insertComment
        override fun commentPosted(): Observable<CommentCardData> = this.commentPosted
        override fun updateFailedComment(): Observable<CommentCardData> = this.updateFailedComment

        override fun postComment(comment: String, createdAt: DateTime) = postComment.onNext(Pair(comment, createdAt))
        override fun postCommentToServer(commentCardData: CommentCardData) = postCommentToServer.onNext(commentCardData)

        override fun bindPaginatedData(data: List<CommentCardData>?) {
            lastCommentCursour = data?.lastOrNull()?.comment?.cursor()
            data?.let { loadMoreListData.addAll(it) }
            commentsList.onNext(loadMoreListData)
            this.isRefreshing.onNext(false)
            this.isLoadingMoreItems.onNext(false)
        }

        override fun updatePaginatedState(enabled: Boolean) {
            enablePagination.onNext(enabled)
        }
    }
}
