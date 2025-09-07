package com.tspoon.core.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RequestUtil {
    
    public static Long getCurrentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId instanceof Long) {
            return (Long) userId;
        }
        log.warn("User ID not found in request attributes");
        return null;
    }
    
    public static String getCurrentUserEmail(HttpServletRequest request) {
        Object userEmail = request.getAttribute("userEmail");
        if (userEmail instanceof String) {
            return (String) userEmail;
        }
        log.warn("User email not found in request attributes");
        return null;
    }
    
    public static String getCurrentUserRole(HttpServletRequest request) {
        Object userRole = request.getAttribute("userRole");
        if (userRole instanceof String) {
            return (String) userRole;
        }
        log.warn("User role not found in request attributes");
        return null;
    }
    
    public static String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}
