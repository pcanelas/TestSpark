package org.jetbrains.research.testspark.tools.llm.generation

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import org.jetbrains.research.testspark.bundles.TestSparkBundle
import org.jetbrains.research.testspark.core.generation.network.ResponseErrorCode
import org.jetbrains.research.testspark.core.generation.network.LLMResponse
import org.jetbrains.research.testspark.tools.llm.error.LLMErrorManager
import org.jetbrains.research.testspark.tools.llm.generation.openai.ChatMessage



abstract class RequestManager(var token: String) {
    enum class SendResult {
        OK,
        TOO_LONG,
        OTHER,
    }

    // var token: String = SettingsArguments.getToken()
    val chatHistory = mutableListOf<ChatMessage>()

    protected val log: Logger = Logger.getInstance(this.javaClass)

    /**
     * Sends a request to LLM with the given prompt and returns the generated TestSuite.
     *
     * @param prompt the prompt to send to LLM
     * @param indicator the progress indicator to show progress during the request
     * @param packageName the name of the package for the generated TestSuite
     * @param project the project associated with the request
     * @param llmErrorManager the error manager to handle errors during the request
     * @param isUserFeedback indicates if this request is a test generation request or a user feedback
     * @return the generated TestSuite, or null and prompt message
     */
    open fun request(
        prompt: String,
        indicator: ProgressIndicator,
        packageName: String,
        project: Project,
        llmErrorManager: LLMErrorManager,
        isUserFeedback: Boolean = false,
    ): LLMResponse {
        // save the prompt in chat history
        chatHistory.add(ChatMessage("user", prompt))

        // Send Request to LLM
        log.info("Sending Request...")

        val (sendResult, testsAssembler) = send(prompt, indicator, project, llmErrorManager)

        if (sendResult == SendResult.TOO_LONG) {
            return LLMResponse(ResponseErrorCode.PROMPT_TOO_LONG, testsAssembler.rawText, null)
        }

        // we remove the user request because we don't store user's requests in chat history
        if (isUserFeedback) {
            chatHistory.removeLast()
        }

        return when (isUserFeedback) {
            true -> processUserFeedbackResponse(testsAssembler, packageName)
            false -> processResponse(testsAssembler, packageName, project)
        }
    }

    open fun processResponse(
        testsAssembler: TestsAssembler,
        packageName: String,
        project: Project,
    ): LLMResponse {
        // save the full response in the chat history
        val response = testsAssembler.rawText

        log.info("The full response: \n $response")
        chatHistory.add(ChatMessage("assistant", response))

        // check if response is empty
        if (response.isEmpty() || response.isBlank()) {
            return LLMResponse(ResponseErrorCode.EMPTY_LLM_RESPONSE, response, null)
        }

        val testSuiteGeneratedByLLM = testsAssembler.returnTestSuite(packageName)

        return if (testSuiteGeneratedByLLM == null) {
            LLMResponse(ResponseErrorCode.TEST_SUITE_PARSING_FAILURE, response, null)
        }
        else {
            LLMResponse(ResponseErrorCode.OK, response, testSuiteGeneratedByLLM.reformat())
        }
    }

    abstract fun send(
        prompt: String,
        indicator: ProgressIndicator,
        project: Project,
        llmErrorManager: LLMErrorManager,
    ): Pair<SendResult, TestsAssembler>

    open fun processUserFeedbackResponse(
        testsAssembler: TestsAssembler,
        packageName: String,
    ): LLMResponse {
        val response = testsAssembler.rawText
        log.info("The full response: \n $response")

        val testSuiteGeneratedByLLM = testsAssembler.returnTestSuite(packageName)

        return LLMResponse(ResponseErrorCode.OK, response, testSuiteGeneratedByLLM)
    }
}
