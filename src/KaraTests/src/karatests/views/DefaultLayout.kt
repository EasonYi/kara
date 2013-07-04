package karatests.views

import kara.*
import kotlin.html.*

/** A default layout implementation.
 */
class DefaultLayout() : HtmlLayout() {
    override fun HTML.render(context: ActionContext, mainView: HtmlView) {
        head {
            title {+"This is the default layout"}
        }
        body {
            h1 {+"Default Layout"}
            renderView(context, mainView)
        }
    }
}
