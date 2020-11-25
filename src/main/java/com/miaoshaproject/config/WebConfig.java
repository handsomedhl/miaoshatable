package com.miaoshaproject.config;


import com.miaoshaproject.access.AccessInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 描述：
 *      配置拦截器
 * @author hl
 * @version 1.0
 * @date 2020/10/22 19:28
 */
@Component
public class WebConfig implements WebMvcConfigurer {
    @Autowired
    AccessInterceptor accessInterceptor;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        InterceptorRegistration interceptorRegistration = registry.addInterceptor(accessInterceptor);
        interceptorRegistration.addPathPatterns("/order/**");
    }
}
