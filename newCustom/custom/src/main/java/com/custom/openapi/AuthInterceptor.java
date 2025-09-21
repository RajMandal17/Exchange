package com.custom.openapi;

import com.custom.marketdata.entity.User;
import com.custom.service.UnifiedAuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {
    private final UnifiedAuthenticationService unifiedAuthenticationService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        System.out.println("DEBUG: AuthInterceptor.preHandle() called for: " + request.getRequestURI());
        
        // Use unified authentication that supports both session tokens and JWT
        User user = unifiedAuthenticationService.authenticate(request);
        if (user != null) {
            System.out.println("DEBUG: Setting currentUser attribute: " + user.getEmail());
            request.setAttribute("currentUser", user);
            // Also set accessToken if it was extracted for backward compatibility
            String accessToken = unifiedAuthenticationService.getAccessTokenFromRequest(request);
            if (accessToken != null) {
                request.setAttribute("accessToken", accessToken);
            }
        } else {
            System.out.println("DEBUG: No user authenticated in interceptor");
        }
        return true;
    }
}
