package kara.internal

import javax.servlet.http.*
import java.io.IOException
import kara.*
import javax.servlet.ServletConfig
import kotlin.properties.Delegates
import org.apache.log4j.Logger

open class Servlet() : HttpServlet() {
    val logger = Logger.getLogger(javaClass<Servlet>())!!

    val application: Application by Delegates.blockingLazy {
        val servletContext = getServletContext()!!
        val config: ApplicationConfig = ApplicationConfig.loadFrom(servletContext.getInitParameter("kara.config") ?: error("kara.config context parameter is required."))

        for (name in servletContext.getInitParameterNames()) {
            config[name] = servletContext.getInitParameter(name)!!
        }

        ApplicationLoader(config).load()
    }


    override fun init() {
        super<HttpServlet>.init()
        application.config // Just to make sure application is eagerly loaded when servlet is initialized.
    }

    public override fun destroy() {
        application.shutDown()
    }

    protected override fun service(req: HttpServletRequest?, resp: HttpServletResponse?) {
        dispatch(req!!, resp!!)
    }

    private fun dispatch(req: HttpServletRequest, resp: HttpServletResponse) {
        resp.setCharacterEncoding("UTF-8")
        req.setCharacterEncoding("UTF-8")

        try {
            val query = req.getQueryString()
            if (!application.context.dispatch(req, resp)) {
                logger.trace("${req.getMethod()} -- ${req.getRequestURL()}${if (query != null) "?" + query else ""} -- FAILED")
                resp.sendError(HttpServletResponse.SC_NOT_FOUND)
            } else {
                logger.trace("${req.getMethod()} -- ${req.getRequestURL()}${if (query != null) "?" + query else ""} -- OK")
            }
        }
        catch (ex: Throwable) {
            logger.error("Error processing request: ${req.getRequestURI()}", ex)
            if (!resp.isCommitted()) {
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex.getMessage())
            }
        }
    }

}
