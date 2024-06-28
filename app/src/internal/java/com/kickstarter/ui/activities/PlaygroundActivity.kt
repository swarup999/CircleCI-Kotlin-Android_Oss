package com.kickstarter.ui.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Pair
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import com.google.android.material.snackbar.Snackbar
import com.kickstarter.R
import com.kickstarter.databinding.PlaygroundLayoutBinding
import com.kickstarter.libs.RefTag
import com.kickstarter.libs.utils.extensions.addToDisposable
import com.kickstarter.libs.utils.extensions.getEnvironment
import com.kickstarter.libs.utils.extensions.getPaymentSheetConfiguration
import com.kickstarter.mock.factories.ProjectFactory
import com.kickstarter.models.CompleteOrderPayload
import com.kickstarter.models.Project
import com.kickstarter.ui.extensions.showSnackbar
import com.kickstarter.ui.extensions.snackbar
import com.kickstarter.viewmodels.PlaygroundViewModel
import com.kickstarter.viewmodels.PlaygroundViewModel.Factory
import com.stripe.android.paymentsheet.CreateIntentResult
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult
import com.stripe.android.paymentsheet.model.PaymentOption
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.rx2.asObservable
import timber.log.Timber

class PlaygroundActivity : ComponentActivity() {
    private lateinit var binding: PlaygroundLayoutBinding
    private lateinit var view: View
    private lateinit var viewModelFactory: Factory
    private var stripePaymentMethod: String = ""
    val viewModel: PlaygroundViewModel by viewModels { viewModelFactory }

    private lateinit var flowController: PaymentSheet.FlowController

    private val compositeDisposable = CompositeDisposable()

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = PlaygroundLayoutBinding.inflate(layoutInflater)
        view = binding.root
        setContentView(view)

        this.getEnvironment()?.let { env ->
            viewModelFactory = Factory(env)
        }

        viewModel.payloadUIState.asObservable()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                if (it.status.isNotBlank())
                    Toast.makeText(this, "complete_order status: ${it.status}", Toast.LENGTH_LONG).show()
            }
            .addToDisposable(compositeDisposable)


        flowController = createFlowController()
        configureFlowController()

        this.binding.newMethodButton.setOnClickListener {
            flowController.presentPaymentOptions()
        }

        this.binding.pledgeButton.setOnClickListener {
            flowController.confirm()
        }

    }

    private fun createFlowController() = PaymentSheet.FlowController.create(
        activity = this,
        paymentOptionCallback = ::onPaymentOption,
        createIntentCallback = { paymentMethod, _ ->
            // -  createIntentCallback is triggered with flowController.confirm()
            // -  Make a request to complete to create a PaymentIntent and return its client secret
            try {
                viewModel.completeOrder(paymentMethod.id ?: "")
                viewModel.payloadUIState.collect {
                    CreateIntentResult.Success(it.clientSecret)
                }
            } catch (e: Exception) {
                Toast.makeText(this, "error when calling complete_order", Toast.LENGTH_LONG).show()
                CreateIntentResult.Failure(
                    cause = e,
                    displayMessage = e.message
                )
            }
        },
        paymentResultCallback = ::onPaymentSheetResult,
    )

    private fun onPaymentOption(paymentOption: PaymentOption?) {
        paymentOption?.let {
            val toast = Toast.makeText(this, "new payment added: ${paymentOption.label}", Toast.LENGTH_LONG) // in Activity
            toast.show()
            Timber.d("paymentOption: $paymentOption" )
        }
    }


    private fun configureFlowController() {
        flowController.configureWithIntentConfiguration(
            intentConfiguration = PaymentSheet.IntentConfiguration(
                mode = PaymentSheet.IntentConfiguration.Mode.Payment(
                    amount = 1099,
                    currency = "usd",
                ),
            ),
            //onBehalfOf = "acct_1Ir6hZ4NJG33TWAg",
            configuration = this.getPaymentSheetConfiguration("arkariang@gmail.com"),
            callback = { success, error ->
            },
        )
    }

    fun onPaymentSheetResult(paymentSheetResult: PaymentSheetResult) {
        when(paymentSheetResult) {
            is PaymentSheetResult.Canceled -> {
                // Customer canceled - you should probably do nothing.
            }
            is PaymentSheetResult.Failed -> {
                print("Error: ${paymentSheetResult.error}")
                // PaymentSheet encountered an unrecoverable error. You can display the error to the user, log it, etc.
            }
            is PaymentSheetResult.Completed -> {
                // Display, for example, an order confirmation screen
                print("Completed")
            }
        }
    }

    /**
     * Set up the stepper example
     */
    private fun setStepper() {
        binding.stepper.inputs.setMinimum(1)
        binding.stepper.inputs.setMaximum(9)
        binding.stepper.inputs.setInitialValue(5)
        binding.stepper.inputs.setVariance(1)

        binding.stepper.outputs.display()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                showSnackbar(binding.stepper, "The updated value on the display is: $it")
            }.dispose()
    }

    private fun setStartActivity() {
        binding.startActivity.setOnClickListener { startProjectActivity(Pair(ProjectFactory.project(), RefTag.searchFeatured())) }
    }

    private fun startProjectActivity(projectAndRefTag: Pair<Project, RefTag>) {
        val intent = Intent(this, PaginationActivity::class.java)
        startActivity(intent)
        overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out_slide_out_left)
    }
}
