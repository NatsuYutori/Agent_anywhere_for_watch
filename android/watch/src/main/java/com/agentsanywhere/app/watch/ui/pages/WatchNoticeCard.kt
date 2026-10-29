package com.agentsanywhere.app.watch.ui.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentsanywhere.app.feature.sessiondetail.RuntimeInputRequestDraft
import com.agentsanywhere.app.feature.sessiondetail.RuntimeInputRequestQuestion
import com.agentsanywhere.app.feature.sessiondetail.RuntimeNotice
import com.agentsanywhere.app.feature.sessiondetail.buildPayload
import com.agentsanywhere.app.feature.sessiondetail.initialDrafts
import com.agentsanywhere.app.feature.sessiondetail.inputRequestForm
import com.agentsanywhere.app.feature.sessiondetail.isComplete
import com.agentsanywhere.app.ui.designsystem.LocalAAColors
import com.agentsanywhere.app.watch.ui.WatchCaption
import com.agentsanywhere.app.watch.ui.WatchChoiceChip
import com.agentsanywhere.app.watch.ui.WatchPrimaryButton
import com.agentsanywhere.app.watch.ui.WatchSecondaryButton
import com.agentsanywhere.app.watch.ui.WatchTextField

/** Height of an answer button. Same as the other compact actions on this page. */
private val ApprovalActionHeight = 32.dp

/** Height of the free-text field behind the "其他" option. */
private val ApprovalCustomHeight = 30.dp

/**
 * The card that answers a notice the runtime is waiting on.
 *
 * Two shapes arrive over the wire and both read as "待审批":
 *
 * - a plain approval, whose choices are the action buttons themselves (允许 / 拒绝);
 * - an input request - the runtime asking the user a question - whose choices are *not* in
 *   `actions` at all but inside the action's `uiSchema`, as `component = "inputRequest"`.
 *
 * The second case is why a question used to show up as a title with nothing under it: every action
 * on it requires input, the old card skipped exactly those, and it fell back to telling the user to
 * finish on a phone or a computer. The options are rendered here instead.
 */
@Composable
internal fun WatchNoticeCard(
    notice: RuntimeNotice,
    busy: Boolean,
    onRespond: (noticeId: String, actionId: String, input: Map<String, Any?>?) -> Unit,
) {
    val colors = LocalAAColors.current
    val shape = RoundedCornerShape(10.dp)
    val form = remember(notice.noticeId, notice.revision, notice.actions) {
        notice.inputRequestForm()
    }
    val actions = notice.actions.filter { it.actionId.isNotBlank() }
    // Actions that need the user to type something else are not answerable with one tap; the
    // form action is the exception, because the form below is what fills it in.
    val formActionId = form?.action?.actionId
    val plainActions = actions.filter { !it.input.required || it.actionId == formActionId }
    val tapActions = plainActions.filter { it.actionId != formActionId }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.noticeWarning.copy(alpha = 0.16f))
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = notice.title.ifBlank { "需要确认" },
            color = colors.ink,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
        )
        notice.message?.takeIf(String::isNotBlank)?.let { message ->
            Text(text = message, color = colors.inkSoft, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 4)
        }

        if (form != null) {
            WatchInputRequestForm(
                questions = form.questions,
                busy = busy,
                submitLabel = form.action.label.ifBlank { "提交" },
                isComplete = { drafts -> form.isComplete(drafts) },
                onSubmit = { drafts ->
                    onRespond(notice.noticeId, form.action.actionId, form.buildPayload(drafts))
                },
            )
        }

        tapActions.forEach { action ->
            WatchPrimaryButton(
                text = action.label.ifBlank { action.actionId },
                onClick = { onRespond(notice.noticeId, action.actionId, null) },
                enabled = !busy,
                height = ApprovalActionHeight,
                cornerRadius = ApprovalActionHeight / 2,
            )
        }

        if (form == null && tapActions.isEmpty()) {
            WatchCaption(
                text = "这个审批需要在手机或电脑上填写。",
                maxLines = 2,
            )
        }
    }
}

/**
 * One question, its options, and the button that answers it.
 *
 * Single-select replaces the selection, multi-select toggles it - the two behave differently
 * because the runtime reads them differently, and guessing wrong here means answering a question
 * with an option the user did not pick.
 */
@Composable
private fun WatchInputRequestForm(
    questions: List<RuntimeInputRequestQuestion>,
    busy: Boolean,
    submitLabel: String,
    isComplete: (Map<String, RuntimeInputRequestDraft>) -> Boolean,
    onSubmit: (Map<String, RuntimeInputRequestDraft>) -> Unit,
) {
    var drafts by remember(questions) {
        mutableStateOf(questions.associate { it.id to RuntimeInputRequestDraft() })
    }
    val complete = isComplete(drafts)

    questions.forEach { question ->
        val draft = drafts[question.id] ?: RuntimeInputRequestDraft()
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = question.prompt,
                color = LocalAAColors.current.ink,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 3,
            )
            if (question.multiple) {
                WatchCaption(text = "可多选", maxLines = 1)
            }
            question.options.forEach { option ->
                val selected = option.id in draft.optionIds
                WatchChoiceChip(
                    text = option.label,
                    selected = selected,
                    enabled = !busy,
                    onClick = {
                        val optionIds = when {
                            question.multiple && selected -> draft.optionIds - option.id
                            question.multiple -> draft.optionIds + option.id
                            else -> listOf(option.id)
                        }
                        drafts = drafts + (question.id to draft.copy(optionIds = optionIds, useCustom = false))
                    },
                )
            }
            if (question.allowCustom) {
                WatchChoiceChip(
                    text = "其他（自己输入）",
                    selected = draft.useCustom,
                    enabled = !busy,
                    onClick = {
                        drafts = drafts + (question.id to draft.copy(useCustom = !draft.useCustom))
                    },
                )
                AnimatedVisibility(
                    visible = draft.useCustom,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    WatchTextField(
                        value = draft.customText,
                        onValueChange = { text ->
                            drafts = drafts + (question.id to draft.copy(customText = text, useCustom = true))
                        },
                        placeholder = "输入回答",
                        enabled = !busy,
                        height = ApprovalCustomHeight,
                        textSize = 11.sp,
                        verticalPadding = 4.dp,
                    )
                }
            }
        }
    }

    WatchPrimaryButton(
        text = submitLabel,
        onClick = { onSubmit(drafts) },
        enabled = !busy && complete,
        height = ApprovalActionHeight,
        cornerRadius = ApprovalActionHeight / 2,
    )
}
