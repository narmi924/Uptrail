package com.uptrail.identity.web;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

/**
 * After sign-in, go to the page the user asked for (an email link or a protected page) if it is a safe
 * path in the same workspace; otherwise go to the workspace dashboard. External URLs are never followed.
 */
public class EntrySuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final EntryPoint entryPoint;
    private final HttpSessionRequestCache requestCache = new HttpSessionRequestCache();

    public EntrySuccessHandler(EntryPoint entryPoint) {
        this.entryPoint = entryPoint;
        setDefaultTargetUrl(entryPoint.home());
        setAlwaysUseDefaultTargetUrl(false);
        setRequestCache(requestCache);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws ServletException, IOException {
        String next = request.getParameter("next");
        SavedRequest saved = requestCache.getRequest(request, response);
        requestCache.removeRequest(request, response);
        String target = entryPoint.home();
        if (entryPoint.isSafeRedirect(next)) {
            target = next;
        } else if (saved != null) {
            String path = pathOf(saved.getRedirectUrl(), request);
            if (entryPoint.isSafeRedirect(path)) {
                target = path;
            }
        }
        clearAuthenticationAttributes(request);
        getRedirectStrategy().sendRedirect(request, response, target);
    }

    private static String pathOf(String url, HttpServletRequest request) {
        if (url == null) {
            return null;
        }
        String prefix = request.getScheme() + "://" + request.getServerName();
        int start = url.indexOf(request.getContextPath() + "/", prefix.length());
        if (!url.startsWith(prefix) || start < 0) {
            return null;
        }
        return url.substring(start + request.getContextPath().length());
    }
}
