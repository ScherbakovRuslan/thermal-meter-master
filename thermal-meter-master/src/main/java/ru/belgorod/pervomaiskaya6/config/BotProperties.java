package ru.belgorod.pervomaiskaya6.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "bot")
@Component
@Getter
@Setter
public class BotProperties {
    private String name;
    private String token;
}
