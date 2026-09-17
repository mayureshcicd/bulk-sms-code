package com.sms.config;

import com.sms.service.TrialService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class TrialWebConfig implements WebMvcConfigurer {
    private final TrialService trial;
    public TrialWebConfig(TrialService trial) { this.trial = trial; }
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                if (!trial.isExpired()) return true;
                response.setStatus(402);
                response.setContentType("text/plain;charset=UTF-8");
                response.getWriter().write("Trial period has expired.");
                return false;
            }
        }).addPathPatterns("/**").excludePathPatterns("/login", "/login.html", "/logout", "/css/**", "/js/**", "/images/**", "/error");
    }
}
