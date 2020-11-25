package com.miaoshaproject.service.impl;

import com.miaoshaproject.dao.PromoDOMapper;
import com.miaoshaproject.dataobject.PromoDO;
import com.miaoshaproject.error.BusinessException;
import com.miaoshaproject.error.EmBusinessError;
import com.miaoshaproject.service.ItemService;
import com.miaoshaproject.service.PromoService;
import com.miaoshaproject.service.model.ItemModel;
import com.miaoshaproject.service.model.PromoModel;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Created by hzllb on 2018/11/18.
 */
@Service
public class PromoServiceImpl implements PromoService {

    @Autowired
    private PromoDOMapper promoDOMapper;

    @Autowired
    private ItemService itemService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public PromoModel getPromoByItemId(Integer itemId) {
        //获取对应商品的秒杀活动信息
        PromoDO promoDO = promoDOMapper.selectByItemId(itemId);

        //dataobject->model
        PromoModel promoModel = convertFromDataObject(promoDO);
        if(promoModel == null){
            return null;
        }

        //判断当前时间是否秒杀活动即将开始或正在进行
        if(promoModel.getStartDate().isAfterNow()){
            promoModel.setStatus(1);
        }else if(promoModel.getEndDate().isBeforeNow()){
            promoModel.setStatus(3);
        }else{
            promoModel.setStatus(2);
        }
        return promoModel;
    }
    //发布活动信息
    @Override
    public void publishPromo(Integer promoId) {
        PromoDO promoDO = promoDOMapper.selectByPrimaryKey(promoId);
        if (promoDO.getItemId() == null || promoDO.getItemId().intValue() == 0) {
            return;
        }
        ItemModel itemMOdel = itemService.getItemById(promoDO.getItemId());
        //将库存同步到redis中
        redisTemplate.opsForValue().set("promo_item_stock_"+itemMOdel.getId(),itemMOdel.getStock()
                ,1, TimeUnit.HOURS);
        //将大闸设置到redis中
        redisTemplate.opsForValue().set("promo_door_count_"+promoId, itemMOdel.getStock() * 3);
        //清除售罄标识
        if (itemMOdel.getStock() != 0) {
            redisTemplate.delete("promo_item_stock_invalid_"+promoDO.getItemId());
        }

    }

    @Override
    public String generateSecondKillToken(Integer promoId,Integer userId,Integer itemId) throws BusinessException {
        //判断库存是否售罄
        if (redisTemplate.hasKey("promo_item_stock_invalid_"+itemId)) {
            return null;
        };
        //获取对应商品的秒杀活动信息
        PromoDO promoDO = promoDOMapper.selectByPrimaryKey(promoId);
        ItemModel itemModel = itemService.getItemByIdInCache(itemId);
        //dataobject->model
        PromoModel promoModel = convertFromDataObject(promoDO);
        //校验活动信息
        if(promoId != null){
            //（1）校验对应活动是否存在这个适用商品
            if(promoId.intValue() != itemModel.getPromoModel().getId()){
                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"活动信息不正确");
                //（2）校验活动是否正在进行中
            }else if(itemModel.getPromoModel().getStatus().intValue() != 2) {
                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"活动信息还未开始");
            }
        }


        //检验流量大闸
        long count = redisTemplate.opsForValue().increment("promo_door_count_" + promoId, -1);
        if(count < 0){
            return null;
        }
        //生成令牌,设置有效期为5分钟
        String promoToken = UUID.randomUUID().toString().replace("-","");
        redisTemplate.opsForValue()
                .set("promo_token_"+promoId+"_userId_"+userId+"_itemId_"+itemId,promoToken,
                        5,TimeUnit.MINUTES);
        return promoToken;
    }

    private PromoModel convertFromDataObject(PromoDO promoDO){
        if(promoDO == null){
            return null;
        }
        PromoModel promoModel = new PromoModel();
        BeanUtils.copyProperties(promoDO,promoModel);
        promoModel.setPromoItemPrice(new BigDecimal(promoDO.getPromoItemPrice()));
        promoModel.setStartDate(new DateTime(promoDO.getStartDate()));
        promoModel.setEndDate(new DateTime(promoDO.getEndDate()));

        //判断当前时间是否秒杀活动即将开始或正在进行
        if(promoModel.getStartDate().isAfterNow()){
            promoModel.setStatus(1);
        }else if(promoModel.getEndDate().isBeforeNow()){
            promoModel.setStatus(3);
        }else{
            promoModel.setStatus(2);
        }

        return promoModel;
    }
}
