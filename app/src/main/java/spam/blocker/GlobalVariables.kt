package spam.blocker

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import spam.blocker.ui.history.CallViewModel
import spam.blocker.ui.history.SmsViewModel
import spam.blocker.ui.setting.TestingViewModel
import spam.blocker.ui.setting.bot.BotViewModel
import spam.blocker.ui.setting.regex.ContentRuleViewModel
import spam.blocker.ui.setting.regex.NumberRuleViewModel
import spam.blocker.ui.setting.regex.QuickCopyRuleViewModel
import spam.blocker.ui.widgets.BottomBarViewModel
import spam.blocker.util.PermissionChain

@Immutable
object G {
    val globallyEnabled : MutableState<Boolean> = mutableStateOf(false)

    val callVM : CallViewModel = CallViewModel()
    val smsVM : SmsViewModel = SmsViewModel()

    val NumberRuleVM : NumberRuleViewModel = NumberRuleViewModel()
    val ContentRuleVM : ContentRuleViewModel = ContentRuleViewModel()
    val QuickCopyRuleVM : QuickCopyRuleViewModel = QuickCopyRuleViewModel()
    val botVM : BotViewModel = BotViewModel()

    lateinit var bottomBarVM : BottomBarViewModel

    val testingVM : TestingViewModel = TestingViewModel()

    val permissionChain = PermissionChain()
}
