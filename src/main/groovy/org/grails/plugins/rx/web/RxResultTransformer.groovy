package org.grails.plugins.rx.web

import grails.artefact.Controller
import grails.async.web.AsyncGrailsWebRequest
import grails.web.UrlConverter
import grails.web.mapping.LinkGenerator
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.grails.plugins.web.async.GrailsAsyncContext
import org.grails.web.errors.GrailsExceptionResolver
import org.grails.web.servlet.mvc.ActionResultTransformer
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.context.request.async.AsyncWebRequest
import org.springframework.web.context.request.async.WebAsyncManager
import org.springframework.web.context.request.async.WebAsyncUtils
import rx.Observable

import javax.servlet.AsyncContext
import javax.servlet.http.HttpServletRequest

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
@Slf4j
class RxResultTransformer implements ActionResultTransformer {

    /**
     * For handling exceptions
     */
    @Autowired(required = false)
    GrailsExceptionResolver exceptionResolver

    @Autowired
    LinkGenerator linkGenerator

    @Autowired(required = false)
    UrlConverter urlConverter

    Object transformActionResult(GrailsWebRequest webRequest, String viewName, Object actionResult, boolean isRender = false) {
        if(actionResult instanceof Observable) {
            // handle RxJava Observables
            Observable observable = (Observable)actionResult

            HttpServletRequest request = webRequest.getCurrentRequest()
            webRequest.setRenderView(false)

            WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request)

            AsyncWebRequest asyncWebRequest = new AsyncGrailsWebRequest(
                    request,
                    webRequest.response,
                    webRequest.servletContext)

            asyncManager.setAsyncWebRequest(asyncWebRequest)

            asyncWebRequest.startAsync()
            request.setAttribute(GrailsApplicationAttributes.ASYNC_STARTED, true)
            GrailsAsyncContext asyncContext = new GrailsAsyncContext(asyncWebRequest.asyncContext, webRequest)

            asyncContext.start {
                RxResultSubscriber subscriber = new RxResultSubscriber(
                        asyncContext,
                        exceptionResolver,
                        linkGenerator,
                        webRequest.controllerClass,
                        (Controller)webRequest.attributes.getController(request)
                )
                subscriber.isRender = isRender
                subscriber.urlConverter = urlConverter
                observable.subscribe(subscriber)
            }
            return null
        }
        return actionResult
    }
}