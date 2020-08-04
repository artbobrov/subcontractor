package slack.ui.create

import com.slack.api.model.kotlin_extension.block.withBlocks
import com.slack.api.model.view.View
import com.slack.api.model.view.Views.*
import core.model.PollOption
import core.model.PollType
import slack.model.SlackChannel
import slack.model.SlackUser
import slack.ui.base.SlackViewUIRepresentable
import java.util.*

class CreatePollView(
    currentPollType: PollType,
    options: List<PollOption>,
    users: List<SlackUser>,
    channels: List<SlackChannel>
) : SlackViewUIRepresentable {
    val id = UUID.randomUUID().toString()

    private val createPollBlockView = CreatePollBlockView(currentPollType, options)
    private val audiencePickerBlockView = CreatePollAudiencePickerBlockView(users, channels)

    override fun representIn(builder: View.ViewBuilder) {
        builder
            .type("modal")
            .title(viewTitle { it.type("plain_text").text(VIEW_TITLE) })
            .close(viewClose { it.type("plain_text").text(VIEW_CLOSE_BUTTON_TITLE) })
            .submit(viewSubmit { it.type("plain_text").text(VIEW_CREATE_BUTTON_TITLE) })
            .callbackId(CreationIDConstants.CREATION_VIEW_SUBMISSION_CALLBACK)
            .id(id)
            .blocks(withBlocks {
                createPollBlockView.representIn(this)
                audiencePickerBlockView.representIn(this)
            })

    }

    companion object {
        const val VIEW_TITLE = "Create poll"
        const val VIEW_CLOSE_BUTTON_TITLE = "Close"
        const val VIEW_CREATE_BUTTON_TITLE = "Create"
    }
}
