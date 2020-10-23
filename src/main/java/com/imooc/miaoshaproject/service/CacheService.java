package com.imooc.miaoshaproject.service;

/**
 * 描述：
 *      封装本地缓存类
 * @author hl
 * @version 1.0
 * @date 2020/10/2 10:58
 */
public interface CacheService {
    //存
    void setCommonCache(String key, Object value);
    //取
    Object getCommonCache(String key);
}
