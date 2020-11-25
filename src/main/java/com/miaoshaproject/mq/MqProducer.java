package com.miaoshaproject.mq;

import com.alibaba.fastjson.JSON;
import com.miaoshaproject.dao.StockLogDOMapper;
import com.miaoshaproject.dataobject.StockLogDO;
import com.miaoshaproject.error.BusinessException;
import com.miaoshaproject.service.OrderService;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.*;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * 描述：
 *      消息的生产者
 * @author hl
 * @version 1.0
 * @date 2020/10/4 10:53
 */
@Component
public class MqProducer {

    private DefaultMQProducer producer;
    //事务型消息
    private TransactionMQProducer transactionMQProducer;

    @Value("${mq.nameserver.addr}")
    private String nameAddr;
    @Value("${mq.topicname}")
    private String topicName;
    @Autowired
    private OrderService orderService;
    @Autowired
    private StockLogDOMapper stockLogDOMapper;
    @Autowired
    private RedisTemplate redisTemplate;

    @PostConstruct
    public void init() throws MQClientException {
        //mq Producer的初始化
        producer = new DefaultMQProducer("producer_group");
        producer.setNamesrvAddr(nameAddr);
        producer.start();
        //transactionMQProducer初始化
        transactionMQProducer = new TransactionMQProducer("transaction_producer_group");
        transactionMQProducer.setNamesrvAddr(nameAddr);
        transactionMQProducer.start();
        transactionMQProducer.setTransactionListener(new TransactionListener() {
            //将消息投递后随即调用该方法
            @Override
            public LocalTransactionState executeLocalTransaction(Message message, Object arg) {
                //在这里执行创建订单的操作，这里执行的结果决定了消息是否可以被消费
                Map<String, Object> map = (Map) arg;
                Integer userId = (Integer) map.get("userId");
                Integer promoId = (Integer) map.get("promoId");
                Integer itemId = (Integer) map.get("itemId");
                Integer amount = (Integer) map.get("amount");
                String stockLogId = (String) map.get("stockLogId");
                try {
                    orderService.createOrder(userId,itemId,promoId,amount,stockLogId);
                } catch (BusinessException e) {
                    e.printStackTrace();
                    //更改流水状态
                    StockLogDO stockLogDO = stockLogDOMapper.selectByPrimaryKey(stockLogId);
                    stockLogDO.setStatus(3);
                    stockLogDOMapper.updateByPrimaryKeySelective(stockLogDO);
                    //回补库存
                    redisTemplate.opsForValue().increment("promo_item_stock_" + itemId, amount.intValue());
                    return LocalTransactionState.ROLLBACK_MESSAGE;
                }
                return LocalTransactionState.COMMIT_MESSAGE;
            }
            //当executeLocalTransaction长时间没有返回结果或返回UNKNOW，那么消息中间件就会回调这个方法
            @Override
            public LocalTransactionState checkLocalTransaction(MessageExt messageExt) {
                //根据是否扣减库存成功，来判断是否要返回COMMIT，ROLLBACK还是继续UNKNOW
                byte[] body = messageExt.getBody();
                Map<String,Object> map = JSON.parseObject(new String(body), Map.class);
                String stockLogId = (String) map.get("stockLogId");
                StockLogDO stockLogDO = stockLogDOMapper.selectByPrimaryKey(stockLogId);
                if (stockLogDO == null) {
                    return LocalTransactionState.UNKNOW;
                }
                Integer status = stockLogDO.getStatus();
                if (status.intValue() == 2) {
                    return LocalTransactionState.COMMIT_MESSAGE;
                }else if (status.intValue() == 1) {
                    return LocalTransactionState.UNKNOW;
                }else{
                    return LocalTransactionState.ROLLBACK_MESSAGE;
                }
            }
        });
    }

    //事务型同步库存扣减消息
    public boolean transactionAsyncReduceStock(Integer userId, Integer itemId, Integer promoId, Integer amount, String stockLogId){
        Map<String,Object> map = new HashMap<>();
        map.put("itemId", itemId);
        map.put("amount", amount);
        map.put("stockLogId", stockLogId);
        Message message = new Message(topicName, "increase",
                JSON.toJSON(map).toString().getBytes(Charset.forName("UTF-8")));
        HashMap<String, Object> argsMap = new HashMap<>();
        argsMap.put("itemId", itemId);
        argsMap.put("amount", amount);
        argsMap.put("userId", userId);
        argsMap.put("promoId", promoId);
        argsMap.put("stockLogId", stockLogId);
        TransactionSendResult transactionSendResult = null;
        try {
            //发会将消息投递到broker中但是是处于prepare阶段，消费者不可见，在客户端执行executeLocalTransaction方法
            transactionSendResult = transactionMQProducer.sendMessageInTransaction(message,argsMap);
        } catch (MQClientException e) {
            e.printStackTrace();
            return false;
        }
        if (transactionSendResult.getLocalTransactionState() == LocalTransactionState.COMMIT_MESSAGE) {
            return true;
        }else{
            return false;
        }
    }



    //同步扣减库存
    public boolean asyncReduceStock(Integer itemId, Integer amount){
        Map<String,Object> map = new HashMap<>();
        map.put("itemId", itemId);
        map.put("amount", amount);
        Message message = new Message(topicName, "increase",
                JSON.toJSON(map).toString().getBytes(Charset.forName("UTF-8")));
        try {
            producer.send(message);
        } catch (MQClientException e) {
            e.printStackTrace();
            return false;
        } catch (RemotingException e) {
            e.printStackTrace();
            return false;
        } catch (MQBrokerException e) {
            e.printStackTrace();
            return false;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
}
