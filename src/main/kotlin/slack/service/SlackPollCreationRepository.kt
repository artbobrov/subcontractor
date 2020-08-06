package slack.service

import core.model.base.Poll

interface SlackPollCreationRepository {
    fun put(viewId: String, poll: Poll)

    fun get(viewId: String): Poll?

    fun remove(viewId: String)
}
