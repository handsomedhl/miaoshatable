package com.miaoshaproject.config;

import org.apache.coyote.http11.Http11NioProtocol;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

/**
 * 描述：
 *  我们在配置文件中配置的参数会被传递到WebServerFactoryCustomizer，我们在WebServerFactoryCustomizer中
 *  进行最后一次修改，对keepAlive进行优化
 * @author hl
 * @version 1.0
 * @date 2020/9/28 19:22
 */
@Component
public class WebServerConfiguration implements WebServerFactoryCustomizer<ConfigurableWebServerFactory> {
    /**
     * 在new出tomcat容器的时候会传给我们对应的一个ConfigurableWebServerFactory factory) ，一个可配置
     * 的WebServer的工厂，我们可以在这里定制化一些东西
     * @param factory
     */
    @Override
    public void customize(ConfigurableWebServerFactory factory) {
        //使用对应的工厂类提供给我们的接口定制化我们的tomcat connector
        ((TomcatServletWebServerFactory)factory).addConnectorCustomizers(connector -> {
            //在这里定制化我们需要的参数
            Http11NioProtocol protocol = (Http11NioProtocol)connector.getProtocolHandler();
            //设置长连接的超时时间，30秒
            protocol.setKeepAliveTimeout(30000);
            //设置单次长连接的最大请求数，超过定制的阈值后服务端断开连接
            protocol.setMaxKeepAliveRequests(10000);
        });
    }
}
