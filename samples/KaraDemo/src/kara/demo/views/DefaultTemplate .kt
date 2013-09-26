package kara.demo.views

import kara.*
import kara.demo.styles.DefaultStyles
import kotlin.html.*

class DefaultTemplate : HtmlTemplate<DefaultTemplate, HTML>() {
    val content = Placeholder<BODY>()
    override fun HTML.render() {
        head {
            title("Kara Demo Title")
            stylesheet(DefaultStyles)
        }
        body {
            h1 { +"Kara Demo Site" }
            div(id = "main") {
                insert(content)
            }
            +"Kara is devloped by: "
            a {
                text = "Tiny Mission"
                href = "http://tinymission.com".link()
            }
            +" and "
            a {
                text = "JetBrains"
                href = "http://jetbrains.com".link()
            }
        }
    }
}
