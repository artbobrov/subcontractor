package core.model

import core.model.base.OptionID
import core.model.base.PollID
import core.model.base.UserID

sealed class VoteWork {
    class Vote(val pollID: PollID, val optionId: OptionID) : VoteWork()

    class Delegate(val voterId: UserID) : VoteWork()
}
