package com.miaoshaproject.access;

import com.miaoshaproject.service.model.UserModel;

/**
 * 描述：
 *      保存用户信息到ThreadLocal中，可以在此次请求的任意位置获取
 * @author hl
 * @version 1.0
 * @date 2020/10/22 19:17
 */
public class UserContext {
    private static ThreadLocal<UserModel> holder = new ThreadLocal<>();

    public static UserModel getHolder() {
        return holder.get();
    }

    public static void setHolder(UserModel userModel) {
        holder.set(userModel);
    }
}
