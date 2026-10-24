package com.tspoon.core.jwt;

import com.tspoon.auth.jwt.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Bearer 토큰이 유효하면 SecurityContext 에 인증 정보를 채운다.
 * 접근 허용 여부는 SecurityFilterChain 의 인가 규칙이 결정하므로 여기서는 공개 경로를 따로 구분하지 않는다.
 *
 * 빈으로 등록하지 않는다. (@Component 로 등록하면 서블릿 필터 체인에도 한 번 더 등록된다)
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            jwtUtil.parseAccessToken(header.substring(BEARER_PREFIX.length()))
                    .ifPresent(user -> SecurityContextHolder.getContext().setAuthentication(
                            UsernamePasswordAuthenticationToken.authenticated(
                                    user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.role())))));
        }

        filterChain.doFilter(request, response);
    }
}
