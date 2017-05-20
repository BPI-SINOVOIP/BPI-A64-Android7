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

package org.eclipse.jetty.security.authentication;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.BitSet;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpHeaders;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.security.ServerAuthException;
import org.eclipse.jetty.security.UserAuthentication;
import org.eclipse.jetty.server.Authentication;
import org.eclipse.jetty.server.Authentication.User;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.UserIdentity;
import org.eclipse.jetty.util.B64Code;
import org.eclipse.jetty.util.QuotedStringTokenizer;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.TypeUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.security.Credential;

/**
 * @version $Rev: 4793 $ $Date: 2009-03-19 00:00:01 +0100 (Thu, 19 Mar 2009) $
 * 
 * The nonce max age in ms can be set with the {@link SecurityHandler#setInitParameter(String, String)} 
 * using the name "maxNonceAge"
 */
public class DigestAuthenticator extends LoginAuthenticator
{
    private static final Logger LOG = Log.getLogger(DigestAuthenticator.class);
    SecureRandom _random = new SecureRandom();
    private long _maxNonceAgeMs = 60*1000;
    private int _maxNC=1024;
    private ConcurrentMap<String, Nonce> _nonceMap = new ConcurrentHashMap<String, Nonce>();
    private Queue<Nonce> _nonceQueue = new ConcurrentLinkedQueue<Nonce>();
    private static class Nonce
    {
        final String _nonce;
        final long _ts;
        final BitSet _seen; 

        public Nonce(String nonce, long ts, int size)
        {
            _nonce=nonce;
            _ts=ts;
            _seen = new BitSet(size);
        }

        public boolean seen(int count)
        {
            synchronized (this)
            {
                if (count>=_seen.size())
                    return true;
                boolean s=_seen.get(count);
                _seen.set(count);
                return s;
            }
        }
    }

    /* ------------------------------------------------------------ */
    public DigestAuthenticator()
    {
        super();
    }

    /* ------------------------------------------------------------ */
    /**
     * @see org.eclipse.jetty.security.authentication.LoginAuthenticator#setConfiguration(org.eclipse.jetty.security.Authenticator.AuthConfiguration)
     */
    @Override
    public void setConfiguration(AuthConfiguration configuration)
    {
        super.setConfiguration(configuration);
        
        String mna=configuration.getInitParameter("maxNonceAge");
        if (mna!=null)
        {
            _maxNonceAgeMs=Long.valueOf(mna);
        }
    }

   
    /* ------------------------------------------------------------ */
    public int getMaxNonceCount()
    {
        return _maxNC;
    }

    /* ------------------------------------------------------------ */
    public void setMaxNonceCount(int maxNC)
    {
        _maxNC = maxNC;
    }
    
    /* ------------------------------------------------------------ */
    public void setMaxNonceAge(long maxNonceAgeInMillis)
    {
        _maxNonceAgeMs = maxNonceAgeInMillis;
    }

    /* ------------------------------------------------------------ */
    public long getMaxNonceAge()
    {
        return _maxNonceAgeMs;
    }

    /* ------------------------------------------------------------ */
    public String getAuthMethod()
    {
        return Constraint.__DIGEST_AUTH;
    }

    /* ------------------------------------------------------------ */
    public boolean secureResponse(ServletRequest req, ServletResponse res, boolean mandatory, User validatedUser) throws ServerAuthException
    {
        return true;
    }
    


    /* ------------------------------------------------------------ */
    public Authentication validateRequest(ServletRequest req, ServletResponse res, boolean mandatory) throws ServerAuthException
    {
        if (!mandatory)
            return new DeferredAuthentication(this);

        HttpServletRequest request = (HttpServletRequest)req;
        HttpServletResponse response = (HttpServletResponse)res;
        String credentials = request.getHeader(HttpHeaders.AUTHORIZATION);

        try
        {
            boolean stale = false;
            if (credentials != null)
            {
                if (LOG.isDebugEnabled()) 
                    LOG.debug("Credentials: " + credentials);
                QuotedStringTokenizer tokenizer = new QuotedStringTokenizer(credentials, "=, ", true, false);
                final Digest digest = new Digest(request.getMethod());
                String last = null;
                String name = null;

                while (tokenizer.hasMoreTokens())
                {
                    String tok = tokenizer.nextToken();
                    char c = (tok.length() == 1) ? tok.charAt(0) : '\0';

                    switch (c)
                    {
                        case '=':
                            name = last;
                            last = tok;
                            break;
                        case ',':
                            name = null;
                            break;
                        case ' ':
                            break;

                        default:
                            last = tok;
                            if (name != null)
                            {
                                if ("username".equalsIgnoreCase(name))
                                    digest.username = tok;
                                else if ("realm".equalsIgnoreCase(name))
                                    digest.realm = tok;
                                else if ("nonce".equalsIgnoreCase(name))
                                    digest.nonce = tok;
                                else if ("nc".equalsIgnoreCase(name))
                                    digest.nc = tok;
                                else if ("cnonce".equalsIgnoreCase(name))
                                    digest.cnonce = tok;
                                else if ("qop".equalsIgnoreCase(name))
                                    digest.qop = tok;
                                else if ("uri".equalsIgnoreCase(name))
                                    digest.uri = tok;
                                else if ("response".equalsIgnoreCase(name)) 
                                    digest.response = tok;
                                name=null;
                            }
                    }
                }

                int n = checkNonce(digest,(Request)request);

                if (n > 0)
                {
                    //UserIdentity user = _loginService.login(digest.username,digest);
                    UserIdentity user = login(digest.username, digest, req);
                    if (user!=null)
                    {
                        return new UserAuthentication(getAuthMethod(),user);
                    }
                }
                else if (n == 0) 
                    stale = true;

            }

            if (!DeferredAuthentication.isDeferred(response))
            {
                String domain = request.getContextPath();
                if (domain == null) 
                    domain = "/";
                response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Digest realm=\"" + _loginService.getName()
                        + "\", domain=\""
                        + domain
                        + "\", nonce=\""
                        + newNonce((Request)request)
                        + "\", algorithm=MD5, qop=\"auth\","
                        + " stale=" + stale);
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);

                return Authentication.SEND_CONTINUE;
            }

            return Authentication.UNAUTHENTICATED;
        }
        catch (IOException e)
        {
            throw new ServerAuthException(e);
        }

    }

    /* ------------------------------------------------------------ */
    public String newNonce(Request request)
    {
        Nonce nonce;
        
        do
        {
            byte[] nounce = new byte[24];
            _random.nextBytes(nounce);

            nonce = new Nonce(new String(B64Code.encode(nounce)),request.getTimeStamp(),_maxNC);
        }
        while (_nonceMap.putIfAbsent(nonce._nonce,nonce)!=null);
        _nonceQueue.add(nonce);
               
        return nonce._nonce;
    }

    /**
     * @param nstring nonce to check
     * @param request
     * @return -1 for a bad nonce, 0 for a stale none, 1 for a good nonce
     */
    /* ------------------------------------------------------------ */
    private int checkNonce(Digest digest, Request request)
    {
        // firstly let's expire old nonces
        long expired = request.getTimeStamp()-_maxNonceAgeMs;
        Nonce nonce=_nonceQueue.peek();
        while (nonce!=null && nonce._ts<expired)
        {
            _nonceQueue.remove(nonce);
            _nonceMap.remove(nonce._nonce);
            nonce=_nonceQueue.peek();
        }
        
        // Now check the requested nonce
        try
        {
            nonce = _nonceMap.get(digest.nonce);
            if (nonce==null)
                return 0;
         
            long count = Long.parseLong(digest.nc,16);
            if (count>=_maxNC)
                return 0;
            if (nonce.seen((int)count))
                return -1;
            return 1;
        }
        catch (Exception e)
        {
            LOG.ignore(e);
        }
        return -1;
    }

    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    /* ------------------------------------------------------------ */
    private static class Digest extends Credential
    {
        private static final long serialVersionUID = -2484639019549527724L;
        final String method;
        String username = "";
        String realm = "";
        String nonce = "";
        String nc = "";
        String cnonce = "";
        String qop = "";
        String uri = "";
        String response = "";

        /* ------------------------------------------------------------ */
        Digest(String m)
        {
            method = m;
        }

        /* ------------------------------------------------------------ */
        @Override
        public boolean check(Object credentials)
        {
            if (credentials instanceof char[])
                credentials=new String((char[])credentials);
            String password = (credentials instanceof String) ? (String) credentials : credentials.toString();

            try
            {
                MessageDigest md = MessageDigest.getInstance("MD5");
                byte[] ha1;
                if (credentials instanceof Credential.MD5)
                {
                    // Credentials are already a MD5 digest - assume it's in
                    // form user:realm:password (we have no way to know since
                    // it's a digest, alright?)
                    ha1 = ((Credential.MD5) credentials).getDigest();
                }
                else
                {
                    // calc A1 digest
                    md.update(username.getBytes(StringUtil.__ISO_8859_1));
                    md.update((byte) ':');
                    md.update(realm.getBytes(StringUtil.__ISO_8859_1));
                    md.update((byte) ':');
                    md.update(password.getBytes(StringUtil.__ISO_8859_1));
                    ha1 = md.digest();
                }
                // calc A2 digest
                md.reset();
                md.update(method.getBytes(StringUtil.__ISO_8859_1));
                md.update((byte) ':');
                md.update(uri.getBytes(StringUtil.__ISO_8859_1));
                byte[] ha2 = md.digest();

                // calc digest
                // request-digest = <"> < KD ( H(A1), unq(nonce-value) ":"
                // nc-value ":" unq(cnonce-value) ":" unq(qop-value) ":" H(A2) )
                // <">
                // request-digest = <"> < KD ( H(A1), unq(nonce-value) ":" H(A2)
                // ) > <">

                md.update(TypeUtil.toString(ha1, 16).getBytes(StringUtil.__ISO_8859_1));
                md.update((byte) ':');
                md.update(nonce.getBytes(StringUtil.__ISO_8859_1));
                md.update((byte) ':');
                md.update(nc.getBytes(StringUtil.__ISO_8859_1));
                md.update((byte) ':');
                md.update(cnonce.getBytes(StringUtil.__ISO_8859_1));
                md.update((byte) ':');
                md.update(qop.getBytes(StringUtil.__ISO_8859_1));
                md.update((byte) ':');
                md.update(TypeUtil.toString(ha2, 16).getBytes(StringUtil.__ISO_8859_1));
                byte[] digest = md.digest();

                // check digest
                return (TypeUtil.toString(digest, 16).equalsIgnoreCase(response));
            }
            catch (Exception e)
            {
                LOG.warn(e);
            }

            return false;
        }

        @Override
        public String toString()
        {
            return username + "," + response;
        }
    }
}
