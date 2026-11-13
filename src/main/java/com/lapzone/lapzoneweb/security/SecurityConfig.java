package com.lapzone.lapzoneweb.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private CustomAuthenticationSuccessHandler successHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/", "/dangnhap", "/dangki", "/css/**", "/js/**", "/images/**", "/uploads/**").permitAll()
                .requestMatchers("/cart/**", "/profile", "/profile/**", "/support", "/support/**", "/store/orders", "/store/orders/**", "/api/orders", "/api/orders/**").authenticated()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().permitAll() 
            )
            .exceptionHandling(errors -> {
                errors.defaultAuthenticationEntryPointFor((request, response, error) -> {
                    response.setStatus(401);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"message\":\"Vui lòng đăng nhập để liên hệ nhân viên hỗ trợ.\"}");
                }, request -> request.getRequestURI().substring(request.getContextPath().length())
                    .matches("/api/ai/conversations/[^/]+/messages/[^/]+/support"));
                errors.defaultAuthenticationEntryPointFor((request, response, error) -> {
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"message\":\"Vui lòng đăng nhập để xem đơn hàng.\"}");
                }, request -> {
                    String path = request.getRequestURI().substring(request.getContextPath().length());
                    return path.equals("/api/orders") || path.startsWith("/api/orders/");
                });
                errors.defaultAuthenticationEntryPointFor(
                    new org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint("/dangnhap"),
                    request -> true);
            })
            .formLogin(form -> form
                .loginPage("/dangnhap")
                .loginProcessingUrl("/dangnhap")
                .usernameParameter("emailOrPhone")
                .passwordParameter("password")
                .successHandler(successHandler)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            .csrf(csrf -> csrf.requireCsrfProtectionMatcher(request -> {
                String path = request.getRequestURI().substring(request.getContextPath().length());
                return !java.util.Set.of("GET", "HEAD", "OPTIONS", "TRACE").contains(request.getMethod())
                        && (path.startsWith("/admin/") || path.startsWith("/store/orders/") || path.startsWith("/api/orders/")
                            || (path.startsWith("/api/ai/conversations/") && (path.endsWith("/feedback") || path.endsWith("/support")))
                            || path.equals("/support") || path.startsWith("/support/") || path.equals("/checkout/process")
                            || path.equals("/checkout/voucher-preview"));
            }));

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
    }
}
