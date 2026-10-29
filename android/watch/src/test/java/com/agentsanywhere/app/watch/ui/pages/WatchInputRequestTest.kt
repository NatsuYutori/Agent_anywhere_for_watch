package com.agentsanywhere.app.watch.ui.pages

import com.agentsanywhere.app.feature.sessiondetail.RuntimeInputRequestDraft
import com.agentsanywhere.app.feature.sessiondetail.RuntimeNotice
import com.agentsanywhere.app.feature.sessiondetail.RuntimeNoticeAction
import com.agentsanywhere.app.feature.sessiondetail.RuntimeNoticeActionInput
import com.agentsanywhere.app.feature.sessiondetail.buildPayload
import com.agentsanywhere.app.feature.sessiondetail.initialDrafts
import com.agentsanywhere.app.feature.sessiondetail.inputRequestForm
import com.agentsanywhere.app.feature.sessiondetail.isComplete
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The runtime asking the user a question.
 *
 * The options of an input request are not in the notice's `actions` - they are inside the action's
 * `uiSchema`, as `component = "inputRequest"`. That is why a question used to render as a title
 * with no way to answer it, so the parsing and the payload are pinned here: if this breaks, the
 * watch goes back to showing "待审批" and nothing to press.
 */
class WatchInputRequestTest {

    @Test
    fun `options are read out of the action's ui schema`() {
        val notice = noticeWithForm()

        val form = notice.inputRequestForm()

        assertNotNull(form)
        assertEquals("answer", form!!.action.actionId)
        assertEquals(1, form.questions.size)
        val question = form.questions.single()
        assertEquals("q1", question.id)
        assertEquals("用哪个方案？", question.prompt)
        assertFalse(question.multiple)
        assertTrue(question.allowCustom)
        assertEquals(listOf("方案 A", "方案 B"), question.options.map { it.label })
        assertEquals(listOf("a", "b"), question.options.map { it.id })
    }

    @Test
    fun `a notice without an input request form is left to its action buttons`() {
        assertNull(noticeWithForm(uiSchema = null).inputRequestForm())
    }

    @Test
    fun `nothing is complete until something is chosen`() {
        val form = noticeWithForm().inputRequestForm()!!

        val empty = form.initialDrafts()
        assertFalse(form.isComplete(empty))

        val chosen = empty + ("q1" to RuntimeInputRequestDraft(optionIds = listOf("a")))
        assertTrue(form.isComplete(chosen))
    }

    @Test
    fun `custom text alone completes a question that allows it`() {
        val form = noticeWithForm().inputRequestForm()!!

        val custom = form.initialDrafts() +
            ("q1" to RuntimeInputRequestDraft(customText = "方案 C", useCustom = true))

        assertTrue(form.isComplete(custom))
    }

    @Test
    fun `the payload carries the chosen ids under the question id`() {
        val form = noticeWithForm().inputRequestForm()!!

        val payload = form.buildPayload(
            form.initialDrafts() + ("q1" to RuntimeInputRequestDraft(optionIds = listOf("a"))),
        )

        assertEquals(mapOf("q1" to listOf("a")), answersOf(payload))
    }

    @Test
    fun `custom text is sent only when it was actually used`() {
        val form = noticeWithForm().inputRequestForm()!!

        val typed = form.buildPayload(
            form.initialDrafts() + ("q1" to RuntimeInputRequestDraft(customText = "方案 C", useCustom = true)),
        )
        val ignored = form.buildPayload(
            form.initialDrafts() + ("q1" to RuntimeInputRequestDraft(customText = "方案 C")),
        )

        assertEquals(mapOf("customText" to "方案 C"), customTextOf(typed))
        assertEquals(emptyMap<String, Any?>(), customTextOf(ignored))
    }

    @Suppress("UNCHECKED_CAST")
    private fun answersOf(payload: Map<String, Any?>): Map<String, List<String>> {
        val answers = payload["answers"] as Map<String, Any?>
        val first = answers["q1"] as Map<String, Any?>
        return mapOf("q1" to (first["optionIds"] as List<String>))
    }

    @Suppress("UNCHECKED_CAST")
    private fun customTextOf(payload: Map<String, Any?>): Map<String, Any?> {
        val answers = payload["answers"] as Map<String, Any?>
        val first = answers["q1"] as Map<String, Any?>
        return mapOf("customText" to first["customText"]).filterValues { it != null }
    }

    private fun noticeWithForm(uiSchema: Map<String, Any?>? = INPUT_REQUEST_UI_SCHEMA): RuntimeNotice {
        val action = RuntimeNoticeAction(
            actionId = "answer",
            label = "提交",
            style = "primary",
            input = RuntimeNoticeActionInput(
                required = true,
                schema = mapOf("type" to "object"),
                uiSchema = uiSchema,
            ),
            unknown = emptyMap(),
        )
        return RuntimeNotice(
            noticeId = "n1",
            type = "interaction",
            sessionId = "s1",
            title = "需要你的决定",
            message = "运行时在等你选一个方案。",
            severity = "warning",
            status = "open",
            interactionType = "input_request",
            blocking = null,
            responseRequired = true,
            revision = 1,
            updatedSeq = 1,
            source = emptyMap(),
            actions = listOf(action),
            context = emptyMap(),
            metadata = emptyMap(),
            expiresAt = null,
            createdAt = null,
            updatedAt = null,
            resolvedAt = null,
        )
    }

    private companion object {
        val INPUT_REQUEST_UI_SCHEMA: Map<String, Any?> = mapOf(
            "component" to "inputRequest",
            "version" to 1,
            "questions" to listOf(
                mapOf(
                    "id" to "q1",
                    "prompt" to "用哪个方案？",
                    "header" to "方案",
                    "multiple" to false,
                    "allowCustom" to true,
                    "options" to listOf(
                        mapOf("id" to "a", "label" to "方案 A", "description" to "快"),
                        mapOf("id" to "b", "label" to "方案 B", "description" to "稳"),
                    ),
                ),
            ),
        )
    }
}
