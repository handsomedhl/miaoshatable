package com.imooc.miaoshaproject.controller;

import com.google.common.util.concurrent.RateLimiter;
import com.imooc.miaoshaproject.access.AccessLimit;
import com.imooc.miaoshaproject.access.UserContext;
import com.imooc.miaoshaproject.mq.MqProducer;
import com.imooc.miaoshaproject.service.ItemService;
import com.imooc.miaoshaproject.service.OrderService;
import com.imooc.miaoshaproject.error.BusinessException;
import com.imooc.miaoshaproject.error.EmBusinessError;
import com.imooc.miaoshaproject.response.CommonReturnType;
import com.imooc.miaoshaproject.service.PromoService;
import com.imooc.miaoshaproject.service.model.OrderModel;
import com.imooc.miaoshaproject.service.model.UserModel;
import com.imooc.miaoshaproject.util.CodeUtil;
import jdk.nashorn.internal.ir.RuntimeNode;
import jdk.nashorn.internal.parser.Token;
import org.apache.catalina.User;
import org.apache.commons.lang3.StringUtils;
import org.omg.PortableInterceptor.INACTIVE;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.RenderedImage;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.*;

@Controller("order")
@RequestMapping("/order")
@CrossOrigin(origins = {"*"}, allowCredentials = "true")
public class OrderController extends BaseController {
    @Autowired
    private OrderService orderService;

    @Autowired
    private HttpServletRequest httpServletRequest;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private MqProducer producer;
    @Autowired
    private ItemService itemService;
    @Autowired
    private PromoService promoService;
    //泄洪队列
    ExecutorService executorService;
    //限流
    private RateLimiter orderCreateRateLimiter;


    @PostConstruct
    public void init() {
        executorService = Executors.newFixedThreadPool(20);
        orderCreateRateLimiter.create(100);
    }


    //生成验证码
    @RequestMapping(value = "/generateverifycode", method = {RequestMethod.GET})
    @ResponseBody
    public void generateverifycode(HttpServletResponse response) throws BusinessException, IOException {
        UserModel userModel = UserContext.getHolder();
        Map<String,Object> map = CodeUtil.generateCodeAndPic();
        redisTemplate.opsForValue().set("verify_code_"+userModel.getId(),map.get("code"));
        ImageIO.write((RenderedImage) map.get("codePic"), "jpeg", response.getOutputStream());
    }


    /**
     * 生成秒杀令牌
     * @param itemId
     * @param promoId
     * @param verifyCode
     * @return
     * @throws BusinessException
     */
    @RequestMapping(value = "/generatetoken", method = {RequestMethod.POST}, consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType generatetoken(@RequestParam(name = "itemId") Integer itemId,
                                          @RequestParam(name = "promoId") Integer promoId,
                                          @RequestParam(name = "verifyCode") String verifyCode) throws BusinessException {
        //获取用户信息
        UserModel userModel = UserContext.getHolder();
        //对验证码进行判断
        String verifyCodeInRedis = (String) (redisTemplate.opsForValue().get("verify_code_" + userModel.getId()));
        if (!StringUtils.equalsIgnoreCase(verifyCodeInRedis, verifyCode)) {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"验证码错误");
        }


        //生成秒杀令牌
        String promoToken = promoService.generateSecondKillToken(promoId, userModel.getId(), itemId);
        if (promoToken == null) {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "生成令牌失败");
        }
        return new CommonReturnType().create(promoToken);
    }

    /**
     * 封装下单请求
     * @param itemId
     * @param amount
     * @param promoToken    秒杀令牌
     * @param promoId
     * @return
     * @throws BusinessException
     */
    @AccessLimit(second = 60, maxCount = 2)
    @RequestMapping(value = "/createorder", method = {RequestMethod.POST}, consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType createOrder(@RequestParam(name = "itemId") Integer itemId,
                                        @RequestParam(name = "amount") Integer amount,
                                        @RequestParam(name = "promoToken", required = false) String promoToken,
                                        @RequestParam(name = "promoId", required = false) Integer promoId) throws BusinessException {

        //令牌桶限流
//        if (!orderCreateRateLimiter.tryAcquire()) {
//            throw new BusinessException(EmBusinessError.RATELIMIT);
//        }

        UserModel userModel = UserContext.getHolder();
        //校验秒杀令牌是否正确

        if (promoId != null) {
            String inredisPromoToken = (String) redisTemplate.opsForValue().get("promo_token_" + promoId + "_userId_" + userModel.getId() + "_itemId_" + itemId);
            if (!StringUtils.equals(inredisPromoToken, promoToken)) {
                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "秒杀令牌失效");
            }
        }

        if (amount <= 0) {
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR, "数量信息不合法");
        }

        //同步调用线程池的submit方法
        //拥塞窗口为20的等待队列，用来队列泄洪
        Future<Object> future = executorService.submit(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                //初始化库存流水
                String stockLogId = itemService.initStockLog(itemId, amount);

                //        OrderModel orderModel = orderService.createOrder(userModel.getId(),itemId,promoId,amount);
                //事务型消息创建订单

                if (!producer.transactionAsyncReduceStock(userModel.getId(), itemId, promoId, amount, stockLogId)) {
                    throw new BusinessException(EmBusinessError.UNKNOWN_ERROR, "下单失败");
                }
                return null;
            }
        });

        try {
            future.get();//block自己等待返回
        } catch (InterruptedException e) {
            throw new BusinessException(EmBusinessError.UNKNOWN_ERROR);
        } catch (ExecutionException e) {
            throw new BusinessException(EmBusinessError.UNKNOWN_ERROR);
        }
        return CommonReturnType.create(null);
    }
}
