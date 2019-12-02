package ru.ok.newyear.newyear.service;

import chat.tamtam.botapi.TamTamBotAPI;
import chat.tamtam.botapi.TamTamUploadAPI;
import chat.tamtam.botapi.client.TamTamClient;
import chat.tamtam.botapi.exceptions.APIException;
import chat.tamtam.botapi.exceptions.AttachmentNotReadyException;
import chat.tamtam.botapi.exceptions.ClientException;
import chat.tamtam.botapi.exceptions.TooManyRequestsException;
import chat.tamtam.botapi.model.*;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import ru.ok.newyear.newyear.draw.Draw;
import ru.ok.newyear.newyear.draw.Drawer;
import ru.ok.newyear.newyear.utils.Properties;
import ru.ok.newyear.newyear.utils.Texts;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
public class BotService implements DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(BotService.class);

    private static final int MAX_FILE_SEND_ERROR_COUNT = 30;
    private static final int FILE_SEND_RETRY_TIMEOUT = 500;
    private static final CompositeDisposable compositeDisposable = new CompositeDisposable();
    public static final String MAGIC_ON_PHOTO_TEXT = "Что бы применить магию на фото - просто пришли мне его";
    public static final String START_MAGIC_TEXT = "Начинаю колдовать...";
    public static final String ERROR_TRY_LATER_TEXT = "Возникла ошибка. Попробуйте позже";

    private final DownloaderService downloaderService;
    private final TamTamBotAPI botAPI;
    private final TamTamUploadAPI uploadAPI;
    private Disposable updateDisposable;

    public BotService(@NonNull DownloaderService downloaderService, @NonNull @Value("${ny.bot.token}") String botToken) {
        logger.info("Init bot service");
        this.downloaderService = downloaderService;
        TamTamClient client = TamTamClient.create(botToken);
        botAPI = new TamTamBotAPI(client);
        uploadAPI = new TamTamUploadAPI(client);
        infinityCheckUpdates();
    }

    @Override
    public void destroy() {
        logger.info("Destroy bot service");
        compositeDisposable.dispose();
        if (updateDisposable != null) {
            updateDisposable.dispose();
        }
    }

    private void infinityCheckUpdates() {
        updateDisposable = Observable.create((ObservableOnSubscribe<List<Update>>) emitter -> {
            while (true) {
                emitter.onNext(getUpdates());
            }
        })
                .observeOn(Schedulers.io())
                .subscribeOn(Schedulers.io())
                .flatMap(Observable::fromIterable)
                .subscribe(this::handleUpdate, throwable -> logger.error("Error while check updates", throwable));
    }

    @NonNull
    private List<Update> getUpdates() {
        logger.info("Get updates");
        long marker = Properties.getUpdateMarker();
        logger.info("Old Update marker = {}", marker);
        UpdateList updateList;
        try {
            updateList = botAPI.getUpdates()
                    .marker(marker)
                    .execute();
        } catch (APIException | ClientException e) {
            logger.error("Can't get updates", e);
            return Collections.emptyList();
        }
        if (updateList == null) {
            logger.error("Update list is null");
            return Collections.emptyList();
        }
        Long newMarker = updateList.getMarker();
        if (newMarker == null) {
            newMarker = 0L;
        }
        logger.info("New update marker = {}", newMarker);
        if (marker != newMarker) {
            Properties.setUpdateMarker(newMarker);
        }
        List<Update> updates = updateList.getUpdates();
        if (updates == null) {
            logger.error("Updates is null");
            return Collections.emptyList();
        }
        return updates;
    }

    private void handleUpdate(@NonNull Update update) {
        logger.info("Handle update {}", update);
        switch (update.getType()) {
            case Update.BOT_STARTED:
                handleBotStartedUpdate((BotStartedUpdate) update);
                break;
            case Update.MESSAGE_CREATED:
                handleMessageCreatedUpdate((MessageCreatedUpdate) update);
                break;
            case Update.MESSAGE_CALLBACK:
                handleMessageCallback((MessageCallbackUpdate) update);
                break;
            default:
                logger.info("Ignore {} type", update.getType());
                break;
        }
    }

    private void handleMessageCallback(@NonNull MessageCallbackUpdate update) {
        logger.info("Handle message callback update");
        Message message = update.getMessage();
        if (message == null) {
            logger.error("Message is null");
            return;
        }
        Long chatId = getChatId(message);
        if (chatId == null) {
            logger.error("ChatId is null");
            return;
        }
        Callback callback = update.getCallback();
        if (callback == null) {
            logger.error("Callback is null");
            return;
        }
        String payload = callback.getPayload();
        if (Texts.isEmpty(payload)) {
            logger.error("Payload is empty");
            return;
        }
        processPhoto(payload, chatId);
    }

    private void handleMessageCreatedUpdate(@NonNull MessageCreatedUpdate update) {
        logger.info("Handle message created update");
        Message message = update.getMessage();
        if (message == null) {
            logger.error("Message is null");
            return;
        }
        Long chatId = getChatId(message);
        if (chatId == null) {
            logger.error("ChatId is null");
            return;
        }
        String url = null;
        MessageBody messageBody = message.getBody();
        if (messageBody != null) {
            url = getImageUrlFromAttaches(messageBody.getAttachments());
        }
        if (!Texts.isEmpty(url)) {
            processPhoto(url, chatId);
            return;
        }
        LinkedMessage linkedMessage = message.getLink();
        if (linkedMessage != null) {
            messageBody = linkedMessage.getMessage();
            if (messageBody != null) {
                url = getImageUrlFromAttaches(messageBody.getAttachments());
            }
        }
        if (!Texts.isEmpty(url)) {
            processPhoto(url, chatId);
            return;
        }
        sendText(chatId, MAGIC_ON_PHOTO_TEXT);
    }

    @Nullable
    private Long getChatId(@NonNull Message message) {
        Recipient recipient = message.getRecipient();
        if (recipient == null) {
            logger.error("Recipient is null");
            return null;
        }
        Long chatId = recipient.getChatId();
        if (chatId == null) {
            logger.error("ChatId is null");
            return null;
        }
        return chatId;
    }

    @Nullable
    private String getImageUrlFromAttaches(@Nullable List<Attachment> attachments) {
        if (attachments == null) {
            return null;
        }
        for (Attachment attachment : attachments) {
            if (!Objects.equals(attachment.getType(), Attachment.IMAGE)) {
                continue;
            }
            PhotoAttachment photoAttachment = (PhotoAttachment) attachment;
            PhotoAttachmentPayload payload = photoAttachment.getPayload();
            if (payload == null) {
                continue;
            }
            String url = payload.getUrl();
            if (Texts.isEmpty(url)) {
                continue;
            }
            return url;
        }
        return null;
    }

    private void handleBotStartedUpdate(@NonNull BotStartedUpdate update) {
        logger.info("Handle bot started update");
        long chatId = update.getChatId();
        String url = getAvatarUrl(chatId);
        if (Texts.isEmpty(url)) {
            sendText(chatId, MAGIC_ON_PHOTO_TEXT);
            return;
        }
        processPhoto(url, chatId);
    }

    private void processPhoto(@NonNull String url, long chatId) {
        File file = downloaderService.downloadFile(url);
        if (!file.exists()) {
            sendText(chatId, MAGIC_ON_PHOTO_TEXT);
            return;
        }
        sendText(chatId, START_MAGIC_TEXT);
        File result = Drawer.drawOverImage(file, Draw.random());
        if (result == null) {
            sendText(chatId, ERROR_TRY_LATER_TEXT);
            return;
        }
        sendPhoto(chatId, result, buildMoreKeyboard(url));
    }

    private void sendText(long chatId, @NonNull String text) {
        NewMessageBody newMessageBody = new NewMessageBody(text, null, null);
        try {
            botAPI.sendMessage(newMessageBody).chatId(chatId).execute();
        } catch (APIException | ClientException e) {
            logger.error(String.format("Can't send message to chat %d", chatId), e);
        }
    }

    @NonNull
    private InlineKeyboardAttachmentRequest buildMoreKeyboard(@NonNull String url) {
        CallbackButton button = new CallbackButton(url, "Другой вариант").intent(Intent.POSITIVE);
        List<List<Button>> buttons = Collections.singletonList(Collections.singletonList(button));
        InlineKeyboardAttachmentRequestPayload payload = new InlineKeyboardAttachmentRequestPayload(buttons);
        return new InlineKeyboardAttachmentRequest(payload);
    }

    private void sendPhoto(long chatId, @NonNull File file, @Nullable InlineKeyboardAttachmentRequest keyboardAttachmentRequest) {
        logger.info("Send file: chatId = {} file = {}", chatId, file.getPath());
        PhotoTokens photoTokens = uploadFile(file);
        logger.info("Photo tokens = {}", photoTokens);
        if (photoTokens == null) {
            logger.error("Photo tokes is empty");
            return;
        }
        PhotoAttachmentRequestPayload photoAttachmentRequestPayload = new PhotoAttachmentRequestPayload();
        photoAttachmentRequestPayload.setPhotos(photoTokens.getPhotos());
        List<AttachmentRequest> attachmentRequests = new ArrayList<>();
        attachmentRequests.add(new PhotoAttachmentRequest(photoAttachmentRequestPayload));
        if (keyboardAttachmentRequest != null) {
            attachmentRequests.add(keyboardAttachmentRequest);
        }
        NewMessageBody newMessageBody = new NewMessageBody(null, attachmentRequests, null);
        Disposable disposable = Completable.create(singleEmitter -> {
            try {
                logger.info("Try to send photo to chat {}", chatId);
                botAPI.sendMessage(newMessageBody)
                        .chatId(chatId)
                        .execute();
                singleEmitter.onComplete();
            } catch (ClientException | TooManyRequestsException ex) {
                logger.error(String.format("Can't send message to chatId %d", chatId), ex);
                singleEmitter.onComplete();
            } catch (AttachmentNotReadyException ex) {
                logger.info(String.format("Attach not ready. chatId = %d", chatId));
                singleEmitter.onError(ex);
            }
        })
                .observeOn(Schedulers.io())
                .subscribeOn(Schedulers.io())
                .retryWhen((Flowable<Throwable> f) -> f.take(MAX_FILE_SEND_ERROR_COUNT).delay(FILE_SEND_RETRY_TIMEOUT, TimeUnit.MILLISECONDS))
                .subscribe(() -> logger.info("Finish sending file. chatId = {}", chatId),
                        throwable -> logger.error("To many send errors. Can't send", throwable));
        compositeDisposable.add(disposable);
    }

    @Nullable
    private PhotoTokens uploadFile(@NonNull File file) {
        logger.info("Upload file: {}", file.getPath());
        try {
            UploadEndpoint uploadEndpoint = botAPI.getUploadUrl(UploadType.IMAGE).execute();
            return uploadAPI.uploadImage(uploadEndpoint.getUrl(), file).execute();
        } catch (ClientException | FileNotFoundException | APIException e) {
            logger.error(String.format("Can't upload file %s", file.getPath()), e);
        }
        return null;
    }

    @Nullable
    private String getAvatarUrl(long chatId) {
        Chat chat = getChat(chatId);
        if (chat == null) {
            return null;
        }
        Image image = chat.getIcon();
        String url = null;
        if (image != null) {
            url = image.getUrl();
        }
        if (!Texts.isEmpty(url)) {
            logger.info("Avatar for chat {} is {}", chat, url);
            return url;
        }
        UserWithPhoto userWithPhoto = chat.getDialogWithUser();
        if (userWithPhoto != null) {
            url = userWithPhoto.getFullAvatarUrl();
            logger.info("Avatar for chat {} is {}", chat, url);
        }
        return url;
    }

    @Nullable
    private Chat getChat(long chatId) {
        try {
            Chat chat = botAPI.getChat(chatId).execute();
            logger.info("Get chat {}", chat);
            return chat;
        } catch (APIException | ClientException e) {
            logger.error(String.format("Can't get chat %d", chatId), e);
        }
        return null;
    }

}
