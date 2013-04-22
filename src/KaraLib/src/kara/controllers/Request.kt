package kara

import java.net.URL
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpServletRequest
import java.lang.reflect.Modifier
import kara.internal.*
import java.util.HashSet
import java.util.LinkedHashSet

public open class Request(private val handler: ActionContext.() -> ActionResult) : Link {
    fun handle(context : ActionContext) : ActionResult {
        return context.handler()
    }

    override fun href() = toExternalForm()

    public fun toExternalForm() : String {
        val (route, method) = javaClass.route()

        val answer = StringBuilder()

        val properties = LinkedHashSet(properties())

        answer.append(route.routeComps().map({
            when (it) {
                is StringRouteComp -> (it as RouteComp).compString
                is ParamRouteComp -> {
                    properties.remove(it.name)

                    // TODO: introduce serializers similar to deserializers in appconfig
                    "${propertyValue(it.name)}"
                }
                is WildcardRouteComp -> throw RuntimeException("Routes with wildcards aren't supported")
                else -> throw RuntimeException("Unknown route component $it of class ${it.javaClass.getName()}")
            }
        }).join("/"))

        if (answer.length() == 0) answer.append("/")

        val nonEmptyProperties = properties filter { propertyValue(it) != null }
        if (nonEmptyProperties.count() > 0) {
            answer.append("?")
            answer.append(nonEmptyProperties map { "$it=${propertyValue(it)}" } join("&"))
        }

        return answer.toString()
    }
}
