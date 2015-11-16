package ee.bitweb.grails.springsecurity

import grails.plugin.springsecurity.SpringSecurityUtils
import ee.bitweb.grails.springsecurity.userdetails.GenericUser
import grails.transaction.Transactional
import org.springframework.util.Assert

import javax.servlet.http.HttpServletRequest

import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder as SCH
import org.springframework.util.Assert


class GenericSecurityService {

    /** dependency injection for authenticationTrustResolver */
    def authenticationTrustResolver

    /** dependency injection for grailsApplication */
    def grailsApplication

    /** dependency injection for {@link FilterInvocationSecurityMetadataSource} */
    def objectDefinitionSource

    /** dependency injection for userCache */
    def userCache

    /** dependency injection for userDetailsService */
    def userDetailsService

    /**
     * Get the currently logged in user's principal. If not authenticated and the
     * AnonymousAuthenticationFilter is active (true by default) then the anonymous
     * user's name will be returned ('anonymousUser' unless overridden).
     *
     * @return the principal
     */
    def getPrincipal() { getAuthentication()?.principal }

    /**
     * Get the currently logged in user's <code>Authentication</code>. If not authenticated
     * and the AnonymousAuthenticationFilter is active (true by default) then the anonymous
     * user's auth will be returned (AnonymousAuthenticationToken with username 'anonymousUser'
     * unless overridden).
     *
     * @return the authentication
     */
    Authentication getAuthentication() { SCH.context?.authentication }

    /**
     * Get the domain class instance associated with the current authentication.
     * @return the user
     */
    def getCurrentUser() {
        if (!isLoggedIn()) {
            return null
        }

        def User = getClassForName(securityConfig.userLookup.userDomainClassName)

        if (principal instanceof GenericUser) {
            User.get principal.id
        }
        else {
            return null
            //TODO
            /*User.createCriteria().get {
                eq securityConfig.userLookup.usernamePropertyName, principal.username
                cache true
            }*/
        }
    }

    protected Class<?> getClassForName(String name) {
        securityConfig.useExternalClasses ? Class.forName(name) : grailsApplication.getClassForName(name)
    }

    protected ConfigObject getSecurityConfig() { SpringSecurityUtils.securityConfig }

    protected boolean useRequestmaps() { SpringSecurityUtils.securityConfigType == 'Requestmap' }

    def getCurrentUserId() {
        def principal = getPrincipal()
        principal instanceof GenericUser ? principal.id : null
    }

    /**
     * Get a proxy for the domain class instance associated with the current authentication. Use this when you
     * want the user only for its id, e.g. as a proxy for the foreign key in queries like "CreditCard.findAllByUser(user)"
     *
     * @return the proxy
     */
    def loadCurrentUser() {
        if (!isLoggedIn()) {
            return null
        }

        // load() requires an id, so this only works if there's an id property in the principal
        Assert.isInstanceOf GenericUser, principal

        getClassForName(securityConfig.userLookup.userDomainClassName).load(currentUserId)
    }

    /**
     * Quick check to see if the current user is logged in.
     * @return <code>true</code> if the authenticated and not anonymous
     */
    boolean isLoggedIn() {
        def authentication = SCH.context.authentication
        authentication && !authenticationTrustResolver.isAnonymous(authentication)
    }

    /**
     * Call when editing, creating, or deleting a Requestmap to flush the cached
     * configuration and rebuild using the most recent data.
     */
    void clearCachedRequestmaps() {
        objectDefinitionSource?.reset()
    }

    /**
     * Call for reloading the role hierarchy configuration from the database.
     * @author fpape
     */
    void reloadDBRoleHierarchy() {
        Class roleHierarchyEntryClass = Class.forName(securityConfig.roleHierarchyEntryClassName)
        roleHierarchyEntryClass.withTransaction {
            grailsApplication.mainContext.roleHierarchy.hierarchy = roleHierarchyEntryClass.list()*.entry.join('\n')
        }
    }

    /**
     * Delete a role, and if Requestmap class is used to store roles, remove the role
     * from all Requestmap definitions. If a Requestmap's config attribute is this role,
     * it will be deleted.
     *
     * @param role the role to delete
     */
    @Transactional
    void deleteRole(role) {
        def conf = securityConfig
        String configAttributePropertyName = conf.requestMap.configAttributeField
        String authorityFieldName = conf.authority.nameField

        if (useRequestmaps()) {
            String roleName = role."$authorityFieldName"
            def requestmaps = findRequestmapsByRole(roleName, conf)
            for (rm in requestmaps) {
                String configAttribute = rm."$configAttributePropertyName"
                if (configAttribute.equals(roleName)) {
                    rm.delete(flush: true)
                }
                else {
                    List parts = configAttribute.split(',')*.trim()
                    parts.remove roleName
                    rm."$configAttributePropertyName" = parts.join(',')
                }
            }
            clearCachedRequestmaps()
        }

        // remove the role grant from all users
        getClassForName(conf.userLookup.authorityJoinClassName).removeAll role

        role.delete(flush: true)
    }

    /**
     * Update a role, and if Requestmap class is used to store roles, replace the new role
     * name in all Requestmap definitions that use it if the name was changed.
     *
     * @param role the role to update
     * @param newProperties the new role attributes ('params' from the calling controller)
     */
    @Transactional
    boolean updateRole(role, newProperties) {

        def conf = securityConfig
        String configAttributePropertyName = conf.requestMap.configAttributeField
        String authorityFieldName = conf.authority.nameField

        String oldRoleName = role."$authorityFieldName"
        role.properties = newProperties

        role.save()
        if (role.hasErrors()) {
            return false
        }

        if (useRequestmaps()) {
            String newRoleName = role."$authorityFieldName"
            if (newRoleName != oldRoleName) {
                def requestmaps = findRequestmapsByRole(oldRoleName, conf)
                for (rm in requestmaps) {
                    rm."$configAttributePropertyName" = rm."$configAttributePropertyName".replace(oldRoleName, newRoleName)
                }
            }
            clearCachedRequestmaps()
        }

        true
    }

    /**
     * Rebuild an Authentication for the given username and register it in the security context.
     * Typically used after updating a user's authorities or other auth-cached info.
     * <p/>
     * Also removes the user from the user cache to force a refresh at next login.
     *
     * @param username the user's login name
     * @param password optional
     */
    void reauthenticate(String username, String password = null) {
        //SpringSecurityUtils.reauthenticate username, password
    }

    /**
     * Check if the request was triggered by an Ajax call.
     * @param request the request
     * @return <code>true</code> if Ajax
     */
    boolean isAjax(HttpServletRequest request) {
        SpringSecurityUtils.isAjax request
    }

    /**
     * Create multiple requestmap instances in a transaction.
     * @param data
     *           a list of maps where each map contains the data for one instance
     *           (configAttribute and url are required, httpMethod is optional)
     */
    @Transactional
    void createRequestMaps(List<Map<String, Object>> data) {
        def requestmapClass = grailsApplication.getClassForName(conf.requestMap.className)
        for (Map<String, Object> instanceData in data) {
            requestmapClass.newInstance(instanceData).save(failOnError: true)
        }
    }

    /**
     * Create multiple requestmap instances in a transaction that all share the same <code>configAttribute</code>.
     * @param urls a list of url patterns
     */
    @Transactional
    void createRequestMaps(List<String> urls, String configAttribute) {
        def requestmapClass = grailsApplication.getClassForName(conf.requestMap.className)
        String configAttributePropertyName = conf.requestMap.configAttributeField
        String urlPropertyName = conf.requestMap.urlField
        for (String url in urls) {
            requestmapClass.newInstance((urlPropertyName): url, (configAttributePropertyName): configAttribute).save(failOnError: true)
        }
    }

    protected List findRequestmapsByRole(String roleName, conf) {
        getClassForName(conf.requestMap.className).withCriteria {
            like conf.requestMap.configAttributeField, "%$roleName%"
        }
    }
}
