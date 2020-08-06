package slack.service

import core.model.base.Poll

class SlackPollCreationRepositoryImpl : SlackPollCreationRepository {
    private val storage: MutableMap<String, Poll> = mutableMapOf()

    override fun put(viewId: String, poll: Poll) {
        storage[viewId] = poll
    }

    override fun get(viewId: String): Poll? {
        return storage[viewId]
    }

    override fun remove(viewId: String) {
        storage.remove(viewId)
    }
}
