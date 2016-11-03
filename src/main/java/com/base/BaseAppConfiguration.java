package com.base;

import com.getbase.Client;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Created by przemek on 26/10/16.
 */

@Configuration
class BaseAppConfiguration {

    private final String token;

    BaseAppConfiguration(@Value("${config.token}") String token) {
        this.token = token;
    }

    @Bean
    Client client() {
        return new Client(new com.getbase.Configuration.Builder()
                                  .accessToken(token)
                                  .build());
    }
}
