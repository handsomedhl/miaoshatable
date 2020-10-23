package com.imooc.miaoshaproject.service.impl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.imooc.miaoshaproject.service.CacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

/**
 * 描述：
 *
 * @author hl
 * @version 1.0
 * @date 2020/10/2 11:00
 */
@Service
public class CacheServiceImpl implements CacheService {
    private Cache<String,Object> commonCache = null;

    @PostConstruct
    public void  init(){
        commonCache = CacheBuilder.newBuilder()
                //设置缓存容器的初始容量为10
                .initialCapacity(10)
                //设置缓存中最大可以存储100个key，超过之后，按照lru的策略移除缓存项
                .maximumSize(100)
                //设置缓存过程策略，在写入1分钟后过期
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .build();
    }

    @Override
    public void setCommonCache(String key, Object value) {
        commonCache.put(key,value);
    }

    @Override
    public Object getCommonCache(String key) {
        //如果存在直接返回，如果不存在返回null
        return commonCache.getIfPresent(key);
    }
}
