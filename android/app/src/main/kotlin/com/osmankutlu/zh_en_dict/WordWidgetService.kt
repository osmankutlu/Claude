package com.osmankutlu.zh_en_dict

import android.content.Intent
import android.widget.RemoteViewsService

/** Serves the StackView deck's cards for each widget instance. */
class WordWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        WordWidgetFactory(applicationContext, intent)
}
