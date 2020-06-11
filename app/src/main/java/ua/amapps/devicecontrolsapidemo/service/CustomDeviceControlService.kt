package ua.amapps.devicecontrolsapidemo.service

import android.app.PendingIntent
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.service.controls.Control
import android.service.controls.ControlsProviderService
import android.service.controls.DeviceTypes
import android.service.controls.actions.BooleanAction
import android.service.controls.actions.ControlAction
import android.service.controls.actions.FloatAction
import android.service.controls.templates.*
import android.util.Log
import io.reactivex.Flowable
import io.reactivex.processors.ReplayProcessor
import org.reactivestreams.FlowAdapters
import ua.amapps.devicecontrolsapidemo.MainActivity
import java.util.concurrent.Flow
import java.util.function.Consumer

typealias DeviceControlFactory = () -> Control

class CustomDeviceControlService: ControlsProviderService() {

    private val updatePublisher by lazy { ReplayProcessor.create<Control>() }

    private val states = mutableMapOf(
        TOGGLE_BUTTON_CONTROL_ID to BooleanState(),
        DIMMABLE_BULB_CONTROL_ID to BooleanFloatState()
    )

    private val controlFactories: Map<String, DeviceControlFactory>
        get() = mapOf(
            SIMPLE_BUTTON_CONTROL_ID to this::createSimpleButton,
            TOGGLE_BUTTON_CONTROL_ID to this::createToggleButton,
            DIMMABLE_BULB_CONTROL_ID to this::createDimmableBulb
        )

    override fun createPublisherForAllAvailable(): Flow.Publisher<Control> {
        val controls = controlFactories.values.map { it() }
        return FlowAdapters.toFlowPublisher(Flowable.fromIterable(controls))
    }

    override fun createPublisherFor(controlIds: MutableList<String>): Flow.Publisher<Control> {
        controlFactories
            .asSequence()
            .filter { controlIds.contains(it.key)  }
            .map { it.value() }
            .forEach(updatePublisher::onNext)

        return FlowAdapters.toFlowPublisher(updatePublisher)
    }

    override fun performControlAction(controlId: String, action: ControlAction, consumer: Consumer<Int>) {
        Log.d("CustomDeviceControlService", "action: ${action::class.java.simpleName}")

        when(controlId) {
            TOGGLE_BUTTON_CONTROL_ID -> {
                if (action is BooleanAction) {
                    states[TOGGLE_BUTTON_CONTROL_ID] = BooleanState(action.newState)

                    updatePublisher.onNext(createToggleButton())
                    consumer.accept(ControlAction.RESPONSE_OK)
                } else {
                    consumer.accept(ControlAction.RESPONSE_FAIL)
                }
            }
            SIMPLE_BUTTON_CONTROL_ID -> {
                updatePublisher.onNext(createSimpleButton())
                consumer.accept(ControlAction.RESPONSE_OK)
            }
            DIMMABLE_BULB_CONTROL_ID -> {
                val currentState = states[controlId]!! as BooleanFloatState

                when (action) {
                    is FloatAction -> {
                        states[controlId] = currentState.copy(floatValue = action.newValue)
                        consumer.accept(ControlAction.RESPONSE_OK)
                    }
                    is BooleanAction -> {
                        states[controlId] = currentState.copy(booleanValue = action.newState)
                        consumer.accept(ControlAction.RESPONSE_OK)
                    }
                    else -> consumer.accept(ControlAction.RESPONSE_FAIL)
                }
            }

            else -> consumer.accept(ControlAction.RESPONSE_UNKNOWN)
        }
    }

    private fun createSimpleButton(): Control {
        val intent = PendingIntent.getActivity(
            baseContext,
            RC_CONTROL_REQUEST,
            Intent(baseContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        return Control.StatefulBuilder(SIMPLE_BUTTON_CONTROL_ID, intent)
            .setTitle("Button")
            .setSubtitle("Kitchen")
            .setStructure("Sample Home")
            .setZone("Kitchen")
            .setStatus(Control.STATUS_OK)
            .setControlTemplate(StatelessTemplate(SIMPLE_BUTTON_CONTROL_TEMPLATE_ID))
            .setDeviceType(DeviceTypes.TYPE_GENERIC_VIEWSTREAM)
            .build()
    }

    private fun createToggleButton(): Control {
        val intent = PendingIntent.getActivity(
            baseContext,
            RC_CONTROL_REQUEST,
            Intent(baseContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        val isOn = (states[TOGGLE_BUTTON_CONTROL_ID] as BooleanState).value

        val button = ControlButton(isOn, "Toggle")

        return Control.StatefulBuilder(TOGGLE_BUTTON_CONTROL_ID, intent)
            .setTitle("Bulb")
            .setSubtitle("Restroom")
            .setZone("Restroom")
            .setStructure("Sample Home")
            .setDeviceType(DeviceTypes.TYPE_LIGHT)
            .setStatus(Control.STATUS_OK)
            .setStatusText(if (isOn) "On" else "Off")
            .setControlTemplate(ToggleTemplate(TOGGLE_BUTTON_CONTROL_TEMPLATE_ID, button))
            .setCustomColor(ColorStateList.valueOf(0xff413C2D.toInt()))
            .build()
    }

    private fun createDimmableBulb(): Control {
        val intent = PendingIntent.getActivity(
            baseContext,
            RC_CONTROL_REQUEST,
            Intent(baseContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        val state = (states[DIMMABLE_BULB_CONTROL_ID] as BooleanFloatState)

        val rangeTemplate = RangeTemplate(
            DIMMABLE_BULB_CONTROL_TOGGLE_RANGE_TEMPLATE_ID,
            0f,
            100f,
            state.floatValue,
            10f,
            null
        )

        return Control.StatefulBuilder(DIMMABLE_BULB_CONTROL_ID, intent)
            .setTitle("Dimmable bulb")
            .setSubtitle("Hall")
            .setZone("Hall")
            .setStructure("Sample Home")
            .setDeviceType(DeviceTypes.TYPE_LIGHT)
            .setStatus(Control.STATUS_OK)
            .setStatusText("${state.floatValue.toInt()}%")
            .setControlTemplate(ToggleRangeTemplate(DIMMABLE_BULB_CONTROL_RANGE_TEMPLATE_ID, state.booleanValue, "On/Off", rangeTemplate))
            .setCustomColor(ColorStateList.valueOf(Color.parseColor("#303744")))
            .build()
    }

    companion object {
        private const val RC_CONTROL_REQUEST = 1002

        private const val SIMPLE_BUTTON_CONTROL_ID = "simple-button"
        private const val SIMPLE_BUTTON_CONTROL_TEMPLATE_ID = "simple-button-template"
        private const val TOGGLE_BUTTON_CONTROL_ID = "toggle-button"
        private const val TOGGLE_BUTTON_CONTROL_TEMPLATE_ID = "toggle-button-template"
        private const val DIMMABLE_BULB_CONTROL_ID = "dimmable-bulb"
        private const val DIMMABLE_BULB_CONTROL_RANGE_TEMPLATE_ID = "dimmable-bulb-range-template"
        private const val DIMMABLE_BULB_CONTROL_TOGGLE_RANGE_TEMPLATE_ID = "dimmable-bulb-toggle-range-template"
    }
}

sealed class ControlState

data class BooleanState(val value: Boolean = false): ControlState()

data class BooleanFloatState(val floatValue: Float = 0f, val booleanValue: Boolean = false): ControlState()

data class FloatState(val floatValue: Float = 0f): ControlState()