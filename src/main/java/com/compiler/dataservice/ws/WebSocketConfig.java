package com.compiler.dataservice.ws;

import com.compiler.dataservice.config.AppProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final InteractiveExecutionWebSocketHandler handler;
    private final AppProperties props;

    public WebSocketConfig(InteractiveExecutionWebSocketHandler handler, AppProperties props) {
        this.handler = handler;
        this.props = props;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/execute")
                .addInterceptors(new InteractiveAuthInterceptor(props.getSecurity().getApiKeys()))
                .setAllowedOrigins("*");
    }
}
