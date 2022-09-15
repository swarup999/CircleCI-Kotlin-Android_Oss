package com.kickstarter.ui.activities

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import com.kickstarter.R
import com.kickstarter.databinding.ActivitySettingsPaymentMethodsBinding
import com.kickstarter.libs.utils.extensions.getEnvironment
import com.kickstarter.libs.utils.extensions.getPaymentSheetConfiguration
import com.kickstarter.models.StoredCard
import com.kickstarter.ui.adapters.PaymentMethodsAdapter
import com.kickstarter.ui.extensions.showErrorSnackBar
import com.kickstarter.ui.extensions.showErrorToast
import com.kickstarter.ui.extensions.showSnackbar
import com.kickstarter.viewmodels.PaymentMethodsViewModel
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult
import com.stripe.android.paymentsheet.model.PaymentOption
import rx.android.schedulers.AndroidSchedulers

class PaymentMethodsSettingsActivity : AppCompatActivity() {

    private lateinit var adapter: PaymentMethodsAdapter
    private var showDeleteCardDialog: AlertDialog? = null

    private lateinit var binding: ActivitySettingsPaymentMethodsBinding
    private lateinit var flowController: PaymentSheet.FlowController

    private lateinit var viewModelFactory: PaymentMethodsViewModel.Factory
    private val viewModel: PaymentMethodsViewModel by viewModels { viewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        this.getEnvironment()?.let { env ->
            viewModelFactory = PaymentMethodsViewModel.Factory(env)
        }

        binding = ActivitySettingsPaymentMethodsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setUpRecyclerView()

        flowController = PaymentSheet.FlowController.create(
            activity = this,
            paymentOptionCallback = ::onPaymentOption,
            paymentResultCallback = ::onPaymentSheetResult
        )

        this.viewModel.outputs.cards()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { setCards(it) }

        this.viewModel.outputs.dividerIsVisible()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                binding.paymentsDivider.isGone = !it
            }

        this.viewModel.outputs.error()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { showSnackbar(binding.settingPaymentMethodsActivityToolbar.paymentMethodsToolbar, it) }

        this.viewModel.outputs.progressBarIsVisible()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                binding.progressBar.isGone = !it
            }

        this.viewModel.outputs.showDeleteCardDialog()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { lazyDeleteCardConfirmationDialog().show() }

        this.viewModel.success()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { showSnackbar(binding.settingPaymentMethodsActivityToolbar.paymentMethodsToolbar, R.string.Got_it_your_changes_have_been_saved) }

        binding.addNewCard.setOnClickListener {
            this.viewModel.inputs.newCardButtonClicked()
        }

        this.viewModel.outputs.presentPaymentSheet()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                flowControllerPresentPaymentOption(it)
            }

        this.viewModel.showError()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                showErrorSnackBar(binding.settingPaymentMethodsActivityToolbar.paymentMethodsToolbar, getString(R.string.general_error_something_wrong))
            }
    }

    private fun flowControllerPresentPaymentOption(clientSecret: String) {
        flowController.configureWithSetupIntent(
            setupIntentClientSecret = clientSecret,
            configuration = this.getPaymentSheetConfiguration(),
            callback = ::onConfigured
        )
    }

    private fun onConfigured(success: Boolean, error: Throwable?) {
        if (success) {
            flowController.presentPaymentOptions()
        } else {
            showErrorToast(this, binding.paymentMethodsContent, getString(R.string.general_error_something_wrong))
        }
    }

    private fun onPaymentOption(paymentOption: PaymentOption?) {
        paymentOption?.let {
            this.viewModel.inputs.savePaymentOption()
            flowController.confirm()
        }
    }

    fun onPaymentSheetResult(paymentSheetResult: PaymentSheetResult) {
        when (paymentSheetResult) {
            is PaymentSheetResult.Canceled -> {
                showErrorSnackBar(binding.paymentMethodsContent, getString(R.string.general_error_oops))
            }
            is PaymentSheetResult.Failed -> {
                showErrorSnackBar(binding.paymentMethodsContent, getString(R.string.general_error_something_wrong))
            }
            is PaymentSheetResult.Completed -> {
                showSnackbar(binding.settingPaymentMethodsActivityToolbar.paymentMethodsToolbar, R.string.Got_it_your_changes_have_been_saved)
            }
        }
    }

    private fun lazyDeleteCardConfirmationDialog(): AlertDialog {
        if (this.showDeleteCardDialog == null) {
            this.showDeleteCardDialog = AlertDialog.Builder(this, R.style.AlertDialog)
                .setCancelable(false)
                .setTitle(R.string.Remove_this_card)
                .setMessage(R.string.Are_you_sure_you_wish_to_remove_this_card)
                .setNegativeButton(R.string.No_nevermind) { _, _ -> lazyDeleteCardConfirmationDialog().dismiss() }
                .setPositiveButton(R.string.Yes_remove) { _, _ -> this.viewModel.inputs.confirmDeleteCardClicked() }
                .create()
        }
        return this.showDeleteCardDialog!!
    }

    private fun setCards(cards: List<StoredCard>) = this.adapter.populateCards(cards)

    private fun setUpRecyclerView() {
        this.adapter = PaymentMethodsAdapter(
            this.viewModel,
            object : DiffUtil.ItemCallback<Any>() {
                override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
                    return areCardsTheSame(oldItem as StoredCard, newItem as StoredCard)
                }

                override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
                    return areCardsTheSame(oldItem as StoredCard, newItem as StoredCard)
                }

                private fun areCardsTheSame(oldCard: StoredCard, newCard: StoredCard): Boolean {
                    return oldCard.id() == newCard.id()
                }
            }
        )
        binding.recyclerView.adapter = this.adapter
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
    }
}
