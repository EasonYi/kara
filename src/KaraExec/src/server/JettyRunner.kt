package kara.server

import javax.servlet.http.*
import org.eclipse.jetty.server.*
import org.eclipse.jetty.server.handler.*
import org.eclipse.jetty.server.session.*
import org.apache.log4j.Logger
import kara.internal.*
import kara.app.*
import kara.ApplicationConfig
import kara.Application
import java.util.ArrayList

/** A Runnable responsible for managing a Jetty server instance.
 */
public class JettyRunner(val applicationConfig: ApplicationConfig) {
    val logger = Logger.getLogger(this.javaClass)!!
    var server: Server? = null
    val resourceHandlers = ArrayList<ResourceHandler>()

    val application: Application;
    {
        val logger = Logger.getLogger(this.javaClass)!!
        val applicationLoader = ApplicationLoader(applicationConfig)
        applicationLoader.loaded { logger.info("Application ${it.javaClass} loaded into the jetty runner") }
        application = applicationLoader.load()
    }

    inner class Handler() : AbstractHandler() {
        public override fun handle(target: String?, baseRequest: Request?, request: HttpServletRequest?, response: HttpServletResponse?) {
            response!!.setCharacterEncoding("UTF-8")
            val query = request!!.getQueryString()
            val method = request.getMethod()
            try {
                if (application.context.dispatch(request, response)) {
                    baseRequest!!.setHandled(true)
                    logger.info("$method -- ${request.getRequestURL()}${if (query != null) "?" + query else ""} -- OK")
                }
                else {
                    for (resourceHandler in resourceHandlers) {
                        resourceHandler.handle(target, baseRequest, request, response)
                        if (baseRequest!!.isHandled()) {
                            logger.info("$method -- ${request.getRequestURL()}${if (query != null) "?" + query else ""} -- OK @${resourceHandler.getResourceBase()}")
                            break;
                        }
                    }
                }
                if (!baseRequest!!.isHandled()) {
                    logger.info("$method -- ${request.getRequestURL()}${if (query != null) "?" + query else ""} -- FAIL")
                }
            }
            catch(ex: Throwable) {
                logger.warn("dispatch error: ${ex.getMessage()}");
                ex.printStackTrace()
                val out = response.getWriter()
                out?.print(ex.getMessage())
                out?.flush()
            }
        }
    }

    public fun start() {
        logger.info("Starting server...")

        var port: Int
        try {
            port = applicationConfig.port.toInt()
        }
        catch (ex: Exception) {
            throw RuntimeException("${applicationConfig.port} is not a valid port number")
        }
        server = Server(port)

        applicationConfig.publicDirectories.forEach {
            logger.info("Attaching resource handler: ${it}")
            val resourceHandler = ResourceHandler()
            resourceHandler.setDirectoriesListed(false)
            resourceHandler.setResourceBase("./${it}")
            resourceHandler.setWelcomeFiles(array("index.html"))
            resourceHandlers.add(resourceHandler)
        }

        val sessionHandler = SessionHandler()
        val sessionManager = HashSessionManager()
        sessionManager.setStoreDirectory(java.io.File("tmp/sessions"))
        sessionHandler.setSessionManager(sessionManager)
        sessionHandler.setHandler(Handler())
        server?.setHandler(sessionHandler)

        server?.start()
        logger.info("Server running.")
        server?.join()
    }

    public fun stop() {
        if (server != null) {
            server?.stop()
            server = null
        }
    }

    public fun restart() {
        this.stop()
        this.start()
    }

}
