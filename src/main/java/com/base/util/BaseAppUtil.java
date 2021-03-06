package com.base.util;

import com.getbase.Client;
import com.getbase.Configuration;
import lombok.experimental.UtilityClass;
import org.springframework.core.env.MissingRequiredPropertiesException;

/**
 * Created by przemek on 19.10.2016.
 */

@UtilityClass
public class BaseAppUtil {

    public static Client baseClient() {
        return new com.getbase.Client(new Configuration.Builder()
                .accessToken(getProperty("BASECRM_ACCESS_TOKEN"))
                .build());
    }

    public static String getProperty(String key) {
        String value = System.getProperty(key);
        if (null == value) {
            value = System.getenv(key);
            if (null == value) {
                throw new MissingRequiredPropertiesException();
            }
        }
        return value;
    }
}
