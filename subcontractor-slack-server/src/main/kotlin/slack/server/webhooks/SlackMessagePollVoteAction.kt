package slack.server.webhooks

import com.slack.api.bolt.context.builtin.ActionContext
import com.slack.api.bolt.request.builtin.BlockActionRequest
import core.model.*
import core.model.base.ChannelID
import core.model.base.OptionID
import core.model.base.PollID
import core.model.base.UserID
import core.model.storage.LiquidPollRepository
import service.VotingBusinessLogic
import slack.model.*
import slack.server.base.SlackBlockActionDataFactory
import slack.server.base.SlackPatternBlockActionWebhook
import slack.service.SlackRequestProvider
import slack.ui.base.UIConstant
import java.lang.IllegalArgumentException
import java.util.regex.Pattern
import kotlin.math.min
import kotlin.random.Random

class SlackMessagePollVoteAction(
    provider: SlackRequestProvider,
    private val liquidPollRepository: LiquidPollRepository,
    private val businessLogic: VotingBusinessLogic
) : SlackPatternBlockActionWebhook<SlackMessagePollVoteData>(
    provider,
    SlackMessagePollVoteData.Companion
) {
    override val actionID: Pattern = UIConstant.ActionID.VOTE

    override fun handle(content: SlackMessagePollVoteData) {
        businessLogic.vote(content.userID, content.pollID, content.optionID)
        val poll = liquidPollRepository.get(content.pollID) ?: throw IllegalArgumentException()
        // TODO: check
        val voteResults = businessLogic.voteResults(poll.id)
        val compactVoteResults = SlackVoteResultsFactory.compactVoteResults(voteResults)
        val voteInfo = SlackVoteResultsFactory.voteResults(poll, compactVoteResults, provider)

        val blocks = SlackUIFactory.createPollBlocks(poll, voteInfo)
        provider.updateChatMessage(blocks, content.channelID, content.ts)
    }

    companion object {
        // TODO: remove after business logic
        fun dummy(pollID: PollID, options: List<PollOption>, users: Set<SlackUser>): VoteResults {
            var usersSet = users
            val map = mutableMapOf<OptionID, Set<Voter>>()
            for (option in options) {
                val count = min(Random.nextInt(usersSet.size + 1), usersSet.size)
                val usersList = usersSet.toMutableList().apply { shuffle() }

                map[option.id] = usersList.take(count).map { Voter(it.id, VoteWork.Vote(pollID, option.id)) }.toSet()
                usersSet = usersList.drop(count).toSet()
            }
            return VoteResults(map)
        }
    }
}

data class SlackMessagePollVoteData(
    val userID: UserID,
    val pollID: PollID,
    val optionID: OptionID,
    val ts: String,
    val channelID: ChannelID
) {
    companion object : SlackBlockActionDataFactory<SlackMessagePollVoteData> {
        override fun fromRequest(request: BlockActionRequest, context: ActionContext): SlackMessagePollVoteData {
            val userID = request.payload.user.id
            val action = request.payload.actions.first()
            val pattern = UIConstant.ActionID.VOTE.toRegex()
            val matcher = pattern.findAll(action.actionId).first()

            val (_, pollID, optionID) = matcher.groups.toList()
            return SlackMessagePollVoteData(
                userID,
                pollID!!.value,
                optionID!!.value,
                request.payload.message.ts,
                request.payload.channel.id
            )
        }
    }
}
