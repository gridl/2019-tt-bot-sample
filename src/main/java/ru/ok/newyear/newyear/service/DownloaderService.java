package ru.ok.newyear.newyear.service;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;
import okio.Okio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import ru.ok.newyear.newyear.utils.Files;

import java.io.*;

@Service
public class DownloaderService {

    private static final Logger logger = LoggerFactory.getLogger(DownloaderService.class);

    private static final String DOWNLOADS = "downloads";
    private final OkHttpClient client;

    public DownloaderService() {
        logger.info("Init downloader service");
        client = new OkHttpClient();
    }

    @NonNull
    public File downloadFile(@NonNull String url) {
        logger.info("Try to download file {}", url);
        int hashCode = url.hashCode();
        File file = new File(DOWNLOADS, String.format("%d.jpg", hashCode));
        if (file.exists()) {
            logger.info("File {} already downloaded. Skip", url);
            return file;
        }
        File downloads = new File(DOWNLOADS);
        Files.createDirectory(downloads);
        Request request = new Request.Builder()
                .url(url)
                .build();
        Response response;
        try {
            response = client.newCall(request).execute();
        } catch (IOException e) {
            logger.error(String.format("Can't download file %s", url), e);
            return file;
        }
        if (!response.isSuccessful()) {
            logger.error("Response code is not successful {}", response.code());
            response.close();
            return file;
        }
        ResponseBody body = response.body();
        if (body == null) {
            logger.error("Response body is null");
            response.close();
            return file;
        }
        BufferedSink sink = null;
        try {
            sink = Okio.buffer(Okio.sink(file));
            sink.writeAll(body.source());
        } catch (IOException e) {
            logger.error(String.format("Can't download file %s", url), e);
        } finally {
            closeSilently(sink);
        }
        response.close();
        logger.info("Created new file {}", file.getPath());
        return file;
    }

    private void closeSilently(@Nullable Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException e) {
            logger.error("Can't close closeable", e);
        }
    }

}
