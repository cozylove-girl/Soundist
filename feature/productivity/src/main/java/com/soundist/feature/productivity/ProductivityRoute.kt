package com.soundist.feature.productivity

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soundist.core.designsystem.SoundistChip
import com.soundist.core.designsystem.SoundistColors
import com.soundist.core.designsystem.SoundistDimens
import com.soundist.core.designsystem.SoundistSelect
import com.soundist.core.designsystem.archive
import com.soundist.core.designsystem.bell
import com.soundist.core.designsystem.check
import com.soundist.core.designsystem.chevronDown
import com.soundist.core.designsystem.chevronUp
import com.soundist.core.designsystem.circle
import com.soundist.core.designsystem.circleCheck
import com.soundist.core.designsystem.listMusic
import com.soundist.core.designsystem.pause
import com.soundist.core.designsystem.pencilLine
import com.soundist.core.designsystem.play
import com.soundist.core.designsystem.plus
import com.soundist.core.designsystem.rotateCcw
import com.soundist.core.designsystem.slidersHorizontal
import com.soundist.core.designsystem.trash2
import com.soundist.core.designsystem.x
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

val LocalProductivityDependencies = staticCompositionLocalOf<ProductivityDependencies> {
    error("ProductivityDependencies must be provided by the application production graph")
}

/** Exact palette measured from the approved 390x844 web prototype. */
private object FocusPrototypeColors {
    val Canvas = Color(0xFF080B0D); val DeepSea = Color(0xFF0E1417); val Surface = Color(0xFF161E21); val SurfaceLow = Color(0xFF101719)
    val SurfaceHigh = Color(0xFF1E282B); val Text = Color(0xFFE9ECE9); val Secondary = Color(0xFFA9B3AF)
    val Muted = Color(0xFF929D99); val Ambient = Color(0xFF55B6A3); val AmbientLight = Color(0xFF91D3C5)
    val Radio = Color(0xFFC99662); val Success = Color(0xFF7FAE87); val Danger = Color(0xFFD57478); val Border = Color(0xFF314044)
}

private data class EditorRequest(val kind: TargetKind, val id: String? = null)

@Composable private fun TodayPage(s: ProductivityState, vm: ProductivityViewModel, edit: (EditorRequest) -> Unit) {
    val today = LocalDate.now(); val zone = ZoneId.systemDefault()
    val todos = s.todos.filter { !it.done && !it.archived && (it.dueAtEpochMillis == null || Instant.ofEpochMilli(it.dueAtEpochMillis).atZone(zone).toLocalDate() <= today) }
    val habits = s.habits.filter { !it.archived && (today.dayOfWeek.value % 7) in it.weekdays }
    val plans = s.plans.filter { !it.archived && it.doneMinutes < it.targetMinutes }
    val countdowns = s.countdowns.filter { !it.archived }.sortedBy { it.targetAtEpochMillis }.take(2)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(0.dp, 4.dp, 0.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Row(Modifier.fillMaxWidth().padding(bottom=12.dp),Arrangement.SpaceBetween,Alignment.Bottom){Column{Text("今天",fontSize=18.sp,fontWeight=FontWeight.SemiBold,color=FocusPrototypeColors.Text);Text("先看现在需要推进的事情",Modifier.padding(top=2.dp),fontSize=12.sp,color=FocusPrototypeColors.Muted)};TextButton({edit(EditorRequest(TargetKind.TODO))},Modifier.heightIn(min=44.dp)){Icon(plus,null,Modifier.size(14.dp));Spacer(Modifier.width(4.dp));Text("新建",fontSize=12.sp,color=FocusPrototypeColors.AmbientLight)}};HorizontalDivider(color=FocusPrototypeColors.Border) }
        val reminders = activeReminders(s)
        if (reminders.isNotEmpty()) item { Surface(color = FocusPrototypeColors.SurfaceLow, border = BorderStroke(1.dp, FocusPrototypeColors.Border), shape = MaterialTheme.shapes.medium) { Column(Modifier.padding(horizontal=12.dp,vertical=10.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(bell, null,Modifier.size(16.dp), tint = FocusPrototypeColors.Radio); Spacer(Modifier.width(8.dp)); Text("已设置的提醒",style=MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium,color=FocusPrototypeColors.Text); Spacer(Modifier.weight(1f)); Text("${reminders.size} 项",style=MaterialTheme.typography.labelSmall, color = FocusPrototypeColors.Muted) }; reminders.take(3).forEach { Row(Modifier.padding(top=6.dp),verticalAlignment=Alignment.CenterVertically){Surface(color=FocusPrototypeColors.SurfaceHigh,shape=MaterialTheme.shapes.small){Text(it.first,Modifier.padding(horizontal=6.dp,vertical=2.dp),style=MaterialTheme.typography.labelSmall,color=FocusPrototypeColors.Muted)};Spacer(Modifier.width(8.dp));Text(it.second,Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelSmall,color=FocusPrototypeColors.Secondary);Spacer(Modifier.width(8.dp));Text(reminderText(it.third),style=MaterialTheme.typography.labelSmall,color=Color(0xFFC9A46B))} } } } }
        if (todos.isNotEmpty()) item { EntitySection("到期与待办", { vm.organizer(OrganizerPage.TODOS) }) { Column { todos.take(3).forEachIndexed { index,todo -> TodayTodoRow(todo,vm,edit);if(index<todos.take(3).lastIndex)HorizontalDivider(color=FocusPrototypeColors.Border.copy(alpha=.7f)) } } } }
        if (habits.isNotEmpty()) item { EntitySection("今日习惯", { vm.organizer(OrganizerPage.HABITS) }, actionLabel = "管理") { habits.take(4).chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { HabitMini(it, vm, Modifier.weight(1f)) }; if(row.size == 1) Spacer(Modifier.weight(1f)) } } } }
        if (plans.isNotEmpty()) item { EntitySection("进行中的计划", { vm.organizer(OrganizerPage.PLANS) }) { plans.take(2).forEach { TodayPlanRow(it,vm) } } }
        if (countdowns.isNotEmpty()) item { EntitySection("临近节点") { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { countdowns.forEach { CountdownMini(it, vm, Modifier.weight(1f)) } } } }
        if (todos.isEmpty() && habits.isEmpty() && plans.isEmpty()) item { EmptyState("今天没有待推进的事项", "添加第一项") { edit(EditorRequest(TargetKind.TODO)) } }
    }
}

@Composable private fun TodosPage(s: ProductivityState, vm: ProductivityViewModel, edit: (EditorRequest) -> Unit) {
    var quick by remember { mutableStateOf("") }; var quickDue by remember { mutableStateOf(0) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(0.dp, 4.dp, 0.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(quick, { quick = it }, Modifier.weight(1f), placeholder = { Text("添加一次性待办...", color = FocusPrototypeColors.Muted) }, singleLine = true)
                IconButton({ if(quick.isNotBlank()) { val due = if(quickDue == 2) null else startOfDayAfter(quickDue); vm.saveTodo(Todo(text = quick.trim(), dueAtEpochMillis = due)); quick = "" } }) { Icon(plus, null, tint = SoundistColors.Teal) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf("今天", "明天", "无截止日").forEachIndexed { i, t -> SoundistChip(t, quickDue == i, { quickDue = i }) }
                Spacer(Modifier.weight(1f))
                TextButton({ edit(EditorRequest(TargetKind.TODO)) }, Modifier.heightIn(min = 40.dp)) { Text("详细设置", fontSize = 11.sp, color = FocusPrototypeColors.AmbientLight) }
            }
        }
        val groups = listOf(Triple(TodoKind.ONE_OFF, "一次性待办", "适合有明确结束点和 DDL 的事情"), Triple(TodoKind.LONG_TERM, "长期待办", "适合可以转成计划或习惯的事项"))
        groups.forEach { (kind,title,hint) -> item { Row(Modifier.fillMaxWidth().padding(horizontal=4.dp),verticalAlignment=Alignment.Bottom){Text(title,Modifier.weight(1f),style = MaterialTheme.typography.labelSmall, color = FocusPrototypeColors.Muted); Text(hint, style = MaterialTheme.typography.labelSmall, color = FocusPrototypeColors.Secondary)} }; items(s.todos.filter { it.kind == kind && !it.archived }.sortedBy { it.order }, key = { it.id }) { TodoRow(it, vm, edit) } }
    }
}

@Composable private fun PlansPage(s: ProductivityState, vm: ProductivityViewModel, edit: (EditorRequest) -> Unit) = EntityListPage("阶段计划", "有明确结束点与投入目标", { edit(EditorRequest(TargetKind.PLAN)) }) { items(s.plans.filterNot { it.archived }.sortedBy { it.order }, key = { it.id }) { PlanRow(it, vm, edit) } }
@Composable private fun HabitsPage(s: ProductivityState, vm: ProductivityViewModel, edit: (EditorRequest) -> Unit) = EntityListPage("长期习惯", "每天重复并独立累计", { edit(EditorRequest(TargetKind.HABIT)) }) { items(s.habits.filterNot { it.archived }.sortedBy { it.order }, key = { it.id }) { HabitRow(it, vm, edit) } }
@Composable private fun CountdownsPage(s: ProductivityState, vm: ProductivityViewModel, edit: (EditorRequest) -> Unit) = EntityListPage("重要倒计日", "长期节点与每日投入建议", { edit(EditorRequest(TargetKind.COUNTDOWN)) }) { items(s.countdowns.filterNot { it.archived }.sortedBy { it.order }, key = { it.id }) { CountdownRow(it, vm, edit) } }

@Composable private fun EntityListPage(title: String, subtitle: String, add: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) { LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(0.dp, 4.dp, 0.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { PageHeading(title, subtitle, add) }; content() } }

@Composable private fun TodoRow(todo: Todo, vm: ProductivityViewModel, edit: (EditorRequest) -> Unit) { Surface(color = FocusPrototypeColors.DeepSea, border = BorderStroke(1.dp, FocusPrototypeColors.Border.copy(alpha=.7f)), shape = MaterialTheme.shapes.large) { Column { Row(Modifier.padding(horizontal=8.dp,vertical=8.dp), verticalAlignment = Alignment.CenterVertically) { IconButton({ vm.toggleTodo(todo.id) }) { Icon(if(todo.done) circleCheck else circle, null, tint = if(todo.done) FocusPrototypeColors.Ambient else FocusPrototypeColors.Text.copy(alpha=.2f)) }; Column(Modifier.weight(1f)) { Text(todo.text,color=if(todo.done)FocusPrototypeColors.Muted else FocusPrototypeColors.Text,textDecoration = if(todo.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null); Text("DDL ${formatEpoch(todo.dueAtEpochMillis)}", style = MaterialTheme.typography.labelSmall, color = FocusPrototypeColors.Secondary) }; TextButton({ vm.bind(FocusTarget(TargetKind.TODO, todo.id, todo.text, "${if(todo.kind == TodoKind.ONE_OFF) "一次性待办" else "长期待办"} · ${formatEpoch(todo.dueAtEpochMillis)}"), todo.estimatedMinutes) }, Modifier.heightIn(min=44.dp).border(1.dp, FocusPrototypeColors.Ambient.copy(alpha=.18f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))) { Text("专注",fontSize=11.sp,color=FocusPrototypeColors.AmbientLight) }; IconButton({ edit(EditorRequest(TargetKind.TODO, todo.id)) }) { Icon(pencilLine, null, tint = FocusPrototypeColors.Secondary) } }; EntityActions(TargetKind.TODO,todo.id,vm) } } }
@Composable private fun TodayTodoRow(todo:Todo,vm:ProductivityViewModel,edit:(EditorRequest)->Unit){val overdue=todo.dueAtEpochMillis!=null&&todo.dueAtEpochMillis<startOfDayAfter(0);Row(Modifier.fillMaxWidth().heightIn(min=56.dp),verticalAlignment=Alignment.CenterVertically){IconButton({vm.toggleTodo(todo.id)},Modifier.size(44.dp)){Icon(if(todo.done)circleCheck else circle,null,Modifier.size(20.dp),tint=if(todo.done)FocusPrototypeColors.Ambient else FocusPrototypeColors.Muted)};Column(Modifier.weight(1f).clickable{edit(EditorRequest(TargetKind.TODO,todo.id))}){Text(todo.text,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium,color=FocusPrototypeColors.Text);Text(if(overdue)"已逾期 · 预计 ${todo.estimatedMinutes} 分钟" else "${formatEpoch(todo.dueAtEpochMillis)} · 预计 ${todo.estimatedMinutes} 分钟",Modifier.padding(top=2.dp),style=MaterialTheme.typography.labelSmall,color=if(overdue)FocusPrototypeColors.Danger else FocusPrototypeColors.Muted)};TextButton({vm.bind(FocusTarget(TargetKind.TODO,todo.id,todo.text,formatEpoch(todo.dueAtEpochMillis)),todo.estimatedMinutes)},Modifier.heightIn(min=40.dp)){Text("开始",style=MaterialTheme.typography.labelSmall,color=FocusPrototypeColors.AmbientLight)}}}
@Composable private fun TodayPlanRow(plan:FocusPlan,vm:ProductivityViewModel){val pct=(plan.doneMinutes/plan.targetMinutes.toFloat().coerceAtLeast(1f)).coerceIn(0f,1f);Surface(onClick={vm.organizer(OrganizerPage.PLANS)},color=FocusPrototypeColors.Surface,border=BorderStroke(1.dp,FocusPrototypeColors.Border),shape=MaterialTheme.shapes.medium,modifier=Modifier.padding(bottom=8.dp)){Column(Modifier.padding(12.dp)){Row{Text(plan.title,Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium,color=FocusPrototypeColors.Text);Text(plan.endDate?.toString() ?: "未设定",style=MaterialTheme.typography.labelSmall,color=FocusPrototypeColors.Secondary)};Spacer(Modifier.height(12.dp));LinearProgressIndicator({pct},Modifier.fillMaxWidth().height(6.dp),color=FocusPrototypeColors.Ambient,trackColor=FocusPrototypeColors.SurfaceHigh);Text("${plan.doneMinutes}/${plan.targetMinutes} 分钟 · ${plan.milestone.ifBlank{"继续推进下一阶段"}}",Modifier.padding(top=8.dp),style=MaterialTheme.typography.labelSmall,color=FocusPrototypeColors.Muted)}}}
@Composable private fun PlanRow(plan: FocusPlan, vm: ProductivityViewModel, edit: (EditorRequest) -> Unit) { val pct = plan.doneMinutes / plan.targetMinutes.toFloat().coerceAtLeast(1f); Surface(color = FocusPrototypeColors.Surface, border = BorderStroke(1.dp, FocusPrototypeColors.Border), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) { Column(Modifier.padding(16.dp)) { Row(Modifier.padding(bottom=12.dp),verticalAlignment=Alignment.Top) { Column(Modifier.weight(1f)) { Text(plan.title,fontSize=14.sp,fontWeight = FontWeight.Medium,color=FocusPrototypeColors.Text); Text("${if(plan.scope == PlanScope.TODAY) "今日计划" else "本周计划"} · DDL ${plan.endDate ?: "未设定"}",Modifier.padding(top=4.dp),fontSize=12.sp,color = FocusPrototypeColors.Secondary) };Row(horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically){IconButton({ edit(EditorRequest(TargetKind.PLAN, plan.id)) },Modifier.width(36.dp).height(44.dp)) { Icon(pencilLine,"编辑${plan.title}",Modifier.size(14.dp),tint=FocusPrototypeColors.Secondary) };IconButton({vm.deletePlan(plan.id)},Modifier.width(36.dp).height(44.dp)){Icon(trash2,"删除${plan.title}",Modifier.size(14.dp),tint=FocusPrototypeColors.Muted)};OutlinedButton({ vm.bind(FocusTarget(TargetKind.PLAN, plan.id, plan.title, "${plan.doneMinutes}/${plan.targetMinutes} 分钟 · ${plan.endDate ?: "未设定"}"), (plan.targetMinutes-plan.doneMinutes).coerceIn(25,90)) },Modifier.heightIn(min=44.dp),contentPadding=PaddingValues(horizontal=12.dp),shape=androidx.compose.foundation.shape.RoundedCornerShape(8.dp),border=BorderStroke(1.dp,FocusPrototypeColors.Ambient.copy(alpha=.25f)),colors=ButtonDefaults.outlinedButtonColors(containerColor=FocusPrototypeColors.Ambient.copy(alpha=.12f))) { Text("开始",fontSize=12.sp,color=FocusPrototypeColors.AmbientLight) } } }; LinearProgressIndicator({ pct.coerceIn(0f,1f) }, Modifier.fillMaxWidth().height(8.dp), color = FocusPrototypeColors.Ambient, trackColor = FocusPrototypeColors.SurfaceHigh);Row(Modifier.fillMaxWidth().padding(top=8.dp),Arrangement.SpaceBetween){Text("${plan.doneMinutes} / ${plan.targetMinutes} 分钟",fontSize=11.sp,color=FocusPrototypeColors.Secondary);Text("${(pct.coerceIn(0f,1f)*100).toInt()}%",fontSize=11.sp,color=FocusPrototypeColors.Secondary)} } } }
@Composable private fun HabitRow(habit: Habit, vm: ProductivityViewModel, edit: (EditorRequest) -> Unit) {
    val current = if (habit.metric == HabitMetric.MINUTES) habit.todayMinutes else habit.todayCount
    val target = if (habit.metric == HabitMetric.MINUTES) habit.targetMinutes else habit.targetCount
    val progress = (current / target.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
    val unit = if (habit.metric == HabitMetric.MINUTES) "分钟" else "次"
    Surface(color = FocusPrototypeColors.Surface, border = BorderStroke(1.dp, FocusPrototypeColors.Border), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.padding(bottom = 12.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(habit.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = FocusPrototypeColors.Text)
                    Text("每周 ${habit.weekdays.size} 天 · 连续 ${habit.streak} 天", Modifier.padding(top = 4.dp), fontSize = 12.sp, color = FocusPrototypeColors.Secondary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ edit(EditorRequest(TargetKind.HABIT, habit.id)) }, Modifier.width(36.dp).height(44.dp)) { Icon(pencilLine, "编辑${habit.title}", Modifier.size(14.dp), tint = FocusPrototypeColors.Secondary) }
                    IconButton({ vm.deleteHabit(habit.id) }, Modifier.width(36.dp).height(44.dp)) { Icon(trash2, "删除${habit.title}", Modifier.size(14.dp), tint = FocusPrototypeColors.Muted) }
                    when (habit.metric) {
                        HabitMetric.MINUTES -> OutlinedButton({ vm.bind(FocusTarget(TargetKind.HABIT, habit.id, habit.title, "今日 $current/$target $unit"), habit.targetMinutes) }, Modifier.heightIn(min = 44.dp), contentPadding = PaddingValues(horizontal = 12.dp)) { Text("计时", fontSize = 12.sp, color = FocusPrototypeColors.AmbientLight) }
                        HabitMetric.COUNT -> Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton({ vm.undoHabitCheck(habit.id) }, Modifier.size(40.dp), enabled = current > 0) { Text("−", color = if (current > 0) FocusPrototypeColors.Secondary else FocusPrototypeColors.Muted) }
                            FilledTonalButton({ vm.checkHabit(habit.id) }, Modifier.heightIn(min = 40.dp), contentPadding = PaddingValues(horizontal = 12.dp)) { Text("+1", color = FocusPrototypeColors.AmbientLight) }
                        }
                        HabitMetric.CHECK -> FilledTonalButton({ if (current > 0) vm.undoHabitCheck(habit.id) else vm.checkHabit(habit.id) }, Modifier.heightIn(min = 40.dp), contentPadding = PaddingValues(horizontal = 12.dp)) { Icon(if (current > 0) circleCheck else check, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(if (current > 0) "已打卡" else "打卡", fontSize = 12.sp) }
                    }
                }
            }
            LinearProgressIndicator({ progress }, Modifier.fillMaxWidth().height(8.dp), color = FocusPrototypeColors.Success, trackColor = FocusPrototypeColors.SurfaceHigh)
            Text("今日 $current / $target $unit", Modifier.padding(top = 8.dp), fontSize = 11.sp, color = FocusPrototypeColors.Secondary)
        }
    }
}
@Composable private fun CountdownRow(event: CountdownEvent, vm: ProductivityViewModel, edit: (EditorRequest) -> Unit) { val days = daysUntil(event.targetAtEpochMillis); Surface(color = FocusPrototypeColors.Surface, border = BorderStroke(1.dp, FocusPrototypeColors.Border), shape = MaterialTheme.shapes.large) { Column(Modifier.padding(16.dp)) { Row { Column(Modifier.weight(1f)) { Text(event.title, fontWeight = FontWeight.Medium,color=FocusPrototypeColors.Text); Text("${event.note} · 每日建议 ${event.dailyMinutes} 分钟", style = MaterialTheme.typography.labelSmall, color = FocusPrototypeColors.Secondary) }; Column(horizontalAlignment = Alignment.End) { Text(if(days<0) "已到期" else if(days==0L) "今天" else "$days", fontSize=24.sp,fontWeight=FontWeight.SemiBold, color = FocusPrototypeColors.Ambient,fontFamily=FontFamily.Monospace); if(days>0) Text("天", style = MaterialTheme.typography.labelSmall,color=FocusPrototypeColors.Secondary); IconButton({ edit(EditorRequest(TargetKind.COUNTDOWN, event.id)) }) { Icon(pencilLine, null,tint=FocusPrototypeColors.Secondary) } } }; OutlinedButton({ vm.bind(FocusTarget(TargetKind.COUNTDOWN, event.id, event.title, "距离目标 $days 天 · ${event.note}"), event.dailyMinutes) }, Modifier.fillMaxWidth(),border=BorderStroke(1.dp,FocusPrototypeColors.Ambient.copy(alpha=.2f)),colors=ButtonDefaults.outlinedButtonColors(containerColor=FocusPrototypeColors.Ambient.copy(alpha=.08f))) { Text("为这个目标开始一次专注",fontSize=12.sp,fontWeight=FontWeight.SemiBold,color=FocusPrototypeColors.AmbientLight) };EntityActions(TargetKind.COUNTDOWN,event.id,vm) } } }
@Composable private fun HabitMini(habit: Habit, vm: ProductivityViewModel, modifier: Modifier) {
    val current = if (habit.metric == HabitMetric.MINUTES) habit.todayMinutes else habit.todayCount
    val target = if (habit.metric == HabitMetric.MINUTES) habit.targetMinutes else habit.targetCount
    val pct = (current / target.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
    Surface(onClick = { if (habit.metric == HabitMetric.MINUTES) vm.bind(FocusTarget(TargetKind.HABIT, habit.id, habit.title, "今日 $current/$target 分钟"), habit.targetMinutes) else if (habit.metric == HabitMetric.CHECK && current > 0) vm.undoHabitCheck(habit.id) else vm.checkHabit(habit.id) }, modifier = modifier, color = FocusPrototypeColors.Surface, border = BorderStroke(1.dp, FocusPrototypeColors.Border), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(12.dp)) {
            Row { Text(habit.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, color = FocusPrototypeColors.Text); Text(if (habit.metric == HabitMetric.CHECK && current > 0) "已完成" else "${(pct * 100).toInt()}%", color = FocusPrototypeColors.AmbientLight) }
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator({ pct }, Modifier.fillMaxWidth().height(6.dp), color = FocusPrototypeColors.Success, trackColor = FocusPrototypeColors.SurfaceHigh)
            Text("连续 ${habit.streak} 天", style = MaterialTheme.typography.labelSmall, color = FocusPrototypeColors.Muted)
        }
    }
}
@Composable private fun CountdownMini(event: CountdownEvent, vm: ProductivityViewModel, modifier: Modifier) { val days=daysUntil(event.targetAtEpochMillis); Surface(onClick={vm.organizer(OrganizerPage.COUNTDOWNS)},modifier=modifier,color=FocusPrototypeColors.Surface,border=BorderStroke(1.dp,FocusPrototypeColors.Border),shape=MaterialTheme.shapes.medium) { Column(Modifier.padding(12.dp)) { Text(event.title,maxLines=1,overflow=TextOverflow.Ellipsis,color=FocusPrototypeColors.Text); Text(if(days<0)"已到期" else if(days==0L)"今天" else "D-$days",fontSize=24.sp,color=FocusPrototypeColors.Radio,fontFamily=FontFamily.Monospace); Text(formatEpoch(event.targetAtEpochMillis),style=MaterialTheme.typography.labelSmall,color=FocusPrototypeColors.Muted) } } }

@Composable fun ProductivityRoute(
    dependencies: ProductivityDependencies = LocalProductivityDependencies.current,
    notesContent: @Composable () -> Unit = LocalFocusNotesContent.current,
    showSleepPanel: Boolean = false,
    onDismissSleepPanel: () -> Unit = {},
    /** True while the hosted notes feature is showing its full editor. */
    notesEditorActive: Boolean = false,
) {
    val vm = sharedProductivityViewModel(dependencies)
    val state by vm.state.collectAsState()
    ProductivityTimerEffects(state, vm)
    Column(Modifier.fillMaxSize().background(FocusPrototypeColors.Canvas).padding(horizontal=16.dp)) {
        val hideWorkspaceTabs = state.workspacePage == WorkspacePage.NOTES && notesEditorActive
        if (!hideWorkspaceTabs) Row(Modifier.fillMaxWidth().height(48.dp)) {
            listOf(WorkspacePage.FOCUS to "专注", WorkspacePage.ORGANIZER to "事务", WorkspacePage.NOTES to "笔记").forEach { (page, label) ->
                Box(Modifier.weight(1f).fillMaxHeight().clickable{vm.navigate(page)},contentAlignment=Alignment.Center){Text(label,color=if(state.workspacePage==page)FocusPrototypeColors.Text else FocusPrototypeColors.Muted,fontWeight=FontWeight.SemiBold,fontSize=14.sp);if(state.workspacePage==page)Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth(.58f).height(2.dp).background(FocusPrototypeColors.Ambient,MaterialTheme.shapes.small))}
            }
        }
        HorizontalDivider(color = FocusPrototypeColors.Border)
        when (state.workspacePage) { WorkspacePage.FOCUS -> FocusPage(state, vm); WorkspacePage.ORGANIZER -> OrganizerPage(state, vm); WorkspacePage.NOTES -> Box(Modifier.fillMaxSize()) { notesContent() } }
    }
    if (showSleepPanel) SleepTimerPanel(state, vm, onDismissSleepPanel)
}

/**
 * Global sleep entry point for the application header.
 *
 * Keep this host composed at the application shell (including when [visible] is false) so a
 * restored absolute-time sleep session continues to reconcile while the user visits Home,
 * Sounds, Radio, or Records. [ProductivityRoute] and this host deliberately request the same
 * keyed ViewModel from the same host ViewModelStoreOwner, preventing a second timer state.
 */
@Composable fun ProductivitySleepHost(
    dependencies: ProductivityDependencies = LocalProductivityDependencies.current,
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    val vm = sharedProductivityViewModel(dependencies)
    val state by vm.state.collectAsState()
    ProductivityTimerEffects(state, vm)
    if (visible) SleepTimerPanel(state, vm, onDismiss)
}

/** Header-level focus clock shared across every bottom destination. */
@Composable
fun rememberGlobalFocusTimerText(
    dependencies: ProductivityDependencies = LocalProductivityDependencies.current,
): String? {
    val vm = sharedProductivityViewModel(dependencies)
    val state by vm.state.collectAsState()
    val focus = state.focus
    val now by tickingNow(focus.status == SessionStatus.RUNNING, vm.clock)
    if (focus.status == SessionStatus.IDLE || focus.status == SessionStatus.REVIEW) return null
    return formatMillis(FocusStateMachine.displayMillis(focus, now))
}

private const val PRODUCTIVITY_VIEW_MODEL_KEY = "soundist.productivity.shared"

@Composable
private fun sharedProductivityViewModel(dependencies: ProductivityDependencies): ProductivityViewModel =
    LocalContext.current.let { context ->
        val fallbackOwner = checkNotNull(LocalViewModelStoreOwner.current) {
            "Productivity UI requires a ViewModelStoreOwner"
        }
        val appOwner = remember(context, fallbackOwner) {
            context.findViewModelStoreOwner() ?: fallbackOwner
        }
        viewModel(
            viewModelStoreOwner = appOwner,
            key = PRODUCTIVITY_VIEW_MODEL_KEY,
            factory = remember(dependencies) { ProductivityViewModelFactory(dependencies) },
        )
    }

private tailrec fun Context.findViewModelStoreOwner(): ViewModelStoreOwner? = when (this) {
    is ViewModelStoreOwner -> this
    is ContextWrapper -> baseContext.findViewModelStoreOwner()
    else -> null
}

@Composable
private fun ProductivityTimerEffects(state: ProductivityState, vm: ProductivityViewModel) {
    LaunchedEffect(state.focus.status, state.sleep.status) {
        vm.ensureTimerReconciliation()
    }
}

/** Host-owned notes UI rendered inside the focus workspace, never as a fifth bottom destination. */
val LocalFocusNotesContent = staticCompositionLocalOf<@Composable () -> Unit> {
    { error("Focus notes content must be provided by the application host") }
}

/** App.tsx `saveQuickNote`; the notes host owns the resulting note and persistence. */
val LocalFocusQuickNoteWriter = staticCompositionLocalOf<(String) -> Unit> {
    { error("Focus quick-note writer must be provided by the application host") }
}

private class ProductivityViewModelFactory(private val dependencies: ProductivityDependencies) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = ProductivityViewModel(dependencies) as T
}

@Composable private fun FocusPage(s: ProductivityState, vm: ProductivityViewModel) {
    val f = s.focus; val now by tickingNow(f.status == SessionStatus.RUNNING, vm.clock)
    var settingsExpanded by remember { mutableStateOf(false) }
    var quickNote by remember { mutableStateOf("") }
    val writeQuickNote = LocalFocusQuickNoteWriter.current
    if (f.status == SessionStatus.REVIEW) FocusReviewSheet(f, vm)
    val active = f.status != SessionStatus.IDLE
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(0.dp, 16.dp, 0.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            FocusTargetHeader(f,active,vm)
        }
        item {
            Surface(color = FocusPrototypeColors.Surface, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), border = BorderStroke(1.dp, FocusPrototypeColors.Border)) {
                Column(Modifier.padding(16.dp),horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth().padding(bottom=12.dp),Arrangement.SpaceBetween,Alignment.CenterVertically){
                        Column(Modifier.weight(1f)){
                            Text(if(active)"${if(f.timerMode==TimerMode.COUNTDOWN)"倒计时" else "正计时"} · ${if(f.phase==SessionPhase.FOCUS)"专注" else "休息"}" else if(f.timerMode==TimerMode.COUNTDOWN)"${f.focusMinutes} 分钟倒计时" else "自由正计时",style=MaterialTheme.typography.bodyMedium,color=FocusPrototypeColors.Text)
                            Text(if(active&&f.timerMode==TimerMode.STOPWATCH)"自由记录本次时长" else if(active)"${if(f.phase==SessionPhase.FOCUS)f.focusMinutes else f.breakMinutes} 分钟" else "第 ${f.cycle.round}/${f.cycle.rounds} 轮 · 休息 ${f.breakMinutes} 分钟",Modifier.padding(top=2.dp),style=MaterialTheme.typography.labelSmall,color=FocusPrototypeColors.Muted)
                        }
                        if(!active) TextButton({settingsExpanded=!settingsExpanded},Modifier.heightIn(min=44.dp)){Icon(slidersHorizontal,null,Modifier.size(15.dp),tint=FocusPrototypeColors.AmbientLight);Spacer(Modifier.width(6.dp));Text(if(settingsExpanded)"收起" else "计时设置",color=FocusPrototypeColors.AmbientLight,style=MaterialTheme.typography.labelSmall)}
                    }
                    HorizontalDivider(color=FocusPrototypeColors.Border.copy(alpha=.7f))
                    if(!active&&settingsExpanded) TimerSettingsContent(f,vm)
                    TimerDial(f,now)
                    FocusTimerControls(f,active,vm)
                }
            }
        }
        if(active)item{
            Column(Modifier.fillMaxWidth().padding(horizontal=4.dp,vertical=12.dp)){
                Text("快速记录",fontSize=11.sp,color=FocusPrototypeColors.Muted)
                Row(Modifier.padding(top=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    OutlinedTextField(quickNote,{quickNote=it},Modifier.weight(1f).heightIn(min=44.dp),placeholder={Text("此刻的想法...",fontSize=14.sp,color=FocusPrototypeColors.Muted)},singleLine=true,shape=androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    Surface(onClick={if(quickNote.isBlank()){}else{writeQuickNote(quickNote.trim());quickNote=""}},modifier=Modifier.size(44.dp),color=FocusPrototypeColors.Ambient.copy(alpha=.12f),border=BorderStroke(1.dp,FocusPrototypeColors.Ambient.copy(alpha=.22f)),shape=androidx.compose.foundation.shape.RoundedCornerShape(8.dp)){Box(contentAlignment=Alignment.Center){Icon(pencilLine,"保存快速记录",Modifier.size(16.dp),tint=FocusPrototypeColors.Ambient.copy(alpha=.7f))}}
                }
            }
            HorizontalDivider(color=FocusPrototypeColors.Border)
        }
    }
}

@Composable private fun FocusTargetHeader(f:FocusSessionState,active:Boolean,vm:ProductivityViewModel){
    val content:@Composable ()->Unit={Column(Modifier.padding(if(active)PaddingValues(vertical=12.dp) else PaddingValues(16.dp))){
        Text("当前目标",fontSize=11.sp,color=FocusPrototypeColors.Muted)
        Row(Modifier.fillMaxWidth(),Arrangement.spacedBy(12.dp),Alignment.Top){Column(Modifier.weight(1f)){Text(f.target.name,maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=18.sp,fontWeight=FontWeight.SemiBold,fontFamily=FontFamily.Serif,color=FocusPrototypeColors.Text);Text(f.target.meta.ifBlank{"可绑定待办、计划、习惯或倒计日，也可以自由专注。"},Modifier.padding(top=4.dp),fontSize=12.sp,color=FocusPrototypeColors.Secondary);if(!active)TextButton({if(f.target.kind==TargetKind.FREE){vm.navigate(WorkspacePage.ORGANIZER);vm.organizer(OrganizerPage.TODOS)}else vm.unbind()},Modifier.heightIn(min=44.dp),contentPadding=PaddingValues(0.dp)){Text(if(f.target.kind==TargetKind.FREE)"从事务中选择目标" else "取消绑定，改为自由专注",fontSize=12.sp,color=FocusPrototypeColors.AmbientLight)}};StatusBadge(when(f.status){SessionStatus.IDLE->"准备开始";SessionStatus.RUNNING->"进行中";SessionStatus.PAUSED->"已暂停";SessionStatus.REVIEW->"待复盘"})}
    }}
    if(active){HorizontalDivider(color=FocusPrototypeColors.Border);content();HorizontalDivider(color=FocusPrototypeColors.Border)}else Surface(color=FocusPrototypeColors.Surface,shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp),border=BorderStroke(1.dp,FocusPrototypeColors.Border)){content()}
}

@Composable private fun TimerSettingsContent(f: FocusSessionState, vm: ProductivityViewModel) {
    Column(Modifier.fillMaxWidth().padding(top=12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TimerMode.entries.forEach { SoundistChip(if(it == TimerMode.COUNTDOWN) "倒计时" else "正计时", f.timerMode == it, { vm.configureTimer(mode = it) }, Modifier.weight(1f)) } }
            Spacer(Modifier.height(10.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SessionPhase.entries.forEach { SoundistChip(if(it == SessionPhase.FOCUS) "专注" else "休息", f.phase == it, { vm.configureTimer(phase = it) }, Modifier.weight(1f)) } }
            Spacer(Modifier.height(10.dp)); Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { NumericSetting("专注分钟", f.focusMinutes, 1..240, Modifier.weight(1f)) { vm.configureTimer(focusMinutes = it) }; NumericSetting("休息分钟", f.breakMinutes, 1..120, Modifier.weight(1f)) { vm.configureTimer(breakMinutes = it) } }
            Spacer(Modifier.height(10.dp)); Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { NumericSetting("每组轮数", f.cycle.rounds, 1..8, Modifier.weight(1f)) { vm.configureTimer(cycle = f.cycle.copy(rounds = it)) }; NumericSetting("长休息分钟", f.cycle.longBreakMinutes, 5..60, Modifier.weight(1f)) { vm.configureTimer(cycle = f.cycle.copy(longBreakMinutes = it)) } }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleChip("自动开始休息", f.cycle.autoBreak, Modifier.weight(1f)) { vm.configureTimer(cycle = f.cycle.copy(autoBreak = !f.cycle.autoBreak)) }
                ToggleChip("休息后自动专注", f.cycle.autoFocus, Modifier.weight(1f)) { vm.configureTimer(cycle = f.cycle.copy(autoFocus = !f.cycle.autoFocus)) }
            }
    }
}

@Composable private fun TimerDial(f:FocusSessionState,now:Long){
    val configured=FocusStateMachine.configuredMillis(f).coerceAtLeast(60_000L)
    val elapsed=FocusStateMachine.elapsedMillis(f,now)
    val progress=when{
        f.status==SessionStatus.IDLE->0f
        f.timerMode==TimerMode.STOPWATCH->((elapsed%configured).toFloat()/configured).coerceIn(0f,1f)
        else->(elapsed.toFloat()/configured).coerceIn(0f,1f)
    }
    Box(Modifier.padding(vertical=12.dp).size(208.dp),contentAlignment=Alignment.Center){
        if(f.status==SessionStatus.RUNNING){
            val infinite=rememberInfiniteTransition(label="pulseGlow")
            val pulse by infinite.animateFloat(.35f,.75f,infiniteRepeatable(tween(1000),RepeatMode.Reverse),label="pulse")
            val glowScale by infinite.animateFloat(1f,1.1f,infiniteRepeatable(tween(1000),RepeatMode.Reverse),label="glowScale")
            Canvas(Modifier.fillMaxSize()){
                drawCircle(Brush.radialGradient(listOf(FocusPrototypeColors.Ambient.copy(alpha=.10f*pulse),Color.Transparent),center=center,radius=size.minDimension/2f),radius=size.minDimension/2f*glowScale)
            }
        }
        Canvas(Modifier.fillMaxSize()){
            val stroke=size.minDimension*.025f
            val radius=size.minDimension*.44f
            drawCircle(FocusPrototypeColors.Ambient.copy(alpha=.08f),radius=radius,style=Stroke(stroke))
            if(progress>0f)drawArc(FocusPrototypeColors.Ambient,-90f,360f*progress,false,topLeft=androidx.compose.ui.geometry.Offset(center.x-radius,center.y-radius),size=androidx.compose.ui.geometry.Size(radius*2,radius*2),style=Stroke(stroke,cap=StrokeCap.Round))
        }
        Column(horizontalAlignment=Alignment.CenterHorizontally){
            Text(formatMillis(FocusStateMachine.displayMillis(f,now)),style=MaterialTheme.typography.displaySmall.copy(fontFamily=FontFamily.Monospace,fontSize=36.sp,fontWeight=FontWeight.Light),color=FocusPrototypeColors.Text)
            Text("${if(f.timerMode==TimerMode.COUNTDOWN)"倒计时" else "正计时"} · ${when(f.status){SessionStatus.IDLE->"准备开始";SessionStatus.RUNNING->"进行中";SessionStatus.PAUSED->"已暂停";SessionStatus.REVIEW->"待复盘"}}",Modifier.padding(top=4.dp),style=MaterialTheme.typography.labelSmall,color=FocusPrototypeColors.Secondary)
        }
    }
}

@Composable private fun FocusTimerControls(f:FocusSessionState,active:Boolean,vm:ProductivityViewModel){
    Row(Modifier.fillMaxWidth().padding(top=20.dp),horizontalArrangement=Arrangement.spacedBy(20.dp,Alignment.CenterHorizontally),verticalAlignment=Alignment.CenterVertically){
        FilledIconButton(
            onClick={if(active)vm.finishTimer() else vm.resetTimer()},
            modifier=Modifier.size(44.dp).background(FocusPrototypeColors.SurfaceHigh,androidx.compose.foundation.shape.CircleShape),
            colors=IconButtonDefaults.filledIconButtonColors(containerColor=FocusPrototypeColors.SurfaceHigh,contentColor=FocusPrototypeColors.Secondary),
        ){Icon(if(active)x else rotateCcw,if(active)"结束本次计时" else "重置计时",Modifier.size(18.dp))}
        FilledIconButton(
            onClick=vm::toggleTimer,
            modifier=Modifier.size(64.dp).background(if(f.status==SessionStatus.RUNNING)FocusPrototypeColors.Ambient.copy(alpha=.15f) else FocusPrototypeColors.Ambient,androidx.compose.foundation.shape.CircleShape),
            colors=IconButtonDefaults.filledIconButtonColors(containerColor=if(f.status==SessionStatus.RUNNING)FocusPrototypeColors.Ambient.copy(alpha=.15f) else FocusPrototypeColors.Ambient,contentColor=if(f.status==SessionStatus.RUNNING)FocusPrototypeColors.Ambient else FocusPrototypeColors.Canvas),
        ){Icon(if(f.status==SessionStatus.RUNNING)pause else play,if(f.status==SessionStatus.RUNNING)"暂停计时" else "开始计时",Modifier.size(26.dp))}
        if(!active) FilledIconButton(
            onClick={vm.navigate(WorkspacePage.ORGANIZER)},
            modifier=Modifier.size(44.dp).background(FocusPrototypeColors.SurfaceHigh,androidx.compose.foundation.shape.CircleShape),
            colors=IconButtonDefaults.filledIconButtonColors(containerColor=FocusPrototypeColors.SurfaceHigh,contentColor=FocusPrototypeColors.Secondary),
        ){Icon(listMusic,"选择专注目标",Modifier.size(18.dp))}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun FocusReviewSheet(f: FocusSessionState, vm: ProductivityViewModel) {
    var note by remember { mutableStateOf("") }; var completeTodo by remember { mutableStateOf(f.target.kind == TargetKind.TODO) }
    ModalBottomSheet(
        onDismissRequest = { vm.resetTimer() },
        containerColor = FocusPrototypeColors.Surface,
        contentColor = FocusPrototypeColors.Text,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        scrimColor = Color.Black.copy(alpha = .65f),
        dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 28.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Top) {
                Column { Text("本次专注", fontSize = 11.sp, letterSpacing = 1.8.sp, color = FocusPrototypeColors.Muted); Text("${f.completionMinutes} 分钟 · ${f.target.name}", Modifier.padding(top = 4.dp), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = FocusPrototypeColors.Text) }
                IconButton({ vm.resetTimer() }, Modifier.size(44.dp)) { Icon(x, "关闭复盘", Modifier.size(16.dp), tint = FocusPrototypeColors.Muted) }
            }
            Text("复盘记录（可选）", Modifier.padding(top = 12.dp), fontSize = 11.sp, color = FocusPrototypeColors.Secondary)
            OutlinedTextField(note, { note = it }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), minLines = 4, placeholder = { Text("完成了什么，下一次从哪里继续...", color = FocusPrototypeColors.Muted) })
            if (f.target.kind == TargetKind.TODO) Row(Modifier.fillMaxWidth().clickable { completeTodo = !completeTodo }.padding(top = 12.dp).border(1.dp, FocusPrototypeColors.Border, MaterialTheme.shapes.medium).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(20.dp).border(1.dp, if (completeTodo) FocusPrototypeColors.Ambient else FocusPrototypeColors.Border, androidx.compose.foundation.shape.RoundedCornerShape(4.dp)).background(if (completeTodo) FocusPrototypeColors.Ambient else Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) { if (completeTodo) Icon(check, null, Modifier.size(12.dp), tint = FocusPrototypeColors.Canvas) }
                Column(Modifier.padding(start = 12.dp)) { Text("同时完成绑定待办", fontSize = 12.sp, color = FocusPrototypeColors.Text); Text("专注记录仍会独立保存", Modifier.padding(top = 2.dp), fontSize = 10.sp, color = FocusPrototypeColors.Muted) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ vm.saveReview(note, CompletionStatus.INTERRUPTED, false) }, Modifier.weight(1f).height(48.dp)) { Text("保存为中断", color = FocusPrototypeColors.Secondary) }
                Button({ vm.saveReview(note, CompletionStatus.COMPLETED, completeTodo) }, Modifier.weight(1f).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = FocusPrototypeColors.Ambient, contentColor = FocusPrototypeColors.Canvas)) { Text("完成并保存", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable private fun OrganizerPage(s: ProductivityState, vm: ProductivityViewModel) {
    var editor by remember { mutableStateOf<EditorRequest?>(null) }
    if (s.archiveOpen) ArchiveDialog(s, vm)
    editor?.let { EditEntityDialog(it, s, vm) { editor = null } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(top=16.dp).height(50.dp).background(FocusPrototypeColors.Surface,MaterialTheme.shapes.large).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) { OrganizerPage.entries.forEach { page -> Box(Modifier.weight(1f).fillMaxHeight().background(if(s.organizerPage==page)FocusPrototypeColors.SurfaceHigh else Color.Transparent,MaterialTheme.shapes.medium).clickable{vm.organizer(page)},contentAlignment=Alignment.Center){Text(page.label,style=MaterialTheme.typography.labelSmall,color=if(s.organizerPage==page)FocusPrototypeColors.Text else FocusPrototypeColors.Muted)} } }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), Arrangement.End) { val archivedCount = s.todos.count{it.archived}+s.plans.count{it.archived}+s.habits.count{it.archived}+s.countdowns.count{it.archived}; TextButton({ vm.archiveManager(true) }) { Icon(archive, null, Modifier.size(14.dp), tint = FocusPrototypeColors.Muted); Spacer(Modifier.width(5.dp)); Text(if(archivedCount>0)"归档管理 · $archivedCount" else "归档管理",fontSize=11.sp,color=FocusPrototypeColors.Muted) } }
        when(s.organizerPage) {
            OrganizerPage.TODAY -> TodayPage(s, vm) { editor = it }
            OrganizerPage.TODOS -> TodosPage(s, vm) { editor = it }
            OrganizerPage.PLANS -> PlansPage(s, vm) { editor = it }
            OrganizerPage.HABITS -> HabitsPage(s, vm) { editor = it }
            OrganizerPage.COUNTDOWNS -> CountdownsPage(s, vm) { editor = it }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun EditEntityDialog(request: EditorRequest, s: ProductivityState, vm: ProductivityViewModel, close: () -> Unit) {
    val oldTodo=s.todos.find{it.id==request.id};val oldPlan=s.plans.find{it.id==request.id};val oldHabit=s.habits.find{it.id==request.id};val oldCountdown=s.countdowns.find{it.id==request.id}
    val currentTitle = oldTodo?.text?:oldPlan?.title?:oldHabit?.title?:oldCountdown?.title.orEmpty()
    var title by remember(request) { mutableStateOf(currentTitle) }; var minutes by remember { mutableStateOf((oldTodo?.estimatedMinutes?:oldPlan?.targetMinutes?:oldHabit?.targetMinutes?:oldCountdown?.dailyMinutes?:if(request.kind==TargetKind.COUNTDOWN)60 else if(request.kind==TargetKind.TODO)25 else 30).toString()) }; var note by remember { mutableStateOf(oldTodo?.note?:oldPlan?.description?:oldCountdown?.note.orEmpty()) }; var more by remember { mutableStateOf(request.id!=null) }; var reminder by remember { mutableStateOf(oldTodo?.reminder?:oldPlan?.reminder?:oldHabit?.reminder?:oldCountdown?.reminder?:ReminderOffset.NONE) }; var longTerm by remember { mutableStateOf(oldTodo?.kind==TodoKind.LONG_TERM) }
    var allDay by remember{mutableStateOf(oldTodo?.allDay?:true)};var priority by remember{mutableStateOf(oldTodo?.priority?:Priority.MEDIUM)};var dueTime by remember{mutableStateOf(oldTodo?.dueTime?.ifBlank{"20:00"}?:"20:00")};var targetTime by remember{mutableStateOf(oldCountdown?.targetTime?.ifBlank{"09:00"}?:"09:00")};var date by remember{mutableStateOf(oldPlan?.startDate?:oldHabit?.startDate?:LocalDate.now().plusDays(if(request.kind==TargetKind.COUNTDOWN)30 else 1))};var endDate by remember{mutableStateOf(oldPlan?.endDate?:LocalDate.now().plusDays(7))};var weekdays by remember{mutableStateOf(oldHabit?.weekdays?:(0..6).toSet())};var metric by remember{mutableStateOf(oldHabit?.metric?:HabitMetric.MINUTES)};var targetCount by remember{mutableStateOf((oldHabit?.targetCount?:1).toString())};var reminderHour by remember{mutableStateOf(oldHabit?.reminderHour?:9)};var reminderMinute by remember{mutableStateOf(oldHabit?.reminderMinute?:0)};var planId by remember{mutableStateOf(oldTodo?.planId?:oldCountdown?.planId)};var milestone by remember{mutableStateOf(oldPlan?.milestone?:oldCountdown?.milestone.orEmpty())};var defaultScene by remember{mutableStateOf(oldTodo?.defaultSceneId?:oldPlan?.defaultSceneId?:oldHabit?.defaultSceneId.orEmpty())};var error by remember{mutableStateOf("")}
    val save: () -> Unit = save@{
        val mins=minutes.toIntOrNull()?:0
        if(title.isBlank()){error="请输入名称";return@save}
        if(mins<1){error="目标时长必须大于 0 分钟";return@save}
        if(request.kind==TargetKind.PLAN&&endDate<date){error="计划的结束日期不能早于开始日期";return@save}
        if(request.kind==TargetKind.HABIT&&weekdays.isEmpty()){error="请至少选择一个执行日";return@save}
        if(request.kind==TargetKind.COUNTDOWN&&date<LocalDate.now()){error="倒计日目标必须是今天或未来日期";return@save}
        val id=request.id?:java.util.UUID.randomUUID().toString()
        val zone=ZoneId.systemDefault()
        val todoDueAt=if(allDay) date.atStartOfDay(zone).toInstant().toEpochMilli() else date.atTime(parseHhMm(dueTime)).atZone(zone).toInstant().toEpochMilli()
        val countdownTargetAt=date.atTime(parseHhMm(targetTime)).atZone(zone).toInstant().toEpochMilli()
        when(request.kind){
            TargetKind.TODO -> vm.saveTodo(Todo(id=id,text=title,kind=if(longTerm)TodoKind.LONG_TERM else TodoKind.ONE_OFF,done=oldTodo?.done?:false,dueAtEpochMillis=todoDueAt,allDay=allDay,priority=priority,estimatedMinutes=mins,note=note,reminder=reminder,planId=planId,defaultSceneId=defaultScene.ifBlank{null},dueTime=if(allDay)"" else normalizeHhMm(dueTime)))
            TargetKind.PLAN -> vm.savePlan(FocusPlan(id=id,title=title,startDate=date,endDate=endDate,targetMinutes=mins,doneMinutes=oldPlan?.doneMinutes?:0,description=note,reminder=reminder,milestone=milestone,defaultSceneId=defaultScene.ifBlank{null}))
            TargetKind.HABIT -> vm.saveHabit(Habit(id=id,title=title,weekdays=weekdays,metric=metric,targetMinutes=mins,targetCount=targetCount.toIntOrNull()?.coerceIn(1,99)?:1,streak=oldHabit?.streak?:0,todayMinutes=oldHabit?.todayMinutes?:0,todayCount=oldHabit?.todayCount?:0,reminder=reminder,reminderHour=reminderHour,reminderMinute=reminderMinute,startDate=date,defaultSceneId=defaultScene.ifBlank{null}))
            TargetKind.COUNTDOWN -> vm.saveCountdown(CountdownEvent(id=id,title=title,targetAtEpochMillis=countdownTargetAt,dailyMinutes=mins,note=note.ifBlank{"持续推进"},reminder=reminder,planId=planId,milestone=milestone,investedMinutes=oldCountdown?.investedMinutes?:0,targetTime=normalizeHhMm(targetTime)))
            else -> Unit
        }
        close()
    }
    val maxSheetHeight = LocalConfiguration.current.screenHeightDp.dp * .88f
    ModalBottomSheet(
        onDismissRequest = close,
        modifier = Modifier.widthIn(max = 390.dp),
        containerColor = FocusPrototypeColors.Surface,
        contentColor = FocusPrototypeColors.Text,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxSheetHeight)) {
            SheetHeader(
                title = "${if(request.id == null) "新建" else "编辑"}${targetLabel(request.kind)}",
                subtitle = "核心信息先完成，提醒与声场可在更多设置中补充",
                onClose = close,
                closeLabel = "关闭事务编辑器",
            )
            LazyColumn(
                Modifier.weight(1f, fill = false).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
        item { OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("标题", color = FocusPrototypeColors.Muted) }) }
        if(request.kind == TargetKind.TODO) item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SoundistChip("一次性", !longTerm, { longTerm=false }, Modifier.weight(1f)); SoundistChip("长期", longTerm, { longTerm=true }, Modifier.weight(1f)) } }
        if(request.kind==TargetKind.TODO) item{
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){DateField("截止日期",date,{date=it},Modifier.weight(1f));TimeField("截止时间",dueTime,enabled=!allDay,{dueTime=it},Modifier.weight(1f))}
            Row(Modifier.fillMaxWidth().padding(top=12.dp),verticalAlignment=Alignment.CenterVertically){Text("时间",Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,color=FocusPrototypeColors.Secondary);SoundistChip("全天",allDay,{allDay=!allDay})}
            Text("优先级",Modifier.padding(top=12.dp,bottom=6.dp),style=MaterialTheme.typography.labelSmall,color=FocusPrototypeColors.Secondary)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Priority.entries.forEach{SoundistChip(when(it){Priority.LOW->"低优先";Priority.MEDIUM->"中优先";Priority.HIGH->"高优先"},priority==it,{priority=it},Modifier.weight(1f))}}
        }
        if(request.kind==TargetKind.PLAN) item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){NumericSetting("开始日 +N",java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(),date).toInt(),0..365,Modifier.weight(1f)){date=LocalDate.now().plusDays(it.toLong())};NumericSetting("截止日 +N",java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(),endDate).toInt(),0..365,Modifier.weight(1f)){endDate=LocalDate.now().plusDays(it.toLong())}}}
        if(request.kind==TargetKind.HABIT) item{Text("执行日",style=MaterialTheme.typography.labelSmall,color=FocusPrototypeColors.Secondary);Row(horizontalArrangement=Arrangement.spacedBy(3.dp)){(0..6).forEach{idx->val day=if(idx==6)0 else idx+1;SoundistChip("一二三四五六日"[idx].toString(),day in weekdays,{weekdays=if(day in weekdays)weekdays-day else weekdays+day},Modifier.weight(1f))}};Row(Modifier.padding(top = 8.dp),horizontalArrangement=Arrangement.spacedBy(5.dp)){HabitMetric.entries.forEach{SoundistChip(when(it){HabitMetric.MINUTES->"按分钟";HabitMetric.COUNT->"按次数";HabitMetric.CHECK->"完成打卡"},metric==it,{metric=it},Modifier.weight(1f))}};if(metric!=HabitMetric.MINUTES)OutlinedTextField(targetCount,{targetCount=it.filter(Char::isDigit)},Modifier.fillMaxWidth(),label={Text("每日目标次数",color=FocusPrototypeColors.Muted)})}
        if(request.kind==TargetKind.COUNTDOWN) item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){DateField("目标日期",date,{date=it},Modifier.weight(1f));TimeField("目标时间",targetTime,enabled=true,{targetTime=it},Modifier.weight(1f))};Text("目标必须是今天或未来日期",Modifier.padding(top=6.dp),style=MaterialTheme.typography.labelSmall,color=FocusPrototypeColors.Muted)}
        item { OutlinedTextField(minutes, { minutes=it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label={Text(if(request.kind==TargetKind.PLAN)"计划总投入（分钟）" else if(request.kind==TargetKind.TODO)"预计专注（分钟）" else "每日目标（分钟）",color=FocusPrototypeColors.Muted)}) }
        item { TextButton({more=!more},Modifier.fillMaxWidth()){Text(if(more)"收起更多设置" else "更多设置",color=FocusPrototypeColors.AmbientLight)} }
        if(more) { item { OutlinedTextField(note,{note=it},Modifier.fillMaxWidth(),minLines=2,label={Text("说明",color=FocusPrototypeColors.Muted)}) }; item { Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){ ReminderOffset.entries.forEach { SoundistChip(reminderLabel(it),reminder==it,{reminder=it}) } } };if(request.kind==TargetKind.HABIT&&reminder!=ReminderOffset.NONE)item{Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){NumericSetting("提醒小时",reminderHour,0..23,Modifier.weight(1f)){reminderHour=it};NumericSetting("提醒分钟",reminderMinute,0..59,Modifier.weight(1f)){reminderMinute=it}}};if(request.kind==TargetKind.TODO||request.kind==TargetKind.COUNTDOWN)item{Text("关联计划",style=MaterialTheme.typography.labelSmall,color=FocusPrototypeColors.Secondary);Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)){SoundistChip("不关联",planId==null,{planId=null});s.plans.forEach{SoundistChip(it.title,planId==it.id,{planId=it.id})}}};if(request.kind==TargetKind.PLAN||request.kind==TargetKind.COUNTDOWN)item{OutlinedTextField(milestone,{milestone=it},Modifier.fillMaxWidth(),label={Text("下一里程碑",color=FocusPrototypeColors.Muted)})};item{DefaultSceneField(defaultScene,{defaultScene=it})} }
        if(error.isNotBlank())item{Surface(color=FocusPrototypeColors.Danger.copy(alpha=.08f),border=BorderStroke(1.dp,FocusPrototypeColors.Danger.copy(alpha=.25f)),shape=MaterialTheme.shapes.medium){Text(error,Modifier.fillMaxWidth().padding(12.dp),color=FocusPrototypeColors.Danger,style=MaterialTheme.typography.bodySmall)}}
            }
            HorizontalDivider(color = FocusPrototypeColors.Border)
            Row(
                Modifier.fillMaxWidth().padding(start=16.dp,end=16.dp,top=12.dp,bottom=24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                request.id?.let { id ->
                    OutlinedIconButton(
                        onClick = { vm.archive(request.kind,id,true);close() },
                        modifier = Modifier.size(48.dp),
                        border = BorderStroke(1.dp, FocusPrototypeColors.Border),
                    ) { Icon(archive, "归档", tint = FocusPrototypeColors.Secondary) }
                }
                Button(
                    onClick = save,
                    modifier = Modifier.weight(1f).height(48.dp),
                    enabled = title.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor=FocusPrototypeColors.Ambient,contentColor=FocusPrototypeColors.Canvas),
                    shape = MaterialTheme.shapes.medium,
                ) { Text(if(request.id==null)"创建并保存" else "保存修改",fontWeight=FontWeight.SemiBold) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ArchiveDialog(s: ProductivityState, vm: ProductivityViewModel) {
    val archived=listOf(s.todos.filter{it.archived}.map{Triple(TargetKind.TODO,it.id,it.text)},s.plans.filter{it.archived}.map{Triple(TargetKind.PLAN,it.id,it.title)},s.habits.filter{it.archived}.map{Triple(TargetKind.HABIT,it.id,it.title)},s.countdowns.filter{it.archived}.map{Triple(TargetKind.COUNTDOWN,it.id,it.title)}).flatten()
    val close: () -> Unit = { vm.archiveManager(false); Unit }
    val maxSheetHeight=LocalConfiguration.current.screenHeightDp.dp*.78f
    ModalBottomSheet(
        onDismissRequest=close,
        modifier=Modifier.widthIn(max=390.dp),
        containerColor=FocusPrototypeColors.Surface,
        contentColor=FocusPrototypeColors.Text,
        shape=androidx.compose.foundation.shape.RoundedCornerShape(topStart=16.dp,topEnd=16.dp),
        dragHandle=null,
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max=maxSheetHeight)) {
            SheetHeader("事务归档","归档不会删除记录，可恢复到原分类",close,"关闭归档管理")
            if(archived.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(vertical=48.dp),horizontalAlignment=Alignment.CenterHorizontally){
                    Icon(rotateCcw,null,tint=Color(0xFF5F6D69))
                    Spacer(Modifier.height(12.dp))
                    Text("还没有归档事务",color=FocusPrototypeColors.Secondary,style=MaterialTheme.typography.bodyMedium)
                }
            } else LazyColumn(Modifier.weight(1f,fill=false).fillMaxWidth(),contentPadding=PaddingValues(start=16.dp,end=16.dp,bottom=24.dp)) {
                items(archived,key={"${it.first}-${it.second}"}) { item ->
                    Row(Modifier.fillMaxWidth().heightIn(min=56.dp).padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically){
                        Surface(color=FocusPrototypeColors.SurfaceHigh,shape=MaterialTheme.shapes.small){Text(targetLabel(item.first),Modifier.padding(horizontal=8.dp,vertical=4.dp),style=MaterialTheme.typography.labelSmall,color=FocusPrototypeColors.Muted)}
                        Spacer(Modifier.width(12.dp));Text(item.third,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,color=FocusPrototypeColors.Text)
                        TextButton({vm.archive(item.first,item.second,false)}){Text("恢复",color=FocusPrototypeColors.AmbientLight)}
                    }
                    HorizontalDivider(color=FocusPrototypeColors.Border.copy(alpha=.7f))
                }
            }
        }
    }
}

@Composable private fun SheetHeader(title:String,subtitle:String,onClose:()->Unit,closeLabel:String){
    Row(Modifier.fillMaxWidth().padding(start=16.dp,end=8.dp,top=12.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically){
        Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Medium,color=FocusPrototypeColors.Text);Text(subtitle,Modifier.padding(top=2.dp),style=MaterialTheme.typography.labelSmall,color=FocusPrototypeColors.Muted)}
        IconButton(onClose,Modifier.size(44.dp)){Icon(x,closeLabel,tint=FocusPrototypeColors.Muted,modifier=Modifier.size(16.dp))}
    }
    HorizontalDivider(color=FocusPrototypeColors.Border)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SleepTimerPanel(s: ProductivityState, vm: ProductivityViewModel, onDismiss: () -> Unit) {
    val sleep=s.sleep; val now by tickingNow(sleep.status != SleepStatus.IDLE,vm.clock); var routineName by remember{mutableStateOf("")}; var custom by remember(sleep.plannedMinutes){mutableStateOf(sleep.plannedMinutes.toString())}
    val maxSheetHeight=LocalConfiguration.current.screenHeightDp.dp*.88f
    BackHandler(onBack=onDismiss)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.65f)),contentAlignment=Alignment.BottomCenter){Box(Modifier.fillMaxSize().clickable(onClick=onDismiss,indication=null,interactionSource=remember{MutableInteractionSource()}))
    LazyColumn(Modifier.fillMaxWidth().widthIn(max=390.dp).heightIn(max=maxSheetHeight).clip(androidx.compose.foundation.shape.RoundedCornerShape(topStart=16.dp,topEnd=16.dp)).background(FocusPrototypeColors.Surface).border(1.dp,FocusPrototypeColors.Border,androidx.compose.foundation.shape.RoundedCornerShape(topStart=16.dp,topEnd=16.dp)),contentPadding=PaddingValues(start=16.dp,end=16.dp,top=12.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Box(Modifier.fillMaxWidth()){Box(Modifier.align(Alignment.TopCenter).width(36.dp).height(4.dp).background(Color(0xFF43565A),androidx.compose.foundation.shape.CircleShape));Row(Modifier.fillMaxWidth().padding(top=12.dp),Arrangement.SpaceBetween,Alignment.CenterVertically){Column{Text("睡眠定时",fontSize=16.sp,fontWeight=FontWeight.SemiBold,color=FocusPrototypeColors.Text);Text("让声音自然退场，而不是突然中断",Modifier.padding(top=2.dp),fontSize=11.sp,color=FocusPrototypeColors.Muted)};IconButton(onDismiss,Modifier.size(44.dp)){Icon(x,"关闭睡眠定时",Modifier.size(16.dp),tint=FocusPrototypeColors.Muted)}}} }
        if(sleep.status!=SleepStatus.IDLE) item { Column(Modifier.fillMaxWidth().background(FocusPrototypeColors.Ambient.copy(alpha=.05f)).padding(vertical=12.dp)){Row(Modifier.fillMaxWidth(),Arrangement.SpaceBetween,Alignment.Bottom){Column(Modifier.weight(1f)){Text(formatSleepRemaining(SleepStateMachine.remainingMillis(sleep,now)),fontSize=30.sp,fontWeight=FontWeight.Medium,fontFamily=FontFamily.Monospace,color=FocusPrototypeColors.Text);Text("${formatClock(sleep.endsAtEpochMillis)} · ${sleep.target.label} · ${if(sleep.fadeMinutes>0)"最后 ${sleep.fadeMinutes} 分钟渐弱" else "直接停止"}",Modifier.padding(top=4.dp),fontSize=11.sp,color=FocusPrototypeColors.Secondary)};StatusBadge("进行中")};Row(Modifier.fillMaxWidth().padding(top=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton({vm.extendSleep()},Modifier.weight(1f).heightIn(min=44.dp),shape=MaterialTheme.shapes.medium,border=BorderStroke(1.dp,FocusPrototypeColors.Border)){Text("+15 分钟",fontSize=12.sp,color=FocusPrototypeColors.Secondary)};OutlinedButton({vm.toggleSleepAdjust()},Modifier.weight(1f).heightIn(min=44.dp),shape=MaterialTheme.shapes.medium,border=BorderStroke(1.dp,FocusPrototypeColors.Border)){Text(if(sleep.status==SleepStatus.EDITING)"收起调整" else "调整",fontSize=12.sp,color=FocusPrototypeColors.AmbientLight)};OutlinedButton({vm.cancelSleep()},Modifier.weight(1f).heightIn(min=44.dp),shape=MaterialTheme.shapes.medium,border=BorderStroke(1.dp,FocusPrototypeColors.Danger.copy(alpha=.3f))){Text("取消",fontSize=12.sp,color=FocusPrototypeColors.Danger)}}} }
        if(sleep.status!=SleepStatus.RUNNING || sleep.status==SleepStatus.EDITING) {
            item { Text("结束方式",fontSize=11.sp,color=FocusPrototypeColors.Secondary);Row(Modifier.fillMaxWidth().height(48.dp).background(FocusPrototypeColors.SurfaceLow,MaterialTheme.shapes.medium).padding(4.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)){SleepEndMode.entries.forEach{Box(Modifier.weight(1f).fillMaxHeight().background(if(sleep.endMode==it)FocusPrototypeColors.SurfaceHigh else Color.Transparent,MaterialTheme.shapes.small).clickable{vm.configureSleep(sleep.copy(endMode=it))},contentAlignment=Alignment.Center){Text(if(it==SleepEndMode.DURATION)"持续时长" else "指定时间",fontSize=12.sp,color=if(sleep.endMode==it)FocusPrototypeColors.Text else FocusPrototypeColors.Muted)}}} }
            if(sleep.endMode==SleepEndMode.DURATION) item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(15,30,45,60).forEach{SleepOption("$it 分",sleep.plannedMinutes==it,{vm.configureSleep(sleep.copy(plannedMinutes=it))},Modifier.weight(1f))}};Spacer(Modifier.height(8.dp));Row(Modifier.fillMaxWidth().heightIn(min=44.dp).background(FocusPrototypeColors.SurfaceHigh,MaterialTheme.shapes.medium).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){Text("自定义",fontSize=12.sp,color=FocusPrototypeColors.Secondary);OutlinedTextField(custom,{custom=it.filter(Char::isDigit);it.toIntOrNull()?.let{m->vm.configureSleep(sleep.copy(plannedMinutes=m.coerceIn(1,480))) }},Modifier.weight(1f),singleLine=true,colors=OutlinedTextFieldDefaults.colors(unfocusedBorderColor=Color.Transparent,focusedBorderColor=Color.Transparent));Text("分钟",fontSize=12.sp,color=FocusPrototypeColors.Muted)} } else item { Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){NumericSetting("小时",sleep.clockHour,0..23,Modifier.weight(1f)){vm.configureSleep(sleep.copy(clockHour=it))};NumericSetting("分钟",sleep.clockMinute,0..59,Modifier.weight(1f)){vm.configureSleep(sleep.copy(clockMinute=it))}} }
            item { Text("停止范围",fontSize=11.sp,color=FocusPrototypeColors.Secondary);Spacer(Modifier.height(8.dp));Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){SleepTarget.entries.forEach{SleepOption(it.label,sleep.target==it,{vm.configureSleep(sleep.copy(target=it))},Modifier.weight(1f))}} }
            item { Text("结束前渐弱",fontSize=11.sp,color=FocusPrototypeColors.Secondary);Spacer(Modifier.height(8.dp));Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf(0,5,10,15).forEach{SleepOption(if(it==0)"关闭" else "$it 分",sleep.fadeMinutes==it,{vm.configureSleep(sleep.copy(fadeMinutes=it))},Modifier.weight(1f))}} }
            item { Surface(color=FocusPrototypeColors.SurfaceLow,border=BorderStroke(1.dp,FocusPrototypeColors.Border),shape=MaterialTheme.shapes.medium){Text("${if(sleep.endMode==SleepEndMode.CLOCK)"${sleep.clockHour.toString().padStart(2,'0')}:${sleep.clockMinute.toString().padStart(2,'0')} 停止" else "${sleep.plannedMinutes} 分钟后停止"}${sleep.target.label}，${if(sleep.fadeMinutes>0)"最后 ${sleep.fadeMinutes} 分钟渐弱" else "到时直接停止"}。",Modifier.padding(horizontal=12.dp,vertical=10.dp),fontSize=11.sp,color=FocusPrototypeColors.Secondary)};Spacer(Modifier.height(16.dp));Button({vm.startSleep()},Modifier.fillMaxWidth().height(48.dp),colors=ButtonDefaults.buttonColors(containerColor=FocusPrototypeColors.Ambient,contentColor=FocusPrototypeColors.Canvas),shape=MaterialTheme.shapes.medium){Text(if(sleep.status==SleepStatus.EDITING)"应用新设置" else "开始睡眠定时",fontSize=14.sp,fontWeight=FontWeight.SemiBold)} }
        }
        if(sleep.status==SleepStatus.IDLE) item { HorizontalDivider(color=FocusPrototypeColors.Border);Row(Modifier.fillMaxWidth().padding(top=16.dp),Arrangement.SpaceBetween){Text("睡眠方案",fontSize=11.sp,color=FocusPrototypeColors.Muted);Text("点击载入",fontSize=11.sp,color=FocusPrototypeColors.Muted)};s.sleepRoutines.forEach{r->Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f).clickable{vm.loadRoutine(r)}.padding(vertical=8.dp)){Text(r.name,fontSize=14.sp,color=FocusPrototypeColors.Text);Text("${r.minutes} 分钟 · ${r.target.label} · 渐弱 ${r.fadeMinutes} 分钟",Modifier.padding(top=2.dp),fontSize=11.sp,color=FocusPrototypeColors.Muted)};IconButton({vm.deleteRoutine(r.id)},Modifier.size(44.dp)){Icon(trash2,null,Modifier.size(14.dp),tint=FocusPrototypeColors.Muted)}}};Row(Modifier.padding(top=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(routineName,{routineName=it},Modifier.weight(1f).heightIn(min=44.dp),placeholder={Text("为当前设置命名",fontSize=14.sp,color=FocusPrototypeColors.Muted)},singleLine=true);OutlinedButton({vm.saveRoutine(routineName);routineName=""},Modifier.heightIn(min=44.dp),shape=MaterialTheme.shapes.medium,border=BorderStroke(1.dp,FocusPrototypeColors.Ambient.copy(alpha=.25f))){Text("保存方案",fontSize=12.sp,color=FocusPrototypeColors.AmbientLight)}} }
    } }
}

@Composable private fun SleepOption(label:String,active:Boolean,onClick:()->Unit,modifier:Modifier=Modifier){Surface(onClick=onClick,modifier=modifier.heightIn(min=44.dp),color=if(active)FocusPrototypeColors.Ambient.copy(alpha=.1f) else Color.Transparent,contentColor=if(active)FocusPrototypeColors.AmbientLight else FocusPrototypeColors.Muted,border=BorderStroke(1.dp,if(active)FocusPrototypeColors.Ambient.copy(alpha=.35f) else FocusPrototypeColors.Border),shape=MaterialTheme.shapes.medium){Box(Modifier.fillMaxWidth().heightIn(min=44.dp),contentAlignment=Alignment.Center){Text(label,fontSize=12.sp)}}}

@Composable private fun StatusBadge(text:String){Surface(color=FocusPrototypeColors.Ambient.copy(alpha=.08f),shape=androidx.compose.foundation.shape.CircleShape,border=BorderStroke(1.dp,FocusPrototypeColors.Ambient.copy(alpha=.20f))){Text(text,Modifier.padding(horizontal=10.dp,vertical=4.dp),style=MaterialTheme.typography.labelSmall,color=FocusPrototypeColors.AmbientLight)}}
@Composable private fun EntityActions(kind:TargetKind,id:String,vm:ProductivityViewModel){Row(Modifier.fillMaxWidth(),Arrangement.End){IconButton({vm.move(kind,id,-1)}){Icon(chevronUp,"向前排序",tint=FocusPrototypeColors.Secondary)};IconButton({vm.move(kind,id,1)}){Icon(chevronDown,"向后排序",tint=FocusPrototypeColors.Secondary)};IconButton({when(kind){TargetKind.TODO->vm.deleteTodo(id);TargetKind.PLAN->vm.deletePlan(id);TargetKind.HABIT->vm.deleteHabit(id);TargetKind.COUNTDOWN->vm.deleteCountdown(id);else->Unit}}){Icon(trash2,"删除",tint=FocusPrototypeColors.Muted)}}}
@Composable private fun RoundAction(icon:ImageVector,label:String,onClick:()->Unit,primary:Boolean=false){Column(horizontalAlignment=Alignment.CenterHorizontally){FilledIconButton(onClick,colors=IconButtonDefaults.filledIconButtonColors(containerColor=if(primary)SoundistColors.Teal else SoundistColors.Raised,contentColor=if(primary)SoundistColors.Abyss else SoundistColors.Text)){Icon(icon,null)};Text(label,style=MaterialTheme.typography.labelSmall,color=SoundistColors.TextMuted)}}
@Composable private fun NumericSetting(label:String,value:Int,range:IntRange,modifier:Modifier=Modifier,onChange:(Int)->Unit){var text by remember(value){mutableStateOf(value.toString())};OutlinedTextField(text,{text=it.filter(Char::isDigit);it.toIntOrNull()?.let{n->onChange(n.coerceIn(range))}},modifier,label={Text(label,color=FocusPrototypeColors.Muted)},singleLine=true)}
@Composable private fun ToggleChip(label:String,active:Boolean,modifier:Modifier=Modifier,onClick:()->Unit)=SoundistChip(label,active,onClick,modifier)

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun DateField(label: String, value: LocalDate, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Column(modifier.clickable { open = true }) {
        Text(label, fontSize = 11.sp, color = FocusPrototypeColors.Secondary)
        Spacer(Modifier.height(4.dp))
        Surface(color = FocusPrototypeColors.SurfaceHigh, border = BorderStroke(1.dp, FocusPrototypeColors.Border), shape = MaterialTheme.shapes.medium) {
            Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(value.toString(), Modifier.weight(1f), fontSize = 14.sp, color = FocusPrototypeColors.Text)
                Icon(chevronDown, null, Modifier.size(16.dp), tint = FocusPrototypeColors.Secondary)
            }
        }
    }
    if (open) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = value.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton({ pickerState.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()) }; open = false }) { Text("确定", color = FocusPrototypeColors.AmbientLight) } },
            dismissButton = { TextButton({ open = false }) { Text("取消", color = FocusPrototypeColors.Secondary) } },
        ) { DatePicker(pickerState, title = null, headline = null) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun TimeField(label: String, value: String, enabled: Boolean, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Column(modifier.clickable(enabled = enabled) { open = true }) {
        Text(label, fontSize = 11.sp, color = FocusPrototypeColors.Secondary)
        Spacer(Modifier.height(4.dp))
        Surface(color = if (enabled) FocusPrototypeColors.SurfaceHigh else FocusPrototypeColors.SurfaceHigh.copy(alpha = .45f), border = BorderStroke(1.dp, FocusPrototypeColors.Border), shape = MaterialTheme.shapes.medium) {
            Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (enabled) normalizeHhMm(value) else "—", Modifier.weight(1f), fontSize = 14.sp, color = if (enabled) FocusPrototypeColors.Text else FocusPrototypeColors.Muted)
                Icon(chevronDown, null, Modifier.size(16.dp), tint = FocusPrototypeColors.Secondary)
            }
        }
    }
    if (open) {
        val initial = parseHhMm(value)
        val timeState = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton({ onChange("%02d:%02d".format(timeState.hour, timeState.minute)); open = false }) { Text("确定", color = FocusPrototypeColors.AmbientLight) } },
            dismissButton = { TextButton({ open = false }) { Text("取消", color = FocusPrototypeColors.Secondary) } },
            text = { TimePicker(timeState) },
        )
    }
}

/** App.tsx `默认声场` 下拉——选项来自 quickPresets（scenePresets），空值表示跟随当前声场。 */
@Composable private fun DefaultSceneField(value: String, onChange: (String) -> Unit) {
    val presets by LocalProductivityDependencies.current.scenePresets.collectAsState()
    Column {
        Text("默认声场", fontSize = 11.sp, color = FocusPrototypeColors.Secondary)
        Spacer(Modifier.height(4.dp))
        SoundistSelect(
            value = value,
            options = listOf("" to "跟随当前声场") + presets,
            onSelect = onChange,
            modifier = Modifier.fillMaxWidth(),
            minHeight = 44.dp,
            background = FocusPrototypeColors.SurfaceHigh,
            borderColor = FocusPrototypeColors.Border,
            valueColor = FocusPrototypeColors.Text,
            itemBackground = FocusPrototypeColors.SurfaceHigh,
            fontSize = 14.sp,
        )
    }
}

private fun parseHhMm(value: String): LocalTime = runCatching { LocalTime.parse(value) }.getOrDefault(LocalTime.of(9, 0))
private fun normalizeHhMm(value: String): String { val t = parseHhMm(value); return "%02d:%02d".format(t.hour, t.minute) }
@Composable private fun PageHeading(title:String,subtitle:String,add:(()->Unit)?=null){Row(Modifier.fillMaxWidth(),Arrangement.SpaceBetween,Alignment.CenterVertically){Column{Text(title,fontSize=14.sp,fontWeight=FontWeight.Medium,color=FocusPrototypeColors.Text);Text(subtitle,Modifier.padding(top=2.dp),fontSize=11.sp,color=FocusPrototypeColors.Muted)};add?.let{TextButton(it){Icon(plus,null,Modifier.size(16.dp));Text("添加",fontSize=12.sp,color=FocusPrototypeColors.AmbientLight)}}}}
@Composable private fun EntitySection(title:String,action:(()->Unit)?=null,actionLabel:String="全部",content:@Composable ColumnScope.()->Unit){Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Row(Modifier.fillMaxWidth(),Arrangement.SpaceBetween){Text(title,style=MaterialTheme.typography.labelSmall,color=FocusPrototypeColors.Muted);action?.let{TextButton(it){Text(actionLabel,fontSize=11.sp,color=FocusPrototypeColors.AmbientLight)}}};content()}}
@Composable private fun EmptyState(text:String,action:String,onClick:()->Unit){Column(Modifier.fillMaxWidth().padding(vertical=48.dp),horizontalAlignment=Alignment.CenterHorizontally){Icon(check,null,tint=FocusPrototypeColors.Ambient);Text(text,color=FocusPrototypeColors.Secondary);TextButton(onClick){Text(action,fontSize=12.sp,color=FocusPrototypeColors.AmbientLight)}}}
@Composable private fun tickingNow(active:Boolean,clock:Clock):State<Long>{val state=remember{mutableLongStateOf(clock.now())};LaunchedEffect(active){state.longValue=clock.now();while(active){delay(500);state.longValue=clock.now()}};return state}
private fun activeReminders(s:ProductivityState): List<Triple<String,String,ReminderOffset>> = buildList{addAll(s.todos.filter{!it.archived&&it.reminder!=ReminderOffset.NONE}.map{Triple("待办",it.text,it.reminder)});addAll(s.plans.filter{!it.archived&&it.reminder!=ReminderOffset.NONE}.map{Triple("计划",it.title,it.reminder)});addAll(s.habits.filter{!it.archived&&it.reminder!=ReminderOffset.NONE}.map{Triple("习惯",it.title,it.reminder)});addAll(s.countdowns.filter{!it.archived&&it.reminder!=ReminderOffset.NONE}.map{Triple("倒计日",it.title,it.reminder)})}
private fun reminderText(r:ReminderOffset)=when(r){ReminderOffset.AT_TIME->"到时";ReminderOffset.TEN_MINUTES->"提前 10 分钟";ReminderOffset.ONE_HOUR->"提前 1 小时";ReminderOffset.ONE_DAY->"提前 1 天";ReminderOffset.NONE->""}
private fun targetLabel(k:TargetKind)=when(k){TargetKind.TODO->"待办";TargetKind.PLAN->"计划";TargetKind.HABIT->"习惯";TargetKind.COUNTDOWN->"倒计日";TargetKind.FREE->"自由专注"}
private fun reminderLabel(r:ReminderOffset)=when(r){ReminderOffset.NONE->"不提醒";ReminderOffset.AT_TIME->"到时提醒";ReminderOffset.TEN_MINUTES->"提前 10 分钟";ReminderOffset.ONE_HOUR->"提前 1 小时";ReminderOffset.ONE_DAY->"提前 1 天"}
/** App.tsx `pad(displayMins):pad(displayRemainSecs)`——分钟可超 99 的 mm:ss。 */
private fun formatMillis(ms:Long):String {val sec=(ms/1000).coerceAtLeast(0);return "%d:%02d".format(sec/60,sec%60)}
/** App.tsx `sleepRemainingLabel`——≥1 小时显示 H:MM:SS，否则 M:SS。 */
private fun formatSleepRemaining(ms:Long):String {val sec=(ms/1000).coerceAtLeast(0);return if(sec>=3600)"%d:%02d:%02d".format(sec/3600,(sec%3600)/60,sec%60) else "%d:%02d".format(sec/60,sec%60)}
private fun formatEpoch(value:Long?):String=value?.let{Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))}?:"未设定"
private fun formatClock(value:Long?):String=value?.let{Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))}.orEmpty()
private fun startOfDayAfter(days:Int):Long=LocalDate.now().plusDays(days.toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
private fun daysUntil(value:Long):Long=java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(),Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).toLocalDate())
