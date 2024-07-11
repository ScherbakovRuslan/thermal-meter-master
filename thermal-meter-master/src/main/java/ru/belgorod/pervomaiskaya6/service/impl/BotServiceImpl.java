package ru.belgorod.pervomaiskaya6.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Contact;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.belgorod.pervomaiskaya6.config.BotProperties;
import ru.belgorod.pervomaiskaya6.domain.model.UserData;
import ru.belgorod.pervomaiskaya6.domain.repository.UserDataRepository;
import ru.belgorod.pervomaiskaya6.service.IBotService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class BotServiceImpl extends TelegramLongPollingBot implements IBotService {

    private static final String UNAVAILABLE_MESSAGE_FORMAT = "Unavailable message from %s %s %s: %s";
    private static final Pattern PATTERN = Pattern.compile("\\d{1,5}[\\.,]\\d{3}");

    private final BotProperties botProperties;
    private final UserDataRepository repository;

    @Override
    public String getBotUsername() {
        return botProperties.getName();
    }

    @Override
    public String getBotToken() {
        return botProperties.getToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            final long chatId = update.getMessage().getChatId();
            final String firstName = update.getMessage().getFrom().getFirstName();
            final String lastName = update.getMessage().getFrom().getLastName();

            switch (messageText) {
                case "/start":
                    log.info(chatId + " - /start - " + firstName + " " + lastName);
                    startBot(chatId, firstName);
                    break;
                case "/reset":
                    log.info(chatId + " - /reset - " + firstName + " " + lastName);
                    removeRoomNumber(chatId);
                    break;
                case "/sendToAll":
                    sendToAll();
                    break;
                case "/copyAndClear":
                    copyAndClearThermalMeterValue();
                    break;
                default:
                    if (isRoomNumber(messageText)) {
                        log.info(chatId + " - enter room number - " + messageText);
                        setRoomNumber(chatId, Integer.parseInt(messageText));
                    } else if (isThermalMeterData(messageText)) {
                        setThermalData(chatId, messageText);
                    } else {
                        sendError(chatId);
                        log.warn(format(messageText, chatId, firstName, lastName));
                    }
            }
        } else if (update.hasMessage() && update.getMessage().hasContact()) {
            createUserFromContact(update);
        }
    }

    private void sendToAll() {
        for (UserData userData : repository.findAll()) {
            if (userData.getThermalMeterValue() == null) {
                sendMessage(userData.getId(), "Добрый день, пришло время списать показания ваших тепловых счетчиков.");
                sendMessage(userData.getId(), "Отправьте мне текущие показания Вашего теплового счетчика! " +
                        "Для этого необходимо на счетчике нажать главную кнопку ОДИН раз и переписать нужное значение.");
            }

        }
    }

    private void copyAndClearThermalMeterValue() {
        repository.copyAndClearThermalMeterValue();
    }

    private void removeRoomNumber(long chatId) {
        Optional<UserData> optional = repository.findById(chatId);
        if (optional.isPresent()) {
            UserData userData = optional.get();
            userData.setRoomNumber(null);
            repository.save(userData);
            sendMessage(chatId, "Я удалил старый номер квартиры, теперь Вы можете ввести новый.");
        } else {
            sendMessage(chatId, "Вы еще не сохраняли номер квартиры, чтобы перейти к следующему шагу, поделитесь, пожалуйста, номером Вашего телефона по кнопке ниже!");
        }
    }

    private void createUserFromContact(Update update) {
        Contact contact = update.getMessage().getContact();
        Long id = contact.getUserId();
        Optional<UserData> optional = repository.findById(id);
        if (optional.isEmpty()) {
            log.info(id + " - enter phone number");
            UserData userData = new UserData(
                    id,
                    contact.getPhoneNumber(),
                    contact.getFirstName(),
                    contact.getLastName());
            repository.save(userData);
            log.info(id + " - new user created");
            sendMessage(id, "Спасибо, я сохранил Ваш номер телефона. " +
                    "Теперь отправьте мне номер Вашей квартиры!");
        } else {
            log.info(id + " - enter phone second time");
            sendMessage(id, "Ваш номер телефона уже сохранен, введите номер квартиры или показания теплового счетчика!");
        }
    }

    private void setRoomNumber(long chatId, Integer roomNumber) {
        Optional<UserData> byRoomNumber = repository.findByRoomNumber(roomNumber);
        if (byRoomNumber.isPresent()) {
            sendMessage(chatId, "Уже существует пользователь, указавший эту квартиру. " +
                    "Может быть кто-то из Ваших родных уже регестрировался в боте?");
            sendMessage(chatId, "Если вы снова ввели ошибочный номер, просто напишите правильный еще раз!");
            sendMessage(chatId, "Если это действительно ошибка, обратитесь к @olegjan92.");
            log.warn(chatId + " - entered room number is present in db");
        } else {
            Optional<UserData> userDataOptional = repository.findById(chatId);
            if (userDataOptional.isEmpty()) {
                sendMessage(chatId, "Сначала нажмите на кнопку 'Поделиться номером телефона'!");
                log.warn(chatId + " - enter room number before phone number");
            } else {
                UserData userData = userDataOptional.get();
                if (userData.getRoomNumber() != null && !userData.getRoomNumber().equals(roomNumber)) {
                    sendMessage(chatId, "Вы уже ввели номер квартиры, если хотите поменять "
                            + userData.getRoomNumber() + " на " + roomNumber
                            + " нажмите на команду /reset!");
                    log.warn(chatId + " - enter another room number");
                } else if (userData.getRoomNumber() != null && userData.getRoomNumber().equals(roomNumber)) {
                    sendMessage(chatId, "Номер Вашей квартиры уже сохранен!");
                    sendMessage(chatId, "Теперь отправьте мне текущие показания Вашего теплового счетчика! " +
                            "Для этого необходимо на счетчике нажать главную кнопку ОДИН раз и переписать нужное значение.");
                    log.info(chatId + " - enter the same room number");
                } else {
                    userData.setRoomNumber(roomNumber);
                    repository.save(userData);
                    sendMessage(chatId, "Спасибо, я сохранил номер квартиры. " +
                            "Если Вы ошиблись при вводе номера квартиры, нажмите на команду /reset!");
                    sendMessage(chatId, "Теперь отправьте мне текущие показания Вашего теплового счетчика! " +
                            "Для этого необходимо на счетчике нажать главную кнопку ОДИН раз и переписать нужное значение.");
                    log.info(chatId + " - enter correct room number");
                }
            }
        }
    }

    private void setThermalData(long chatId, String data) {
        Optional<UserData> userDataOptional = repository.findById(chatId);
        if (userDataOptional.isPresent()) {
            UserData userData = userDataOptional.get();
            if (userData.getRoomNumber() == null) {
                sendMessage(chatId, "Сначала укажите номер квартиры, а потом запишем показания счетчика.");
                log.warn(chatId + " - enter thermal meter data before room number");
            } else {
                Double value = parseData(data);
                if (userData.getOldThermalMeterValue() != null && value.compareTo(userData.getOldThermalMeterValue()) < 0) {
                    sendMessage(chatId, "Показания меньше чем в прошлом месяце, проверьте еще раз. Если это не ошибка обратитесь к @olegjan92.");
                    log.warn(chatId + " - enter thermal meter data less then month before");
                } else {
                    userData.setThermalMeterValue(value);
                    repository.save(userData);
                    sendMessage(chatId, "Спасибо, я записал показания Вашего счетчика.");
                    sendMessage(chatId, "Если Вы ошиблись в вводе показаний, ничего страшного, просто отправьте снова нужный вариант.");
                    log.info(chatId + " - enter correct thermal meter data");
                }
            }
        } else {
            sendMessage(chatId, "Сначала укажите номер телефона и номер квартиры, а потом запишем показания счетчика.");
            log.warn(chatId + " - enter thermal meter data before room number or phone number");
        }
    }

    private Double parseData(String data) {
        return NumberUtils.isParsable(data)
                ? Double.valueOf(data)
                : Double.valueOf(data.replace(",", "."));
    }

    private boolean isRoomNumber(String messageText) {
        if (messageText.length() < 9 && NumberUtils.isDigits(messageText)) {
            int i = Integer.parseInt(messageText);
            return i > 0 && i < 106;
        }
        return false;
    }

    private boolean isThermalMeterData(String messageText) {
        return PATTERN.asMatchPredicate().test(messageText);
    }

    private static String format(String messageText, long chatId, String firstName, String lastName) {
        return String.format(UNAVAILABLE_MESSAGE_FORMAT, firstName, lastName, chatId, messageText);
    }

    private void startBot(long chatId, String userName) {
        SendMessage sendMessage = new SendMessage(String.valueOf(chatId), "Здравствуйте, " + userName + "! Я бот для подачи показаний тепловых счетчиков. " +
                "Поделитесь, пожалуйста, номером Вашего телефона по кнопке ниже!");
        sendMessage.setReplyMarkup(getReplyKeyboardMarkup());
        sendMessage(sendMessage);
    }

    private static ReplyKeyboardMarkup getReplyKeyboardMarkup() {
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        replyKeyboardMarkup.setResizeKeyboard(true);
        replyKeyboardMarkup.setOneTimeKeyboard(true);

        List<KeyboardRow> keyboardRows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();

        KeyboardButton button = new KeyboardButton();
        button.setRequestContact(true);
        button.setText("Поделиться номером телефона");

        row.add(button);
        keyboardRows.add(row);
        replyKeyboardMarkup.setKeyboard(keyboardRows);
        return replyKeyboardMarkup;
    }

    private void sendError(long chatId) {
        sendMessage(chatId, "Простите, я не понял, что Вы написали. Возможно Вы где-то ошиблись. " +
                "Я понимаю только номера квартир и показания счетчиков с ТРЕМЯ знаками после запятой!");
    }

    private void sendMessage(long chatId, String message) {
        try {
            execute(new SendMessage(String.valueOf(chatId), message));
        } catch (TelegramApiException e) {
            log.error(e.getMessage());
        }
    }

    private void sendMessage(SendMessage sendMessage) {
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error(e.getMessage());
        }
    }
}
