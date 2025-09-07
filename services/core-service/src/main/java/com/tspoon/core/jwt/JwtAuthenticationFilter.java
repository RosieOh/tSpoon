package com.tspoon.core.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private final JwtUtil jwtUtil;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        String requestURI = request.getRequestURI();
        log.debug("Processing request: {}", requestURI);
        
        // 공개 엔드포인트는 필터를 건너뜀
        if (isPublicEndpoint(requestURI)) {
            filterChain.doFilter(request, response);
            return;
        }
        
        String token = extractTokenFromRequest(request);
        
        if (token != null && jwtUtil.validateToken(token)) {
            try {
                String email = jwtUtil.getEmailFromToken(token);
                Long userId = jwtUtil.getUserIdFromToken(token);
                String role = jwtUtil.getRoleFromToken(token);
                
                // 인증 객체 생성
                UsernamePasswordAuthenticationToken authentication = 
                    new UsernamePasswordAuthenticationToken(
                        email, 
                        null, 
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role))
                    );
                
                // 사용자 ID를 request attribute에 저장
                request.setAttribute("userId", userId);
                request.setAttribute("userEmail", email);
                request.setAttribute("userRole", role);
                
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("User authenticated: {} with role: {}", email, role);
                
            } catch (Exception e) {
                log.error("JWT authentication failed: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        
        filterChain.doFilter(request, response);
    }
    
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
    
    private boolean isPublicEndpoint(String requestURI) {
        return requestURI.startsWith("/api/auth/") ||
               requestURI.startsWith("/api/users/register") ||
               requestURI.startsWith("/api/users/login") ||
               requestURI.startsWith("/actuator/") ||
               requestURI.startsWith("/swagger-ui/") ||
               requestURI.startsWith("/v3/api-docs/");
    }
}
