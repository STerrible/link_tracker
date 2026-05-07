package backend.academy.linktracker.bot.service;

import backend.academy.linktracker.bot.command.BotMessages;
import backend.academy.linktracker.bot.command.CommandDispatcher;
import backend.academy.linktracker.bot.model.LinkUpdateRequest;
import backend.academy.linktracker.bot.telegram.TelegramClient;
import com.pengrad.telegrambot.request.SendMessage;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UpdateService {

    private static final Logger log = LoggerFactory.getLogger(UpdateService.class);

    private final TelegramClient telegramClient;
    private final CommandDispatcher dispatcher;

    public void handle(long chatId, String text) {
        log.atInfo().addKeyValue("chatId", chatId).log("message_received");

        try {
            SendMessage sendMessage = dispatcher.dispatch(chatId, text);
            telegramClient.sendMessage(sendMessage);
            log.atInfo().addKeyValue("chatId", chatId).log("message_sent");
        } catch (RuntimeException exception) {
            log.atWarn().addKeyValue("chatId", chatId).setCause(exception).log("message_processing_failed");
            try {
                telegramClient.sendMessage(chatId, BotMessages.FALLBACK_PROCESSING_ERROR);
            } catch (RuntimeException nestedException) {
                log.atWarn()
                        .addKeyValue("chatId", chatId)
                        .setCause(nestedException)
                        .log("fallback_message_send_failed");
            }
        }
    }

    public void handleLinkUpdate(LinkUpdateRequest update) {
        String message = "Обнаружено обновление:\n" + update.url() + "\n\n" + update.description();
        AtomicInteger sent = new AtomicInteger();
        update.tgChatIds().parallelStream().forEach(chatId -> {
            try {
                telegramClient.sendMessage(chatId, message);
                sent.incrementAndGet();
            } catch (RuntimeException exception) {
                log.atWarn().addKeyValue("chatId", chatId).setCause(exception).log("update_send_failed");
            }
        });
        log.atInfo()
                .addKeyValue("linkId", update.id())
                .addKeyValue("chats", update.tgChatIds().size())
                .addKeyValue("sent", sent.get())
                .log("update_sent");
    }
}
