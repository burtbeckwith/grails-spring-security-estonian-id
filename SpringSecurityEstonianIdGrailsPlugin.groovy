import ee.bitweb.grails.springsecurity.estonianid.MobileIdAuthenticationHandler
import ee.bitweb.grails.springsecurity.estonianid.MobileIdAuthenticationFilter
import ee.bitweb.grails.springsecurity.estonianid.MobileIdAuthenticationService
import grails.plugin.springsecurity.SecurityFilterPosition
import grails.plugin.springsecurity.SpringSecurityUtils

import ee.bitweb.grails.springsecurity.estonianid.IdCardAuthenticationFilter
import ee.bitweb.grails.springsecurity.estonianid.MobileIdAuthenticationFilter
import ee.bitweb.grails.springsecurity.estonianid.IdCardAuthenticationProvider
import ee.bitweb.grails.springsecurity.estonianid.MobileIdAuthenticationProvider
import ee.bitweb.grails.springsecurity.estonianid.DefaultEstonianIdAuthenticationDao

import groovy.util.logging.Log4j

@Log4j
class SpringSecurityEstonianIdGrailsPlugin {
    // the plugin version
    def version = "0.1"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "2.5 > *"
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
        "grails-app/views/error.gsp"
    ]

    // TODO Fill in these fields
    def title = "Spring Security Estonian Id Plugin" // Headline display name of the plugin
    def author = "Your name"
    def authorEmail = ""
    def description = '''\
Brief summary/description of the plugin.
'''

    // URL to the plugin's documentation
    def documentation = "http://grails.org/plugin/spring-security-estonian-id"

    // Extra (optional) plugin metadata

    // License: one of 'APACHE', 'GPL2', 'GPL3'
//    def license = "APACHE"

    // Details of company behind the plugin (if there is one)
//    def organization = [ name: "My Company", url: "http://www.my-company.com/" ]

    // Any additional developers beyond the author specified above.
//    def developers = [ [ name: "Joe Bloggs", email: "joe@bloggs.net" ]]

    // Location of the plugin's issue tracker.
//    def issueManagement = [ system: "JIRA", url: "http://jira.grails.org/browse/GPMYPLUGIN" ]

    // Online location of the plugin's browseable source code.
//    def scm = [ url: "http://svn.codehaus.org/grails-plugins/" ]

    def loadAfter = ['springSecurityCore']

    def doWithWebDescriptor = { xml ->
        // TODO Implement additions to web.xml (optional), this event occurs before
    }

    def doWithSpring = {
        def conf = SpringSecurityUtils.securityConfig
        if (!conf) {
            println 'ERROR: There is no Spring Security configuration'
            println 'ERROR: Stop configuring Spring Security Estonian Id'
            return
        }

        println 'Configuring Spring Security EstonianId ...'
        SpringSecurityUtils.loadSecondaryConfig 'DefaultEstonianIdSecurityConfig'
        // have to get again after overlaying DefaultEstonianIdSecurityConfig
        conf = SpringSecurityUtils.securityConfig

        String estonianIdDaoName = conf?.estonianId?.dao ?: null

        if (estonianIdDaoName == null) {
            estonianIdDaoName = 'estonianIdAuthenticationDao'
            estonianIdAuthenticationDao(DefaultEstonianIdAuthenticationDao) {
                estonianIdUserClassName = null//conf.estonianId.domain.classname
                appUserConnectionPropertyName = null//conf.estonianId.domain.connectionPropertyName

                appUserClassName = null//conf.userLookup.userDomainClassName
                rolesPropertyName = null//conf.userLookup.authoritiesPropertyName

                coreUserDetailsService = ref('userDetailsService')
                grailsApplication = ref('grailsApplication')

                //defaultRoleNames = _roles
            }
        }

        estonianMobileIdAuthenticationService(MobileIdAuthenticationService) {

        }

        estonianMobileIdAuthenticationHandler(MobileIdAuthenticationHandler) {

        }

        estonianIdCardAuthenticationProvider(IdCardAuthenticationProvider) {
            //estonianIdCardAuthenticationDao = ref(estonianIdDaoName)
        }
        estonianMobileIdAuthenticationProvider(MobileIdAuthenticationProvider) {
            authenticationService = ref('estonianMobileIdAuthenticationService')
            authenticationDao = ref(estonianIdDaoName)
        }

        estonianIdCardAuthenticationFilter(IdCardAuthenticationFilter) {
            filterProcessesUrl = 'j_spring_security_estonianid_idcard_check'
            authenticationSuccessHandler = ref('authenticationSuccessHandler')
            authenticationFailureHandler = ref('estonianMobileIdAuthenticationHandler')
            authenticationManager = ref('authenticationManager')
            sessionAuthenticationStrategy = ref('sessionAuthenticationStrategy')
        }

        estonianMobileIdAuthenticationFilter(MobileIdAuthenticationFilter) {
            filterProcessesUrl = 'j_spring_security_estonianid_mobileid_check'
            authenticationSuccessHandler = ref('authenticationSuccessHandler')
            authenticationFailureHandler = ref('estonianMobileIdAuthenticationHandler')
            authenticationManager = ref('authenticationManager')
            sessionAuthenticationStrategy = ref('sessionAuthenticationStrategy')
        }

        SpringSecurityUtils.registerProvider('estonianIdCardAuthenticationProvider')
        SpringSecurityUtils.registerProvider('estonianMobileIdAuthenticationProvider')
        SpringSecurityUtils.registerFilter('estonianIdCardAuthenticationFilter', SecurityFilterPosition.SECURITY_CONTEXT_FILTER.order + 10)
        SpringSecurityUtils.registerFilter('estonianMobileIdAuthenticationFilter', SecurityFilterPosition.SECURITY_CONTEXT_FILTER.order + 11)

        println '... finished configuring Spring Security EstonianId'
    }

    def doWithDynamicMethods = { ctx ->
        // TODO Implement registering dynamic methods to classes (optional)
    }

    def doWithApplicationContext = { ctx ->
        // TODO Implement post initialization spring config (optional)
    }

    def onChange = { event ->
        // TODO Implement code that is executed when any artefact that this plugin is
        // watching is modified and reloaded. The event contains: event.source,
        // event.application, event.manager, event.ctx, and event.plugin.
    }

    def onConfigChange = { event ->
        // TODO Implement code that is executed when the project configuration changes.
        // The event is the same as for 'onChange'.
    }

    def onShutdown = { event ->
        // TODO Implement code that is executed when the application shuts down (optional)
    }
}
