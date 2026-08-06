package com.minialalipay.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

/** 验证账户中心的 MyBatis 配置位于 Spring Boot 能识别的顶层。 */
class ConfigurationContractTest {

    @Test
    void shouldEnableUnderscoreToCamelCaseMappingAtTopLevel() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application.yml"));
        Properties properties = factory.getObject();

        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("mybatis.configuration.map-underscore-to-camel-case"))
                .isEqualTo("true");
    }
}
