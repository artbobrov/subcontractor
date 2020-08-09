package slack.ui.create

import com.google.gson.Gson
import com.slack.api.util.json.GsonFactory
import java.time.format.DateTimeFormatter

object CreationConstant {
    object BlockID {
        const val AUDIENCE = "AUDIENCE_BLOCK_ID"
        const val QUESTION = "QUESTION_BLOCK_ID"
    }

    object CallbackID {
        const val ADD_OPTION_VIEW_SUBMISSION = "ADD_NEW_OPTION_BUTTON"
        const val CREATION_VIEW_SUBMISSION = "CREATION_VIEW_SUBMISSION"
    }

    object ActionID {
        const val OPTION_ACTION_OVERFLOW = "OPTION_ACTION_OVERFLOW"
        const val SINGLE_POLL_EDIT_CHOICE = "SINGLE_POLL_ADD_CHOICE"
        const val POLL_TYPE = "POLL_TYPE"
        const val POLL_AUDIENCE = "POLL_AUDIENCE"
        const val POLL_QUESTION = "POLL_QUESTION"
        const val ADD_NEW_OPTION_BUTTON = "ADD_NEW_OPTION_BUTTON"
        const val START_DATE_PICKER = "START_DATE_PICKER"
        const val FINISH_DATE_PICKER = "FINISH_DATE_PICKER"
        const val START_TIME_PICKER = "START_TIME_PICKER"
        const val FINISH_TIME_PICKER = "FINISH_TIME_PICKER"

        const val ANONYMOUS_TOGGLE = "ANONYMOUS_CHECKBOX"
        const val SHOW_RESPONSES_TOGGLE = "SHOW_RESPONSES_CHECKBOX"
        const val START_DATETIME_TOGGLE = "START_TIME_ENABLE"
        const val FINISH_DATETIME_TOGGLE = "FINISH_TIME_ENABLE"
    }

    val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val GSON: Gson = GsonFactory.createSnakeCase()
}