package com.soundist.feature.productivity

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.runtime.mutableStateOf
import com.soundist.core.designsystem.SoundistTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class ProductivityRouteTest {
    @get:Rule val compose = createComposeRule()

    @Test fun organizerAt390x844ExposesAllFiveDestinations() {
        val dependencies = dependencies(ProductivityState(workspacePage = WorkspacePage.ORGANIZER))
        compose.setContent { SoundistTheme { ProductivityRoute(dependencies, notesContent = {}) } }
        listOf("今天", "待办", "计划", "习惯", "倒计日").forEach { compose.onNodeWithText(it, useUnmergedTree = true).assertIsDisplayed() }
    }

    @Test fun runningFocusUsesImmersiveStateAndHidesWorkspaceNavigation() {
        val running = FocusSessionState(status = SessionStatus.RUNNING, startedAtEpochMillis = 1_000)
        val dependencies = dependencies(ProductivityState(workspacePage = WorkspacePage.FOCUS, focus = running))
        compose.setContent { SoundistTheme { ProductivityRoute(dependencies, notesContent = {}) } }
        compose.onNodeWithText("进行中").assertIsDisplayed()
        compose.onNodeWithText("事务", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("暂停").assertIsDisplayed()
    }

    @Test fun sleepClockConfigurationShowsEveryRequiredControl() {
        val dependencies = dependencies(ProductivityState(sleep = SleepSession(endMode = SleepEndMode.CLOCK)))
        compose.setContent { SoundistTheme { ProductivityRoute(dependencies, notesContent = {}, showSleepPanel = true) } }
        listOf("指定时间", "停止范围", "全部声音", "环境声", "电台", "结束前渐弱", "开始睡眠定时").forEach { compose.onNodeWithText(it, useUnmergedTree = true).assertIsDisplayed() }
    }

    @Test fun workspaceUsesExactlyFocusOrganizerAndHostedNotes() {
        val dependencies = dependencies(ProductivityState())
        compose.setContent { SoundistTheme { ProductivityRoute(dependencies, notesContent = { androidx.compose.material3.Text("宿主笔记库") }) } }
        listOf("专注", "事务", "笔记").forEach { compose.onNodeWithText(it, useUnmergedTree = true).assertIsDisplayed() }
        compose.onNodeWithText("睡眠", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun hostedNoteEditorHidesWorkspaceNavigationLikePrototype() {
        val dependencies = dependencies(ProductivityState(workspacePage = WorkspacePage.NOTES))
        compose.setContent {
            SoundistTheme {
                ProductivityRoute(
                    dependencies = dependencies,
                    notesContent = { androidx.compose.material3.Text("笔记编辑器正文") },
                    notesEditorActive = true,
                )
            }
        }
        compose.onNodeWithText("笔记编辑器正文").assertIsDisplayed()
        compose.onNodeWithText("专注", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("事务", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("笔记", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun globalSleepHostCanStayMountedWhileHiddenAndOpenFromAnyPage() {
        val dependencies = dependencies(ProductivityState(workspacePage = WorkspacePage.ORGANIZER))
        val visible = mutableStateOf(false)
        compose.setContent {
            SoundistTheme {
                ProductivityRoute(dependencies, notesContent = {})
                ProductivitySleepHost(dependencies, visible = visible.value, onDismiss = { visible.value = false })
            }
        }
        compose.onNodeWithText("睡眠定时", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { visible.value = true }
        compose.onNodeWithText("睡眠定时", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun dependencies(initial: ProductivityState): ProductivityDependencies {
        val repo = object : ProductivityRepository { override val state = MutableStateFlow(initial); override suspend fun update(transform: (ProductivityState) -> ProductivityState) { state.value = transform(state.value) } }
        return ProductivityDependencies(repo, object:ReminderScheduler{override suspend fun replace(ownerId:String,triggerAtEpochMillis:Long,title:String){};override suspend fun cancel(ownerId:String){}}, object:FocusSessionController{override suspend fun started(session:FocusSessionState){};override suspend fun paused(session:FocusSessionState){};override suspend fun stopped(){}}, object:FocusPersistenceStore{override suspend fun persist(event:FocusPersistenceEvent){}}, object:FocusSceneController{override suspend fun applyPreset(id:String){}}, object:SleepAudioController{override suspend fun snapshotVolumes()=true;override suspend fun beginFade(target:SleepTarget,durationMillis:Long){};override suspend fun stop(target:SleepTarget){};override suspend fun restoreSnapshot(){}}, object:HabitCheckStore{override suspend fun save(check:HabitCheck){};override suspend fun delete(checkId:String){}}, object:Clock{override fun now()=1_000L})
    }
}
