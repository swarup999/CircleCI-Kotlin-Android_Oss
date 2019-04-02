package com.kickstarter.ui.activities

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.util.Pair
import android.view.View
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.transition.AutoTransition
import androidx.transition.Scene
import androidx.transition.Transition
import androidx.transition.TransitionManager
import com.kickstarter.R
import com.kickstarter.libs.ActivityRequestCodes
import com.kickstarter.libs.BaseActivity
import com.kickstarter.libs.KSString
import com.kickstarter.libs.qualifiers.RequiresActivityViewModel
import com.kickstarter.libs.rx.transformers.Transformers
import com.kickstarter.libs.rx.transformers.Transformers.observeForUI
import com.kickstarter.libs.utils.ViewUtils
import com.kickstarter.models.Project
import com.kickstarter.models.User
import com.kickstarter.ui.IntentKey
import com.kickstarter.ui.adapters.ProjectAdapter
import com.kickstarter.ui.data.LoginReason
import com.kickstarter.ui.fragments.RewardsFragment
import com.kickstarter.viewmodels.ProjectViewModel
import kotlinx.android.synthetic.main.project_layout.*
import kotlinx.android.synthetic.main.project_toolbar.*
import rx.android.schedulers.AndroidSchedulers

@RequiresActivityViewModel(ProjectViewModel.ViewModel::class)
class ProjectActivity : BaseActivity<ProjectViewModel.ViewModel>() {
    private lateinit var adapter: ProjectAdapter
    private lateinit var ksString: KSString

    private var grid8Dimen = R.dimen.grid_8

    private val projectBackButtonString = R.string.project_back_button
    private val managePledgeString = R.string.project_checkout_manage_navbar_title
    private val projectShareLabelString = R.string.project_accessibility_button_share_label
    private val projectShareCopyString = R.string.project_share_twitter_message
    private val projectStarConfirmationString = R.string.project_star_confirmation
    private val campaignString = R.string.project_subpages_menu_buttons_campaign
    private val creatorString = R.string.project_subpages_menu_buttons_creator

    private val animDuration = 200L
    private val showRewardsFragment: ObjectAnimator = ObjectAnimator.ofFloat(null, View.ALPHA, 0f, 1f)
    private val hideRewardsFragment: ObjectAnimator = ObjectAnimator.ofFloat(null, View.ALPHA, 1f, 0f)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.project_layout)

        this.adapter = ProjectAdapter(this.viewModel)
        project_recycler_view.adapter = this.adapter
        project_recycler_view.layoutManager = LinearLayoutManager(this)

        this.ksString = environment().ksString()

        this.viewModel.outputs.showRewardsFragment()
                .compose(bindToLifecycle())
                .compose(Transformers.observeForUI())
                .subscribe {
                    animateRewards(it)
                }

        this.viewModel.outputs.heartDrawableId()
                .compose(bindToLifecycle())
                .compose(Transformers.observeForUI())
                .subscribe { heart_icon.setImageDrawable(ContextCompat.getDrawable(this, it)) }

        this.viewModel.outputs.projectAndUserCountry()
                .compose(bindToLifecycle())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    this.renderProject(it.first.first, it.first.second, it.second)
                    this.setupRewardsFragment(it.first.first)
                }

        this.viewModel.outputs.startCampaignWebViewActivity()
                .compose(bindToLifecycle())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { this.startCampaignWebViewActivity(it) }

        this.viewModel.outputs.startCommentsActivity()
                .compose(bindToLifecycle())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { this.startCommentsActivity(it) }

        this.viewModel.outputs.startCreatorBioWebViewActivity()
                .compose(bindToLifecycle())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { this.startCreatorBioWebViewActivity(it) }

        this.viewModel.outputs.setActionButtonId()
                .compose(bindToLifecycle())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    if (it != null) {
                        val view = findViewById<View>(it)
                        ViewUtils.setGone(view, false)
                    }
                }

        this.viewModel.outputs.showShareSheet()
                .compose(bindToLifecycle())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { this.startShareIntent(it) }

        this.viewModel.outputs.startProjectUpdatesActivity()
                .compose(bindToLifecycle())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { this.startProjectUpdatesActivity(it) }

        this.viewModel.outputs.startVideoActivity()
                .compose(bindToLifecycle())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { this.startVideoActivity(it) }

        this.viewModel.outputs.startCheckoutActivity()
                .compose(bindToLifecycle())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { this.startCheckoutActivity(it) }

        this.viewModel.outputs.startManagePledgeActivity()
                .compose(bindToLifecycle())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { this.startManagePledge(it) }

        this.viewModel.outputs.startBackingActivity()
                .compose(bindToLifecycle())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { this.startBackingActivity(it) }

        this.viewModel.outputs.showSavedPrompt()
                .compose(bindToLifecycle())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { this.showStarToast() }

        this.viewModel.outputs.startLoginToutActivity()
                .compose(bindToLifecycle())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { this.startLoginToutActivity() }

        this.viewModel.outputs.viewToShow()
                .compose(observeForUI())
                .subscribe {
                    val view = findViewById<View>(it.first)
                    ViewUtils.setGone(view, false)

                    setProjectActionButtonVisibility(it.second)
                }

        back_project_button.setOnClickListener {
            this.viewModel.inputs.backProjectButtonClicked()
        }

        heart_icon.setOnClickListener {
            this.viewModel.inputs.heartButtonClicked()
        }

        native_back_this_project_button.setOnClickListener {
            this.viewModel.inputs.nativeBackProjectButtonClicked()
        }

        manage_pledge_button.setOnClickListener {
            this.viewModel.inputs.managePledgeButtonClicked()
        }

        rewards_toolbar.setNavigationOnClickListener {
            this.viewModel.inputs.hideRewardsFragmentClicked()
            this.project_toolbar.visibility = View.VISIBLE
        }

        share_icon.setOnClickListener {
            this.viewModel.inputs.shareButtonClicked()
        }

        view_pledge_button.setOnClickListener {
            this.viewModel.inputs.viewPledgeButtonClicked()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        this.project_recycler_view.adapter = null
    }

    private fun animateRewards(isExpanded: Boolean) {
        this.showRewardsFragment.removeAllUpdateListeners()
        this.showRewardsFragment.addUpdateListener { valueAnim ->
            val initialRadius = resources.getDimensionPixelSize(R.dimen.fab_radius).toFloat()
            val radius = initialRadius * if (isExpanded) 1 - valueAnim.animatedFraction else valueAnim.animatedFraction
            this.rewards_container.radius = radius
        }

        val durationTransition = AutoTransition()
        durationTransition.duration = animDuration
        TransitionManager.beginDelayedTransition(root, durationTransition)

        val set = AnimatorSet()
        this.showRewardsFragment.target = if (!isExpanded) native_back_this_project_button else pledge_container
        this.hideRewardsFragment.target = if (!isExpanded) pledge_container else native_back_this_project_button
        set.playTogether(this.showRewardsFragment, this.hideRewardsFragment)
        set.duration = animDuration

        set.addListener(object : Animator.AnimatorListener {
            override fun onAnimationRepeat(animation: Animator?) {}
            override fun onAnimationCancel(animation: Animator?) {}

            override fun onAnimationEnd(animation: Animator?) {
                if (isExpanded) {
                    native_back_this_project_button.visibility = View.GONE
                }
            }

            override fun onAnimationStart(animation: Animator?) {
                setRewardConstraints(isExpanded)
                if (!isExpanded) {
                    native_back_this_project_button.visibility = View.VISIBLE
                }
            }
        })

        set.start()
    }

    private fun renderProject(project: Project, configCountry: String, isHorizontalRewardsEnabled: Boolean) {
        this.adapter.takeProject(project, configCountry, isHorizontalRewardsEnabled)
    }

    private fun setupRewardsFragment(project: Project) {
        val rewardsFragment = supportFragmentManager.findFragmentById(R.id.fragment_rewards) as RewardsFragment
        rewardsFragment.takeProject(project)
    }

    private fun setProjectActionButtonVisibility(isHorizontalRewardsEnabled: Boolean) {
        project_action_buttons.visibility = when {
            ViewUtils.isLandscape(this) || isHorizontalRewardsEnabled -> View.GONE
            else -> View.VISIBLE
        }
    }

    private fun setRewardConstraints(expanded: Boolean) {
        val constraintSet = ConstraintSet()
        constraintSet.clone(root)
        if (!expanded) {
            constraintSet.clear(R.id.rewards_container, ConstraintSet.BOTTOM)
            constraintSet.connect(R.id.rewards_container, ConstraintSet.TOP, R.id.guideline, ConstraintSet.TOP, 0)
        } else {
            constraintSet.connect(R.id.rewards_container, ConstraintSet.TOP, R.id.root, ConstraintSet.TOP, 0)
            constraintSet.connect(R.id.rewards_container, ConstraintSet.BOTTOM, R.id.root, ConstraintSet.BOTTOM, 0)
        }
        constraintSet.applyTo(root)
    }

    private fun startCampaignWebViewActivity(project: Project) {
        startWebViewActivity(getString(this.campaignString), project.descriptionUrl())
    }

    private fun startCreatorBioWebViewActivity(project: Project) {
        startWebViewActivity(getString(this.creatorString), project.creatorBioUrl())
    }

    private fun startProjectUpdatesActivity(project: Project) {
        val intent = Intent(this, ProjectUpdatesActivity::class.java)
                .putExtra(IntentKey.PROJECT, project)
        startActivityWithTransition(intent, R.anim.slide_in_right, R.anim.fade_out_slide_out_left)
    }

    private fun showStarToast() {
        ViewUtils.showToastFromTop(this, getString(this.projectStarConfirmationString), 0, resources.getDimensionPixelSize(this.grid8Dimen))
    }

    private fun startCheckoutActivity(project: Project) {
        val intent = Intent(this, CheckoutActivity::class.java)
                .putExtra(IntentKey.PROJECT, project)
                .putExtra(IntentKey.URL, project.newPledgeUrl())
                .putExtra(IntentKey.TOOLBAR_TITLE, this.projectBackButtonString)
        startActivityWithTransition(intent, R.anim.slide_in_right, R.anim.fade_out_slide_out_left)
    }

    private fun startManagePledge(project: Project) {
        val intent = Intent(this, CheckoutActivity::class.java)
                .putExtra(IntentKey.PROJECT, project)
                .putExtra(IntentKey.URL, project.editPledgeUrl())
                .putExtra(IntentKey.TOOLBAR_TITLE, this.managePledgeString)
        startActivityWithTransition(intent, R.anim.slide_in_right, R.anim.fade_out_slide_out_left)
    }

    private fun startCommentsActivity(project: Project) {
        val intent = Intent(this, CommentsActivity::class.java)
                .putExtra(IntentKey.PROJECT, project)
        startActivityWithTransition(intent, R.anim.slide_in_right, R.anim.fade_out_slide_out_left)
    }

    // todo: limit the apps you can share to
    private fun startShareIntent(project: Project) {
        val shareMessage = this.ksString.format(getString(this.projectShareCopyString), "project_title", project.name())

        val intent = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, shareMessage + " " + project.webProjectUrl())
        startActivity(Intent.createChooser(intent, getString(this.projectShareLabelString)))
    }

    private fun startWebViewActivity(toolbarTitle: String, url: String) {
        val intent = Intent(this, WebViewActivity::class.java)
                .putExtra(IntentKey.TOOLBAR_TITLE, toolbarTitle)
                .putExtra(IntentKey.URL, url)
        startActivityWithTransition(intent, R.anim.slide_in_right, R.anim.fade_out_slide_out_left)
    }

    private fun startLoginToutActivity() {
        val intent = Intent(this, LoginToutActivity::class.java)
                .putExtra(IntentKey.LOGIN_REASON, LoginReason.STAR_PROJECT)
        startActivityForResult(intent, ActivityRequestCodes.LOGIN_FLOW)
    }

    private fun startBackingActivity(projectAndBacker: Pair<Project, User>) {
        val intent = Intent(this, BackingActivity::class.java)
                .putExtra(IntentKey.PROJECT, projectAndBacker.first)
                .putExtra(IntentKey.BACKER, projectAndBacker.second)
        startActivityWithTransition(intent, R.anim.slide_in_right, R.anim.fade_out_slide_out_left)
    }

    private fun startVideoActivity(project: Project) {
        val intent = Intent(this, VideoActivity::class.java)
                .putExtra(IntentKey.PROJECT, project)
        startActivity(intent)
    }

    override fun exitTransition(): Pair<Int, Int>? {
        return Pair.create(R.anim.fade_in_slide_in_left, R.anim.slide_out_right)
    }

    override fun back() {
        if (native_back_this_project_button.visibility == View.GONE) {
            this.viewModel.inputs.hideRewardsFragmentClicked()
        } else {
            super.back()
        }
    }
}
