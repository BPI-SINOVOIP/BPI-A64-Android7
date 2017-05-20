//
//  ========================================================================
//  Copyright (c) 1995-2014 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.security;

import java.io.IOException;
import java.security.Principal;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

import org.eclipse.jetty.security.authentication.DeferredAuthentication;
import org.eclipse.jetty.server.AbstractHttpConnection;
import org.eclipse.jetty.server.Authentication;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.UserIdentity;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandler.Context;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.server.session.AbstractSessionManager;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

/**
 * Abstract SecurityHandler.
 * Select and apply an {@link Authenticator} to a request.
 * <p>
 * The Authenticator may either be directly set on the handler
 * or will be create during {@link #start()} with a call to
 * either the default or set AuthenticatorFactory.
 * <p>
 * SecurityHandler has a set of initparameters that are used by the 
 * Authentication.Configuration. At startup, any context init parameters
 * that start with "org.eclipse.jetty.security." that do not have 
 * values in the SecurityHandler init parameters, are copied.  
 * 
 */
public abstract class SecurityHandler extends HandlerWrapper implements Authenticator.AuthConfiguration
{
    private static final Logger LOG = Log.getLogger(SecurityHandler.class);

    /* ------------------------------------------------------------ */
    private boolean _checkWelcomeFiles = false;
    private Authenticator _authenticator;
    private Authenticator.Factory _authenticatorFactory=new DefaultAuthenticatorFactory();
    private String _realmName;
    private String _authMethod;
    private final Map<String,String> _initParameters=new HashMap<String,String>();
    private LoginService _loginService;
    private boolean _loginServiceShared;
    private IdentityService _identityService;
    private boolean _renewSession=true;

    /* ------------------------------------------------------------ */
    protected SecurityHandler()
    {
    }
    
    /* ------------------------------------------------------------ */
    /** Get the identityService.
     * @return the identityService
     */
    public IdentityService getIdentityService()
    {
        return _identityService;
    }

    /* ------------------------------------------------------------ */
    /** Set the identityService.
     * @param identityService the identityService to set
     */
    public void setIdentityService(IdentityService identityService)
    {
        if (isStarted())
            throw new IllegalStateException("Started");
        _identityService = identityService;
    }

    /* ------------------------------------------------------------ */
    /** Get the loginService.
     * @return the loginService
     */
    public LoginService getLoginService()
    {
        return _loginService;
    }

    /* ------------------------------------------------------------ */
    /** Set the loginService.
     * @param loginService the loginService to set
     */
    public void setLoginService(LoginService loginService)
    {
        if (isStarted())
            throw new IllegalStateException("Started");
        _loginService = loginService;
        _loginServiceShared=false;
    }


    /* ------------------------------------------------------------ */
    public Authenticator getAuthenticator()
    {
        return _authenticator;
    }

    /* ------------------------------------------------------------ */
    /** Set the authenticator.
     * @param authenticator
     * @throws IllegalStateException if the SecurityHandler is running
     */
    public void setAuthenticator(Authenticator authenticator)
    {
        if (isStarted())
            throw new IllegalStateException("Started");
        _authenticator = authenticator;
    }

    /* ------------------------------------------------------------ */
    /**
     * @return the authenticatorFactory
     */
    public Authenticator.Factory getAuthenticatorFactory()
    {
        return _authenticatorFactory;
    }

    /* ------------------------------------------------------------ */
    /**
     * @param authenticatorFactory the authenticatorFactory to set
     * @throws IllegalStateException if the SecurityHandler is running
     */
    public void setAuthenticatorFactory(Authenticator.Factory authenticatorFactory)
    {
        if (isRunning())
            throw new IllegalStateException("running");
        _authenticatorFactory = authenticatorFactory;
    }

    /* ------------------------------------------------------------ */
    /**
     * @return the realmName
     */
    public String getRealmName()
    {
        return _realmName;
    }

    /* ------------------------------------------------------------ */
    /**
     * @param realmName the realmName to set
     * @throws IllegalStateException if the SecurityHandler is running
     */
    public void setRealmName(String realmName)
    {
        if (isRunning())
            throw new IllegalStateException("running");
        _realmName = realmName;
    }

    /* ------------------------------------------------------------ */
    /**
     * @return the authMethod
     */
    public String getAuthMethod()
    {
        return _authMethod;
    }

    /* ------------------------------------------------------------ */
    /**
     * @param authMethod the authMethod to set
     * @throws IllegalStateException if the SecurityHandler is running
     */
    public void setAuthMethod(String authMethod)
    {
        if (isRunning())
            throw new IllegalStateException("running");
        _authMethod = authMethod;
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @return True if forwards to welcome files are authenticated
     */
    public boolean isCheckWelcomeFiles()
    {
        return _checkWelcomeFiles;
    }

    /* ------------------------------------------------------------ */
    /**
     * @param authenticateWelcomeFiles True if forwards to welcome files are
     *                authenticated
     * @throws IllegalStateException if the SecurityHandler is running
     */
    public void setCheckWelcomeFiles(boolean authenticateWelcomeFiles)
    {
        if (isRunning())
            throw new IllegalStateException("running");
        _checkWelcomeFiles = authenticateWelcomeFiles;
    }

    /* ------------------------------------------------------------ */
    public String getInitParameter(String key)
    {
        return _initParameters.get(key);
    }
    
    /* ------------------------------------------------------------ */
    public Set<String> getInitParameterNames()
    {
        return _initParameters.keySet();
    }
    
    /* ------------------------------------------------------------ */
    /** Set an initialization parameter.
     * @param key
     * @param value
     * @return previous value
     * @throws IllegalStateException if the SecurityHandler is running
     */
    public String setInitParameter(String key, String value)
    {
        if (isRunning())
            throw new IllegalStateException("running");
        return _initParameters.put(key,value);
    }
    
    /* ------------------------------------------------------------ */
    protected LoginService findLoginService()
    {
        List<LoginService> list = getServer().getBeans(LoginService.class);
        
        String realm=getRealmName();
        if (realm!=null)
        {
            for (LoginService service : list)
                if (service.getName()!=null && service.getName().equals(realm))
                    return service;
        }
        else if (list.size()==1)
            return list.get(0);
        return null;
    }
    
    /* ------------------------------------------------------------ */
    protected IdentityService findIdentityService()
    {
        return getServer().getBean(IdentityService.class);
    }
    
    /* ------------------------------------------------------------ */
    /** 
     */
    @Override
    protected void doStart()
        throws Exception
    {
        // copy security init parameters
        ContextHandler.Context context =ContextHandler.getCurrentContext();
        if (context!=null)
        {
            Enumeration<String> names=context.getInitParameterNames();
            while (names!=null && names.hasMoreElements())
            {
                String name =names.nextElement();
                if (name.startsWith("org.eclipse.jetty.security.") &&
                        getInitParameter(name)==null)
                    setInitParameter(name,context.getInitParameter(name));
            }
            
            //register a session listener to handle securing sessions when authentication is performed
            context.getContextHandler().addEventListener(new HttpSessionListener()
            {
                
                public void sessionDestroyed(HttpSessionEvent se)
                {
                   
                }
                
                public void sessionCreated(HttpSessionEvent se)
                {
                    //if current request is authenticated, then as we have just created the session, mark it as secure, as it has not yet been returned to a user
                    AbstractHttpConnection connection = AbstractHttpConnection.getCurrentConnection();
                    if (connection == null)
                        return;
                    Request request = connection.getRequest();
                    if (request == null)
                        return;
                    
                    if (request.isSecure())
                    {
                        se.getSession().setAttribute(AbstractSessionManager.SESSION_KNOWN_ONLY_TO_AUTHENTICATED, Boolean.TRUE);
                    }
                }
            });
        }
        
        // complicated resolution of login and identity service to handle
        // many different ways these can be constructed and injected.
        
        if (_loginService==null)
        {
            _loginService=findLoginService();
            if (_loginService!=null)
                _loginServiceShared=true;
        }
        
        if (_identityService==null)
        {
           
            if (_loginService!=null)
                _identityService=_loginService.getIdentityService();

            if (_identityService==null)
                _identityService=findIdentityService();
            
            if (_identityService==null && _realmName!=null)
                _identityService=new DefaultIdentityService();
        }
        
        if (_loginService!=null)
        {
            if (_loginService.getIdentityService()==null)
                _loginService.setIdentityService(_identityService);
            else if (_loginService.getIdentityService()!=_identityService)
                throw new IllegalStateException("LoginService has different IdentityService to "+this);
        }

        if (!_loginServiceShared && _loginService instanceof LifeCycle)
            ((LifeCycle)_loginService).start();        
        
        if (_authenticator==null && _authenticatorFactory!=null && _identityService!=null)
        {
            _authenticator=_authenticatorFactory.getAuthenticator(getServer(),ContextHandler.getCurrentContext(),this, _identityService, _loginService);
            if (_authenticator!=null)
                _authMethod=_authenticator.getAuthMethod();
        }

        if (_authenticator==null)
        {
            if (_realmName!=null)
            {
                LOG.warn("No ServerAuthentication for "+this);
                throw new IllegalStateException("No ServerAuthentication");
            }
        }
        else
        {
            _authenticator.setConfiguration(this);
            if (_authenticator instanceof LifeCycle)
                ((LifeCycle)_authenticator).start();
        }

        super.doStart();
    }

    /* ------------------------------------------------------------ */
    /**
     * @see org.eclipse.jetty.server.handler.HandlerWrapper#doStop()
     */
    @Override
    protected void doStop() throws Exception
    {
        super.doStop();
        
        if (!_loginServiceShared && _loginService instanceof LifeCycle)
            ((LifeCycle)_loginService).stop();
        
    }

    /* ------------------------------------------------------------ */
    protected boolean checkSecurity(Request request)
    {
        switch(request.getDispatcherType())
        {
            case REQUEST:
            case ASYNC:
                return true;
            case FORWARD:
                if (_checkWelcomeFiles && request.getAttribute("org.eclipse.jetty.server.welcome") != null)
                {
                    request.removeAttribute("org.eclipse.jetty.server.welcome");
                    return true;
                }
                return false;
            default:
                return false;
        }
    }
    
    /* ------------------------------------------------------------ */
    /**
     * @see org.eclipse.jetty.security.Authenticator.AuthConfiguration#isSessionRenewedOnAuthentication()
     */
    public boolean isSessionRenewedOnAuthentication()
    {
        return _renewSession;
    }
    
    /* ------------------------------------------------------------ */
    /** Set renew the session on Authentication.
     * <p>
     * If set to true, then on authentication, the session associated with a reqeuest is invalidated and replaced with a new session.
     * @see org.eclipse.jetty.security.Authenticator.AuthConfiguration#isSessionRenewedOnAuthentication()
     */
    public void setSessionRenewedOnAuthentication(boolean renew)
    {
        _renewSession=renew;
    }
    
    /* ------------------------------------------------------------ */
    /*
     * @see org.eclipse.jetty.server.Handler#handle(java.lang.String,
     *      javax.servlet.http.HttpServletRequest,
     *      javax.servlet.http.HttpServletResponse, int)
     */
    @Override
    public void handle(String pathInContext, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
    {
        final Response base_response = baseRequest.getResponse();
        final Handler handler=getHandler();
        
        if (handler==null)
            return;

        final Authenticator authenticator = _authenticator;
        
        if (checkSecurity(baseRequest))
        {
            Object constraintInfo = prepareConstraintInfo(pathInContext, baseRequest);
            
            // Check data constraints
            if (!checkUserDataPermissions(pathInContext, baseRequest, base_response, constraintInfo))
            {
                if (!baseRequest.isHandled())
                {
                    response.sendError(Response.SC_FORBIDDEN);
                    baseRequest.setHandled(true);
                }
                return;
            }

            // is Auth mandatory?
            boolean isAuthMandatory = 
                isAuthMandatory(baseRequest, base_response, constraintInfo);

            if (isAuthMandatory && authenticator==null)
            {
                LOG.warn("No authenticator for: "+constraintInfo);
                if (!baseRequest.isHandled())
                {
                    response.sendError(Response.SC_FORBIDDEN);
                    baseRequest.setHandled(true);
                }
                return;
            }
            
            // check authentication
            Object previousIdentity = null;
            try
            {
                Authentication authentication = baseRequest.getAuthentication();
                if (authentication==null || authentication==Authentication.NOT_CHECKED)
                    authentication=authenticator==null?Authentication.UNAUTHENTICATED:authenticator.validateRequest(request, response, isAuthMandatory);

                if (authentication instanceof Authentication.Wrapped)
                {
                    request=((Authentication.Wrapped)authentication).getHttpServletRequest();
                    response=((Authentication.Wrapped)authentication).getHttpServletResponse();
                }

                if (authentication instanceof Authentication.ResponseSent)
                {
                    baseRequest.setHandled(true);
                }
                else if (authentication instanceof Authentication.User)
                {
                    Authentication.User userAuth = (Authentication.User)authentication;
                    baseRequest.setAuthentication(authentication);
                    if (_identityService!=null)
                        previousIdentity = _identityService.associate(userAuth.getUserIdentity());

                    if (isAuthMandatory)
                    {
                        boolean authorized=checkWebResourcePermissions(pathInContext, baseRequest, base_response, constraintInfo, userAuth.getUserIdentity());
                        if (!authorized)
                        {
                            response.sendError(Response.SC_FORBIDDEN, "!role");
                            baseRequest.setHandled(true);
                            return;
                        }
                    }
                         
                    handler.handle(pathInContext, baseRequest, request, response);
                    if (authenticator!=null)
                        authenticator.secureResponse(request, response, isAuthMandatory, userAuth);
                }
                else if (authentication instanceof Authentication.Deferred)
                {
                    DeferredAuthentication deferred= (DeferredAuthentication)authentication;
                    baseRequest.setAuthentication(authentication);

                    try
                    {
                        handler.handle(pathInContext, baseRequest, request, response);
                    }
                    finally
                    {
                        previousIdentity = deferred.getPreviousAssociation();
                    }

                    if (authenticator!=null)
                    {
                        Authentication auth=baseRequest.getAuthentication();
                        if (auth instanceof Authentication.User)
                        {
                            Authentication.User userAuth = (Authentication.User)auth;
                            authenticator.secureResponse(request, response, isAuthMandatory, userAuth);
                        }
                        else
                            authenticator.secureResponse(request, response, isAuthMandatory, null);
                    }
                }
                else
                {
                    baseRequest.setAuthentication(authentication);
                    if (_identityService!=null)
                        previousIdentity = _identityService.associate(null);
                    handler.handle(pathInContext, baseRequest, request, response);
                    if (authenticator!=null)
                        authenticator.secureResponse(request, response, isAuthMandatory, null);
                }
            }
            catch (ServerAuthException e)
            {
                // jaspi 3.8.3 send HTTP 500 internal server error, with message
                // from AuthException
                response.sendError(Response.SC_INTERNAL_SERVER_ERROR, e.getMessage());
            }
            finally
            {
                if (_identityService!=null)
                    _identityService.disassociate(previousIdentity);
            }
        }
        else
            handler.handle(pathInContext, baseRequest, request, response);
    }


    /* ------------------------------------------------------------ */
    public static SecurityHandler getCurrentSecurityHandler()
    {
        Context context = ContextHandler.getCurrentContext();
        if (context==null)
            return null;
        
        SecurityHandler security = context.getContextHandler().getChildHandlerByClass(SecurityHandler.class);
        return security;
    }

    /* ------------------------------------------------------------ */
    public void logout(Authentication.User user)
    {
        LOG.debug("logout {}",user);
        LoginService login_service=getLoginService();
        if (login_service!=null)
        {
            login_service.logout(user.getUserIdentity());
        }
        
        IdentityService identity_service=getIdentityService();
        if (identity_service!=null)
        {
            // TODO recover previous from threadlocal (or similar)
            Object previous=null;
            identity_service.disassociate(previous);
        }
    }
    
    /* ------------------------------------------------------------ */
    protected abstract Object prepareConstraintInfo(String pathInContext, Request request);

    /* ------------------------------------------------------------ */
    protected abstract boolean checkUserDataPermissions(String pathInContext, Request request, Response response, Object constraintInfo) throws IOException;

    /* ------------------------------------------------------------ */
    protected abstract boolean isAuthMandatory(Request baseRequest, Response base_response, Object constraintInfo);

    /* ------------------------------------------------------------ */
    protected abstract boolean checkWebResourcePermissions(String pathInContext, Request request, Response response, Object constraintInfo,
                                                           UserIdentity userIdentity) throws IOException;

    
    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    public class NotChecked implements Principal
    {
        public String getName()
        {
            return null;
        }

        @Override
        public String toString()
        {
            return "NOT CHECKED";
        }

        public SecurityHandler getSecurityHandler()
        {
            return SecurityHandler.this;
        }
    }

    
    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    public static Principal __NO_USER = new Principal()
    {
        public String getName()
        {
            return null;
        }

        @Override
        public String toString()
        {
            return "No User";
        }
    };
    
    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    /**
     * Nobody user. The Nobody UserPrincipal is used to indicate a partial state
     * of authentication. A request with a Nobody UserPrincipal will be allowed
     * past all authentication constraints - but will not be considered an
     * authenticated request. It can be used by Authenticators such as
     * FormAuthenticator to allow access to logon and error pages within an
     * authenticated URI tree.
     */
    public static Principal __NOBODY = new Principal()
    {
        public String getName()
        {
            return "Nobody";
        }

        @Override
        public String toString()
        {
            return getName();
        }
    };

}
