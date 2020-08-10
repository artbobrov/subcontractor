package slack.server.webhooks

import com.slack.api.bolt.context.builtin.ViewSubmissionContext
import com.slack.api.bolt.request.builtin.ViewSubmissionRequest
import com.slack.api.model.view.ViewState
import core.model.SingleChoicePoll
import core.model.VoteResults
import core.model.storage.LiquidPollRepository
import slack.model.*
import slack.server.base.SlackViewSubmissionDataFactory
import slack.server.base.SlackViewSubmissionWebhook
import slack.server.base.ViewIdentifiable
import slack.service.SlackPollCreationRepository
import slack.service.SlackRequestProvider
import slack.ui.base.UIConstant
import slack.model.SlackPollMetadata
import slack.ui.poll.CompactPollBlockView
import slack.ui.poll.SingleChoicePollBlockView

class SlackPollCreationViewSubmission(
    provider: SlackRequestProvider,
    private val creationRepository: SlackPollCreationRepository,
    private val liquidPollRepository: LiquidPollRepository
) : SlackViewSubmissionWebhook<CreationViewSubmissionData, SlackPollMetadata>(
    provider,
    CreationViewSubmissionData.Companion,
    SlackPollMetadata::class.java
) {
    override val callbackID: String = UIConstant.CallbackID.CREATION_VIEW_SUBMISSION

    override fun handle(metadata: SlackPollMetadata, content: CreationViewSubmissionData) {
        val builder = creationRepository.get(metadata.pollID) ?: throw IllegalArgumentException()
        val errors = SlackPollBuilderValidator.validate(builder)

        if (errors.isNotEmpty()) {
            val audienceFuture = provider.audienceList()
            audienceFuture.thenAccept { audience ->
                val view = SlackUIFactory.creationView(metadata, builder, audience, errors)
                provider.updateView(view, content.viewID)
            }

            throw SlackError.Multiple(errors)
        }
        val newPoll = builder.apply { question = content.question }.build()

        creationRepository.remove(metadata.pollID)

        liquidPollRepository.put(metadata.pollID, newPoll)

        // TODO: results fetch (business logic + slack api request)
        val results = VoteResults(mapOf())
        val view = SlackUIFactory.createPollBlocks(newPoll, results)

        for (channel in content.selectedChannels) {
            provider.postChatMessage(view, channel.id)
        }

        for (user in content.selectedUsers) {
            provider.postDirectMessage(view, user.id)
        }
    }
}


data class CreationViewSubmissionData(
    override val viewID: String,
    val question: String,
    val selectedChannels: List<SlackChannel>,
    val selectedUsers: List<SlackUser>
) : ViewIdentifiable {
    companion object : SlackViewSubmissionDataFactory<CreationViewSubmissionData> {
        override fun fromRequest(
            request: ViewSubmissionRequest,
            context: ViewSubmissionContext
        ): CreationViewSubmissionData {
            val viewState = request.payload.view.state
            val question = fetchQuestion(viewState)
            val selectedAudience = fetchSelectedAudience(viewState)

            val channels = mutableListOf<SlackChannel>()
            val users = mutableListOf<SlackUser>()
            for (audience in selectedAudience) {
                val matcher = CHANNEL_NAME_PATTERN.matcher(audience.text.text)

                if (matcher.matches()) {
                    channels.add(SlackChannel(audience.value, matcher.group(1)))
                } else {
                    users.add(SlackUser(audience.value, audience.text.text))
                }
            }

            return CreationViewSubmissionData(
                request.payload.view.id,
                question ?: "",
                channels,
                users
            )
        }

        val CHANNEL_NAME_PATTERN = "# (\\w+)".toPattern()

        private fun fetchQuestion(state: ViewState): String? {
            return state.values[UIConstant.BlockID.QUESTION]?.get(UIConstant.ActionID.POLL_QUESTION)?.value
        }

        private fun fetchSelectedAudience(state: ViewState): List<ViewState.SelectedOption> {
            return state.values[UIConstant.BlockID.AUDIENCE]?.get(UIConstant.ActionID.POLL_AUDIENCE)?.selectedOptions
                ?: listOf()
        }
    }
}