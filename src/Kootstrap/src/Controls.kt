package kotlin.html.bootstrap

import kotlin.html.*

val form_horizontal = s("form-horizontal")
val form_control = s("form-control")

public fun HtmlBodyTag.blockAction(h: highlight = highlight.default, c: StyleClass? = null, body: A.()->Unit): Unit = a(s("btn btn-block btn-${h.name()}") + c, contents = body)
public fun HtmlBodyTag.action(size: caliber, c: StyleClass? = null, body: A.()->Unit): Unit = a(s("btn") + (if (size.value.length() > 0) s("btn-${size.value}") else null) + c, contents = body)
public fun HtmlBodyTag.action(h: highlight = highlight.default, size: caliber = caliber.default, c: StyleClass? = null, body: A.()->Unit): Unit = action(size, s("btn-${h.name()}") + c, body)
public fun HtmlBodyTag.action(url: Link, h: highlight = highlight.default, size: caliber = caliber.default, c: StyleClass? = null, body: A.()->Unit): Unit = action(h, size, c) { href = url; body() }
public fun HtmlBodyTag.actionGroup(c: StyleClass? = null, body: HtmlBodyTagWithText.()->Unit): Unit = p(c) { body() }
public fun HtmlBodyTag.button(h: highlight, body: BUTTON.()->Unit): Unit = button(s("btn btn-${h.name()}"), contents = body)
public fun HtmlBodyTag.buttonSmall(h: highlight, body: BUTTON.()->Unit): Unit = button(s("btn btn-sm btn-${h.name()}"), contents = body)
public fun HtmlBodyTag.blockButton(h: highlight, body: BUTTON.()->Unit): Unit = button(s("btn btn-block btn-${h.name()}"), contents = body)

public fun HtmlBodyTag.controlWithIcon(iconName: String, body: DIV.()->Unit): Unit = div(s("input-group")) { span(s("input-group-addon")) { icon(iconName) }; body() }
public fun HtmlBodyTag.controlGroup(body: DIV.()->Unit): Unit = div(s("form-group"), contents = body)
public fun HtmlBodyTag.controlLabel(body: LABEL.()->Unit): Unit = div(s("control-label")) { label { body() } }
