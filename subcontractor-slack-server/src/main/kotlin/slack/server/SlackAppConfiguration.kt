package slack.server

import com.slack.api.bolt.App
import core.model.storage.LiquidPollRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import slack.server.base.RegistrableWebhook
import slack.server.webhooks.*
import slack.service.SlackPollCreationRepository
import slack.service.SlackRequestProvider
import slack.ui.base.UIConstant

@Configuration
open class SlackAppConfiguration(
    provider: SlackRequestProvider,
    creationRepository: SlackPollCreationRepository,
    liquidPollRepository: LiquidPollRepository
) {
    // Poll creation
    val liquidCommand = SlackPollCreationSlashCommand(provider, creationRepository)
    val creationSubmission = SlackPollCreationViewSubmission(provider, creationRepository, liquidPollRepository)
    val editOptionsSubmission = SlackPollEditOptionsViewSubmission(provider, creationRepository)
    val editOptionAction = SlackViewPollSingleChoiceEditOptionAction(provider, creationRepository)
    val editOptionAddOptionAction = SlackViewPollEditOptionAddOptionAction(provider, creationRepository)
    val editOverflowOptionAction = SlackViewPollCreationSingleChoicePollOverflowAction(provider, creationRepository)
    val changeTypeAction = SlackViewPollCreationChangeTypeAction(provider, creationRepository)

    // Advanced Settings
    val anonymousSettingAction = SlackViewPollCreationAnonymousSettingAction(provider, creationRepository)
    val showResponsesSettingAction = SlackViewPollCreationShowResponsesSettingAction(provider, creationRepository)
    val startDateTimePickerSettingAction = SlackViewPollCreationStartDateTimeSettingAction(provider, creationRepository)
    val finishDateTimePickerSettingAction =
        SlackViewPollCreationFinishDateTimeSettingAction(provider, creationRepository)

    // Date/Time/DateTime picker
    val startDatePickerAction = SlackViewPollCreationStartDatePickerAction(provider, creationRepository)
    val startTimePickerAction = SlackViewPollCreationStartTimePickerAction(provider, creationRepository)
    val finishDatePickerAction = SlackViewPollCreationFinishDatePickerAction(provider, creationRepository)
    val finishTimePickerAction = SlackViewPollCreationFinishTimePickerAction(provider, creationRepository)

    // Empty action
    val emptyAction = SlackEmptyAction(UIConstant.ActionID.EMPTY, provider)

    // Voting
    val delegationAction = SlackMessagePollVoteDelegationAction(provider)
    val voteAction = SlackMessagePollVoteAction(provider)

    @Bean
    open fun initSlackApp(): App {
        val app = App()
        val webhooks: List<RegistrableWebhook> = listOf(
            liquidCommand,
            creationSubmission,
            liquidCommand,
            creationSubmission,
            editOptionsSubmission,
            editOptionAction,
            editOptionAddOptionAction,
            editOverflowOptionAction,
            changeTypeAction,
            anonymousSettingAction,
            showResponsesSettingAction,
            startDateTimePickerSettingAction,
            finishDateTimePickerSettingAction,
            startDatePickerAction,
            startTimePickerAction,
            finishDatePickerAction,
            finishTimePickerAction,
            emptyAction,
            delegationAction,
            voteAction
        )

        for (webhook in webhooks) {
            webhook.registerIn(app)
        }
        return app
    }
}