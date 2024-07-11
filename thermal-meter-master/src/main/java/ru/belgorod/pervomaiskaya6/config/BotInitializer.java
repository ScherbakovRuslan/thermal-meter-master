package ru.belgorod.pervomaiskaya6.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import ru.belgorod.pervomaiskaya6.service.IBotService;

@Slf4j
@Component
@RequiredArgsConstructor
public class BotInitializer {

    private final IBotService bot;

    @EventListener({ContextRefreshedEvent.class})
    public void init() throws TelegramApiException {
        TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
        try {
            api.registerBot(bot);
        } catch (TelegramApiException ex) {
            log.error(ex.getMessage());
        }
    }
}
