package com.imooc.miaoshaproject.service;

import com.google.common.annotations.VisibleForTesting;
import com.imooc.miaoshaproject.error.BusinessException;
import com.imooc.miaoshaproject.service.model.PromoModel;

public interface PromoService {
    //根据itemid获取即将进行的或正在进行的秒杀活动
    PromoModel getPromoByItemId(Integer itemId);

    //活动发布
    void publishPromo(Integer promoId);

    //根据活动id生成秒杀令牌
    String generateSecondKillToken(Integer promoId,Integer userId,Integer itemId) throws BusinessException;
}
