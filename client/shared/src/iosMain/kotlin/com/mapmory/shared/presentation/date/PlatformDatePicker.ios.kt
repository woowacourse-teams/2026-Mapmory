@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package com.mapmory.shared.presentation.date

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIApplication
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UIDatePicker
import platform.UIKit.UIDatePickerMode
import platform.UIKit.UIDatePickerStyle
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow

@Composable
actual fun PlatformDatePicker(
    visible: Boolean,
    initialDate: String?,
    minimumDate: String?,
    maximumDate: String?,
    onDateSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val presenter = remember { IosDatePickerPresenter() }
    var nativeUnavailable by remember { mutableStateOf(false) }
    presenter.onDateSelected = onDateSelected
    presenter.onDismiss = onDismiss

    LaunchedEffect(visible, initialDate, minimumDate, maximumDate) {
        if (visible) {
            nativeUnavailable = !presenter.present(initialDate, minimumDate, maximumDate)
        } else {
            nativeUnavailable = false
            presenter.dismiss()
        }
    }

    DisposableEffect(Unit) {
        onDispose { presenter.dismiss() }
    }

    if (nativeUnavailable) {
        MaterialDatePickerFallback(
            visible = true,
            initialDate = initialDate,
            minimumDate = minimumDate,
            maximumDate = maximumDate,
            onDateSelected = onDateSelected,
            onDismiss = onDismiss,
        )
    }
}

private class IosDatePickerPresenter {
    var onDateSelected: (String) -> Unit = {}
    var onDismiss: () -> Unit = {}

    private var controller: IosDatePickerViewController? = null

    fun present(
        initialDate: String?,
        minimumDate: String?,
        maximumDate: String?,
    ): Boolean {
        controller?.let { existingController ->
            existingController.updateDateBounds(
                minimumDate = minimumDate.toIosDate(),
                maximumDate = maximumDate.toIosDate(),
            )
            return true
        }

        val presenter = topViewControllerForDatePicker() ?: return false
        val pickerController = IosDatePickerViewController(
            initialDate = initialDate.toIosDate(),
            minimumDate = minimumDate.toIosDate(),
            maximumDate = maximumDate.toIosDate(),
            onDateSelected = { date -> onDateSelected(date) },
            onDismiss = {
                controller = null
                onDismiss()
            },
        )
        controller = pickerController
        presenter.presentViewController(pickerController, animated = true, completion = null)
        return true
    }

    fun dismiss() {
        controller?.let { activeController ->
            controller = null
            activeController.dismissSilently()
        }
    }
}

private class IosDatePickerViewController(
    initialDate: NSDate?,
    minimumDate: NSDate?,
    maximumDate: NSDate?,
    private val onDateSelected: (String) -> Unit,
    private val onDismiss: () -> Unit,
) : UIViewController(nibName = null, bundle = null) {
    private val datePicker = UIDatePicker()
    private val cancelButton = UIButton.buttonWithType(UIButtonTypeSystem)
    private val doneButton = UIButton.buttonWithType(UIButtonTypeSystem)
    private var didFinish = false

    init {
        datePicker.datePickerMode = UIDatePickerMode.UIDatePickerModeDate
        datePicker.preferredDatePickerStyle = UIDatePickerStyle.UIDatePickerStyleInline
        datePicker.locale = NSLocale(localeIdentifier = "ko_KR")
        datePicker.date = initialDate ?: NSDate()
        minimumDate?.let { datePicker.minimumDate = it }
        maximumDate?.let { datePicker.maximumDate = it }
    }

    override fun viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.whiteColor

        cancelButton.setTitle("취소", forState = UIControlStateNormal)
        cancelButton.addTarget(
            target = this,
            action = NSSelectorFromString("cancelPressed"),
            forControlEvents = UIControlEventTouchUpInside,
        )
        doneButton.setTitle("완료", forState = UIControlStateNormal)
        doneButton.addTarget(
            target = this,
            action = NSSelectorFromString("donePressed"),
            forControlEvents = UIControlEventTouchUpInside,
        )

        view.addSubview(cancelButton)
        view.addSubview(doneButton)
        view.addSubview(datePicker)
    }

    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        val width = view.bounds.useContents { size.width }
        cancelButton.setFrame(CGRectMake(20.0, 24.0, 80.0, 44.0))
        doneButton.setFrame(CGRectMake(width - 100.0, 24.0, 80.0, 44.0))
        datePicker.setFrame(CGRectMake(16.0, 80.0, width - 32.0, 360.0))
    }

    @ObjCAction
    fun cancelPressed() {
        finish(null)
    }

    @ObjCAction
    fun donePressed() {
        finish(datePicker.date)
    }

    fun dismissSilently() {
        didFinish = true
        dismissViewControllerAnimated(true, completion = null)
    }

    fun updateDateBounds(minimumDate: NSDate?, maximumDate: NSDate?) {
        datePicker.minimumDate = minimumDate
        datePicker.maximumDate = maximumDate
    }

    override fun viewDidDisappear(animated: Boolean) {
        super.viewDidDisappear(animated)
        if (!didFinish) {
            didFinish = true
            onDismiss()
        }
    }

    private fun finish(date: NSDate?) {
        if (didFinish) return
        didFinish = true
        date?.let { onDateSelected(it.toIosDateString()) }
        onDismiss()
        dismissViewControllerAnimated(true, completion = null)
    }
}

private fun String?.toIosDate(): NSDate? = this
    ?.trim()
    ?.takeIf(String::isNotBlank)
    ?.let { value -> iosDateFormatter().dateFromString(value) }

private fun NSDate.toIosDateString(): String = iosDateFormatter().stringFromDate(this)

private fun iosDateFormatter(): NSDateFormatter = NSDateFormatter().apply {
    locale = NSLocale(localeIdentifier = "en_US_POSIX")
    dateFormat = "yyyy-MM-dd"
}

private fun topViewControllerForDatePicker(): UIViewController? {
    val application = UIApplication.sharedApplication
    val window = application.keyWindow
        ?: application.windows.filterIsInstance<UIWindow>().firstOrNull { it.isKeyWindow() }
    var controller = window?.rootViewController
    while (controller?.presentedViewController != null) {
        controller = controller.presentedViewController
    }
    return controller
}
