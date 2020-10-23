package com.imooc.miaoshaproject.access;

import com.alibaba.fastjson.JSON;
import com.imooc.miaoshaproject.error.BusinessException;
import com.imooc.miaoshaproject.error.EmBusinessError;
import com.imooc.miaoshaproject.response.CommonReturnType;
import com.imooc.miaoshaproject.service.model.UserModel;
import io.netty.handler.codec.http.HttpResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 描述：
 * 拦截器，进行接口限流
 *
 * @author hl
 * @version 1.0
 * @date 2020/10/22 19:07
 */
@Component
public class AccessInterceptor implements HandlerInterceptor {
    @Autowired
    RedisTemplate redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        if (handler instanceof HandlerMethod) {
            //根据token获取用户信息
            String token = request.getParameter("token");
            if (token == null) {
                throw new BusinessException(EmBusinessError.USER_NOT_LOGIN, "用户还未登陆，不能下单");
            }
            System.out.println("获取的token：" + token);
            //获取用户的登录信息
            UserModel userModel = (UserModel) redisTemplate.opsForValue().get(token);
            if (userModel == null) {
                render(response,"用户未登录，请先登录");
                return false;
            }
            //将用户信息放入到ThreadLocal中保存
            UserContext.setHolder(userModel);

            HandlerMethod handlerMethod = (HandlerMethod) handler;
            AccessLimit accessLimit = handlerMethod.getMethodAnnotation(AccessLimit.class);
            if (accessLimit == null) {
                return true;
            }
            int second = accessLimit.second();
            int maxCount = accessLimit.maxCount();
            //通过redis来做接口的限流
            String uri = request.getRequestURI();
            String uriKey = uri + "_" + token;
            Integer count = (Integer) redisTemplate.opsForValue().get(uriKey);
            if (count == null) {
                redisTemplate.opsForValue().set(uriKey, 1, second, TimeUnit.SECONDS);
            } else if (count <= maxCount) {
                redisTemplate.opsForValue().increment(uriKey, 1);
            } else {
                render(response,"访问太频繁，请稍后再试");
                return false;
            }
        }
        return true;
    }
    public void render(HttpServletResponse response,String msg) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        OutputStream outputStream = response.getOutputStream();
        outputStream.write(msg.getBytes("UTF-8"));
        outputStream.flush();
        outputStream.close();
    }
//    public void render1(HttpServletResponse response, EmBusinessError error) throws IOException {
//        response.setContentType("application/json;charset=UTF-8");
//        OutputStream outputStream = response.getOutputStream();
//        Map<String,Object> responseData = new HashMap<>();
//        responseData.put("errCode",error.getErrCode());
//        responseData.put("errMsg",error.getErrMsg());
//        String resp = JSON.toJSONString(CommonReturnType.create(responseData, "fail"));
//        outputStream.write(resp.getBytes("UTF-8"));
//        outputStream.flush();
//        outputStream.close();
//    }
}
